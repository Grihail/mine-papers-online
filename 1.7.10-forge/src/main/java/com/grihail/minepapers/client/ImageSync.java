package com.grihail.minepapers.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.network.PacketOfferFiles;
import com.grihail.minepapers.network.SharedImage;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.client.Minecraft;

/** Обмен картинками с сервером. Выкладывание: файлы локального источника рамки готовятся в фоне (большие уменьшаются), сервер получает их список и просит только те, которых у него ещё нет. Скачивание: картинки рамок с сервера по одной, в кэш на диске. Все методы, кроме помеченных, вызываются из клиентского потока. */
public final class ImageSync {

    private static final Logger LOG = LogManager.getLogger(MinePapers.MODID);

    /** Отправка на сервер: 2 куска по 30 КБ за тик (~1.2 МБ/с), чтобы не забивать канал игрока. */
    private static final int UPLOAD_CHUNKS_PER_TICK = 2;
    /** Картинки больше этого (или крупнее MAX_SIDE) перед отправкой пережимаются. */
    private static final int KEEP_ORIGINAL_BYTES = 3 * 1024 * 1024;
    /** Больше не нужно: исходник всё равно уменьшается до этого размера (PictureManager.MAX_SOURCE_SIZE). */
    private static final int MAX_SIDE = 2048;
    private static final float JPEG_QUALITY = 0.92f;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MinePapers sync");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Задачи для клиентского потока (из сетевого и фонового). */
    private static final Queue<Runnable> MAIN = new ConcurrentLinkedQueue<Runnable>();

    private static final Set<String> CACHED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> FAILED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> REQUESTED = new HashSet<String>();
    private static final List<String> TO_REQUEST = new ArrayList<String>();
    private static final Map<String, Partial> DOWNLOADS = new ConcurrentHashMap<String, Partial>();

    private static final Queue<Upload> UPLOADS = new ArrayDeque<Upload>();
    /** "путь|размер|время изменения" -> хеш: не пережимать одни и те же файлы при каждом сохранении. */
    private static final Map<String, String> PREPARED = new ConcurrentHashMap<String, String>();
    private static final Set<String> AUTO_OFFERED = new HashSet<String>();

    /** Состояние последней отправки (для экрана настроек). */
    private static volatile String jobOrigin;
    private static volatile int jobStage, jobDone, jobTotal;
    static final int STAGE_NONE = 0, STAGE_PREPARING = 1, STAGE_UPLOADING = 2;

    private ImageSync() {}

    static File cacheDir() {
        return new File(Minecraft.getMinecraft().mcDataDir, "minepapers-cache");
    }

    private static File cacheFile(String hex) {
        return new File(cacheDir(), hex);
    }

    // ===================================================================== Скачивание (для PictureManager)

    /** true — картинка уже в кэше на диске; иначе она запрашивается у сервера (если ещё не). Рендер-поток. */
    static boolean ensure(String hex) {
        if (CACHED.contains(hex)) return true;
        if (REQUESTED.contains(hex) || FAILED.contains(hex)) return false;
        if (cacheFile(hex).isFile()) {
            CACHED.add(hex);
            return true;
        }
        REQUESTED.add(hex);
        TO_REQUEST.add(hex);
        return false;
    }

    static boolean isFailed(String hex) {
        return FAILED.contains(hex);
    }

    /** Фоновый поток. */
    static byte[] read(String hex) throws IOException {
        return Files.readAllBytes(cacheFile(hex).toPath());
    }

    /** Ещё раз попробовать скачать то, что не получилось. */
    static void retryFailed() {
        REQUESTED.removeAll(FAILED);
        FAILED.clear();
    }

    /** Сетевой поток. */
    public static void onFileChunk(final byte[] hash, final int total, final int offset, final byte[] data) {
        final String hex = SharedImage.hex(hash);
        if (total < 0) {
            DOWNLOADS.remove(hex);
            failed(hex);
            return;
        }
        Partial p = DOWNLOADS.get(hex);
        if (p == null) {
            if (offset != 0 || total > SharedImage.MAX_BYTES) return;
            p = new Partial(total);
            DOWNLOADS.put(hex, p);
        }
        if (offset != p.received || offset + data.length > p.data.length) {
            DOWNLOADS.remove(hex);
            failed(hex);
            return;
        }
        System.arraycopy(data, 0, p.data, offset, data.length);
        p.received += data.length;
        if (p.received < p.data.length) return;

        DOWNLOADS.remove(hex);
        final byte[] blob = p.data;
        WORKER.submit(() -> {
            if (java.util.Arrays.equals(SharedImage.sha1(blob), hash) && writeCache(hex, blob)) {
                CACHED.add(hex);
                MAIN.add(() -> REQUESTED.remove(hex));
            } else {
                failed(hex);
            }
        });
    }

    private static void failed(final String hex) {
        FAILED.add(hex);
        MAIN.add(() -> REQUESTED.remove(hex));
    }

    private static boolean writeCache(String hex, byte[] blob) {
        File dir = cacheDir();
        File target = new File(dir, hex);
        if (target.isFile()) return true;
        File tmp = new File(dir, hex + "." + Thread.currentThread().getId() + ".tmp");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(blob);
            } finally {
                out.close();
            }
            if (!tmp.renameTo(target) && !target.isFile()) throw new IOException("Cannot rename " + tmp);
            return true;
        } catch (IOException e) {
            LOG.warn("Не удалось сохранить картинку в кэш: " + target, e);
            return false;
        } finally {
            tmp.delete();
        }
    }

    // ===================================================================== Выкладывание

    /** Выложить картинки локального источника рамки на сервер, чтобы их увидели все. Если у этого игрока таких файлов нет, ничего не происходит. */
    public static void offer(EntityPicture picture) {
        final String origin = picture.frame.getOriginKey();
        if (origin == null || picture.frame.isNetworkSource()) return;
        final int entityId = picture.getEntityId();
        jobOrigin = origin;
        jobStage = STAGE_PREPARING;
        jobDone = 0;
        jobTotal = 0;
        WORKER.submit(() -> {
            try {
                List<File> files = PictureManager.sourceFiles(origin);
                int n = Math.min(files.size(), PictureFrame.MAX_FILES);
                jobTotal = n;
                ByteArrayOutputStream hashes = new ByteArrayOutputStream();
                for (int i = 0; i < n; i++) {
                    String hex = prepare(files.get(i));
                    if (hex != null) hashes.write(SharedImage.unhex(hex));
                    jobDone = i + 1;
                }
                final byte[] list = hashes.toByteArray();
                if (list.length == 0) {
                    finishJob(origin);
                    return;
                }
                MAIN.add(() -> {
                    if (isConnected()) MinePapers.network.sendToServer(new PacketOfferFiles(entityId, origin, list));
                    if (origin.equals(jobOrigin) && jobStage == STAGE_PREPARING) jobStage = STAGE_NONE;
                });
            } catch (Throwable t) {
                LOG.info("Картинки " + origin + " не отправлены на сервер: " + t);
                finishJob(origin);
            }
        });
    }

    /** Рамки без выложенных картинок хост одиночной игры / LAN выкладывает сам: файлы с его компьютера, значит, это его картины. */
    static void autoOffer(EntityPicture picture) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isIntegratedServerRunning()) return;
        String key = picture.worldObj.provider.dimensionId + "," + picture.getEntityId() + "|"
            + picture.frame.getOriginKey();
        if (AUTO_OFFERED.add(key)) offer(picture);
    }

    /** Сервер попросил эти картинки (сетевой поток). */
    public static void onFilesNeeded(final byte[] hashes) {
        MAIN.add(() -> {
            for (int i = 0; i < hashes.length; i += SharedImage.HASH_BYTES) {
                UPLOADS.add(new Upload(java.util.Arrays.copyOfRange(hashes, i, i + SharedImage.HASH_BYTES)));
            }
            jobStage = STAGE_UPLOADING;
            jobDone = 0;
            jobTotal = UPLOADS.size();
        });
    }

    /** Фоновый поток: готовит файл к отправке и кладёт в кэш. Возвращает хеш или null, если файл не читается. */
    private static String prepare(File file) {
        String id = file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
        String hex = PREPARED.get(id);
        if (hex != null && cacheFile(hex).isFile()) return hex;
        try {
            byte[] blob = encode(file);
            hex = SharedImage.hex(SharedImage.sha1(blob));
            if (!writeCache(hex, blob)) return null;
            CACHED.add(hex);
            PREPARED.put(id, hex);
            return hex;
        } catch (Throwable t) {
            LOG.warn("Не удалось подготовить картинку " + file + ": " + t);
            return null;
        }
    }

    /** Небольшие png/jpg/gif отправляются как есть, остальное уменьшается до MAX_SIDE и пережимается: с прозрачностью — в PNG, без — в JPEG. Анимированный GIF при пережатии потерял бы анимацию, поэтому он отправляется как есть, пока помещается в лимит сервера (кадры уменьшит уже клиент). Исходный размер записывается в заголовок (от него зависит вписывание). */
    private static byte[] encode(File file) throws IOException {
        if (file.length() > PictureManager.MAX_DOWNLOAD_BYTES) throw new IOException("File is too large");
        byte[] bytes = Files.readAllBytes(file.toPath());
        int w, h;
        String format;
        boolean animated;
        ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, false, false);
                animated = GifFrames.isAnimated(reader);
                int[] size = animated ? GifFrames.screenSize(reader) : new int[] { reader.getWidth(0), reader.getHeight(0) };
                w = size[0];
                h = size[1];
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
            } finally {
                reader.dispose();
            }
        } finally {
            in.close();
        }

        if (animated && bytes.length + SharedImage.HEADER_BYTES <= SharedImage.MAX_BYTES) {
            return SharedImage.pack(w, h, bytes);
        }

        boolean webFormat = format.equals("png") || format.equals("jpeg") || format.equals("jpg")
            || format.equals("gif");
        if (webFormat && bytes.length <= KEEP_ORIGINAL_BYTES && Math.max(w, h) <= MAX_SIDE) {
            return SharedImage.pack(w, h, bytes);
        }

        int[] size = new int[2];
        BufferedImage img = PictureManager.limitSize(PictureManager.decode(bytes, size), MAX_SIDE);
        byte[] image = hasAlpha(img) ? writePng(img) : writeJpeg(img);
        return SharedImage.pack(size[0], size[1], image);
    }

    private static boolean hasAlpha(BufferedImage img) {
        if (!img.getColorModel().hasAlpha()) return false;
        int w = img.getWidth(), h = img.getHeight();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int c : row) {
                if ((c >>> 24) != 0xFF) return true;
            }
        }
        return false;
    }

    private static byte[] writePng(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "png", out)) throw new IOException("No PNG writer");
        return out.toByteArray();
    }

    private static byte[] writeJpeg(BufferedImage src) throws IOException {
        // JPEG из ARGB-картинки ImageIO пишет с искажёнными цветами — переводим в RGB.
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(src, 0, 0, null);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) return writePng(src);
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(out);
        try {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
            ios.close();
        }
        return out.toByteArray();
    }

    private static void finishJob(String origin) {
        if (origin.equals(jobOrigin)) jobStage = STAGE_NONE;
    }

    // =====================================================================

    /** Состояние отправки для экрана настроек: {этап, готово, всего} или null, если это не наша рамка. */
    static int[] jobStatus(String origin) {
        if (origin == null || !origin.equals(jobOrigin) || jobStage == STAGE_NONE) return null;
        return new int[] { jobStage, jobDone, jobTotal };
    }

    private static boolean isConnected() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && mc.getNetHandler() != null;
    }

    /** Каждый клиентский тик. */
    static void tick() {
        Runnable task;
        while ((task = MAIN.poll()) != null) task.run();
        if (!isConnected()) return;

        if (!TO_REQUEST.isEmpty()) {
            int n = Math.min(TO_REQUEST.size(), PacketHashes.MAX_HASHES);
            byte[] hashes = new byte[n * SharedImage.HASH_BYTES];
            for (int i = 0; i < n; i++) {
                System.arraycopy(SharedImage.unhex(TO_REQUEST.get(i)), 0, hashes, i * SharedImage.HASH_BYTES,
                    SharedImage.HASH_BYTES);
            }
            TO_REQUEST.subList(0, n).clear();
            MinePapers.network.sendToServer(new PacketHashes.Request(hashes));
        }

        for (int i = 0; i < UPLOAD_CHUNKS_PER_TICK && !UPLOADS.isEmpty(); i++) {
            Upload u = UPLOADS.peek();
            if (u.data == null) {
                try {
                    u.data = read(SharedImage.hex(u.hash));
                } catch (IOException e) {
                    LOG.warn("Картинка пропала из кэша, не отправлена: " + SharedImage.hex(u.hash));
                    UPLOADS.poll();
                    continue;
                }
            }
            int len = Math.min(PacketFileChunk.Upload.MAX_DATA, u.data.length - u.offset);
            MinePapers.network.sendToServer(new PacketFileChunk.Upload(u.hash, u.data.length, u.offset, u.data,
                u.offset, len));
            u.offset += len;
            if (u.offset >= u.data.length) {
                UPLOADS.poll();
                jobDone++;
                if (UPLOADS.isEmpty() && jobStage == STAGE_UPLOADING) jobStage = STAGE_NONE;
            }
        }
    }

    /** Отключились от сервера: всё, что было в пути, пропало. Сетевой поток. */
    public static void onDisconnect() {
        MAIN.add(() -> {
            REQUESTED.clear();
            TO_REQUEST.clear();
            FAILED.clear();
            DOWNLOADS.clear();
            UPLOADS.clear();
            AUTO_OFFERED.clear();
            jobStage = STAGE_NONE;
        });
    }

    private static final class Partial {
        final byte[] data;
        int received;

        Partial(int total) {
            data = new byte[total];
        }
    }

    private static final class Upload {
        final byte[] hash;
        byte[] data;
        int offset;

        Upload(byte[] hash) {
            this.hash = hash;
        }
    }
}
