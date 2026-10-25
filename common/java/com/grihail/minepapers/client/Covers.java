package com.grihail.minepapers.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.grihail.minepapers.network.SharedImage;
import com.grihail.minepapers.picture.PictureSettings;

/** Кэш кадров на диске: последний показанный кадр каждой картины (готовая текстура) — отдельно для каждого мира и сервера. Чанк картины загрузился снова, а источник ещё читается (папка Яндекс Диска, ссылка, картинки с сервера) — картина сразу показывает этот кадр, а потом слайдшоу идёт дальше. Файл — minepapers-cache/covers/<SHA-1 мира, измерения и места картины>. Все методы, кроме фоновых задач, — из клиентского (рендер) потока. */
final class Covers {

    private static final Logger LOG = LogManager.getLogger("minepapers");
    private static final int MAGIC = 0x4D504331; // "MPC1"
    /** Кадры, которые не показывались столько дней, удаляются (раз за запуск игры). */
    private static final long FORGET_AFTER_MS = 30L * 24 * 60 * 60 * 1000;
    /** Чтение и запись — в одном фоновом потоке с низким приоритетом: файлов мало, и они маленькие. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MinePapers covers");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Что уже записано в файл каждой картины (PNG кадра) — чтобы не писать одно и то же снова. */
    private static final Map<String, byte[]> WRITTEN = new HashMap<String, byte[]>();
    /** Кадры, прочитанные с диска, по имени файла. */
    private static final Map<String, Entry> LOADED = new HashMap<String, Entry>();
    private static boolean pruned;

    private Covers() {}

    private static final class Entry {
        /** Прочитанный кадр и то, для чего он стилизован; done — чтение кончилось (файла могло не быть). */
        volatile ImageStylizer.Result frame;
        volatile String sourceKey, layoutKey;
        volatile int origW, origH;
        volatile boolean done;
        PictureManager.Variant variant;
        long lastUsed;
    }

    /** Кадр картины с диска, пока показать больше нечего (null — его нет, он для других настроек или ещё читается). */
    static PictureManager.Variant get(PictureSettings tile, Object world) {
        String name = fileName(tile, world);
        Entry e = LOADED.get(name);
        if (e == null) {
            e = new Entry();
            LOADED.put(name, e);
            read(name, e);
        }
        e.lastUsed = PictureManager.now();
        if (e.variant == null && e.frame != null) {
            // Кадр годится, только если источник и раскладка те же, что сейчас у картины.
            if (e.sourceKey.equals(tile.getSourceKey()) && e.layoutKey.equals(ImageStylizer.Layout.of(tile, e.origW, e.origH).key)) {
                e.variant = PictureManager.Variant.fromCover(e.frame);
            }
            e.frame = null;
        }
        if (e.variant == null) return null;
        e.variant.upload();
        return e.variant.isReady() ? e.variant : null;
    }

    /** Картина показывает другой кадр: запомнить его на диске (в фоне). */
    static void remember(PictureSettings tile, Object world, PictureManager.Variant shown) {
        byte[] png = shown.cover;
        if (png == null || shown.isCover()) return;
        String name = fileName(tile, world);
        if (WRITTEN.get(name) == png) return;
        WRITTEN.put(name, png);
        final String sourceKey = tile.getSourceKey(), layoutKey = shown.layoutKey;
        final int ow = shown.origW, oh = shown.origH;
        final float x0 = shown.x0, y0 = shown.y0, x1 = shown.x1, y1 = shown.y1;
        IO.submit(() -> {
            try {
                File dir = dir();
                Files.createDirectories(dir.toPath());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(png.length + 256);
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeInt(MAGIC);
                out.writeUTF(sourceKey);
                out.writeUTF(layoutKey);
                out.writeInt(ow);
                out.writeInt(oh);
                out.writeFloat(x0);
                out.writeFloat(y0);
                out.writeFloat(x1);
                out.writeFloat(y1);
                out.write(png);
                out.flush();
                File tmp = new File(dir, name + ".tmp");
                Files.write(tmp.toPath(), bytes.toByteArray());
                Files.move(tmp.toPath(), new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable t) {
                LOG.warn("Не удалось сохранить кадр картины: " + t);
            }
        });
    }

    /** Кадр с диска больше не показывается (его сменила настоящая картинка). */
    static void release(PictureManager.Variant cover) {
        for (Iterator<Entry> it = LOADED.values().iterator(); it.hasNext();) {
            Entry e = it.next();
            if (e.variant == cover) {
                cover.release();
                it.remove();
            }
        }
    }

    /** Из PictureManager.tick: кадры, которые никто не держит (картина ушла с клиента), выгружаются. */
    static void tick(long now) {
        for (Iterator<Entry> it = LOADED.values().iterator(); it.hasNext();) {
            Entry e = it.next();
            long used = e.variant != null ? Math.max(e.lastUsed, e.variant.lastUsed) : e.lastUsed;
            if (now - used > PictureManager.RELEASE_AFTER_MS && (e.done || e.variant != null)) {
                if (e.variant != null) e.variant.release();
                it.remove();
            }
        }
    }

    private static void read(final String name, final Entry e) {
        prune();
        IO.submit(() -> {
            File file = new File(dir(), name);
            try {
                if (!file.isFile()) return;
                byte[] data = Files.readAllBytes(file.toPath());
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                if (in.readInt() != MAGIC) return;
                String sourceKey = in.readUTF(), layoutKey = in.readUTF();
                int ow = in.readInt(), oh = in.readInt();
                ImageStylizer.Result frame = new ImageStylizer.Result();
                frame.x0 = in.readFloat();
                frame.y0 = in.readFloat();
                frame.x1 = in.readFloat();
                frame.y1 = in.readFloat();
                BufferedImage image = ImageIO.read(in);
                if (image == null) return;
                frame.width = image.getWidth();
                frame.height = image.getHeight();
                frame.pixels = image.getRGB(0, 0, frame.width, frame.height, null, 0, frame.width);
                e.sourceKey = sourceKey;
                e.layoutKey = layoutKey;
                e.origW = ow;
                e.origH = oh;
                e.frame = frame;
                file.setLastModified(System.currentTimeMillis()); // показан — не удалять как забытый
            } catch (Throwable t) {
                LOG.warn("Не удалось прочитать кадр картины " + file + ": " + t);
            } finally {
                e.done = true;
            }
        });
    }

    /** Раз за запуск: удалить кадры, которые давно не показывались. */
    private static void prune() {
        if (pruned) return;
        pruned = true;
        IO.submit(() -> {
            File[] files = dir().listFiles();
            if (files == null) return;
            long old = System.currentTimeMillis() - FORGET_AFTER_MS;
            for (File f : files) {
                if (f.lastModified() < old) f.delete();
            }
        });
    }

    /** Первый кадр в PNG (из фонового потока стилизации). */
    static byte[] encode(int[] argb, int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, argb, 0, width);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static File dir() {
        return new File(ImageSync.cacheDir(), "covers");
    }

    /** Файл картины: мир (или сервер), измерение и место картины — клетка, сторона, поворот и смещение холста. */
    private static String fileName(PictureSettings tile, Object world) {
        String place = ClientHost.get().worldId() + "|" + world + "|" + tile.x + "," + tile.y + "," + tile.z + "|" + tile.getFacing()
            + "|" + tile.getRotation() + "|" + tile.offsetX + "," + tile.offsetY + "," + tile.offsetInPixels;
        return SharedImage.hex(SharedImage.sha1(place.getBytes(StandardCharsets.UTF_8)));
    }
}
