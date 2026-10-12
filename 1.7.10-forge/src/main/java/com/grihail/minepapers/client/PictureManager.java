package com.grihail.minepapers.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.SharedImage;

import net.minecraft.client.renderer.texture.TextureUtil;

/** Загрузка и кэширование картинок на клиенте. Все публичные методы вызываются только из клиентского (рендер) потока; скачивание, декодирование и стилизация идут в фоновых потоках. */
public final class PictureManager {

    private static final Logger LOG = LogManager.getLogger(MinePapers.MODID);

    /** Текстура выгружается из видеопамяти, если её не рисовали столько миллисекунд. */
    private static final long EVICT_AFTER_MS = 30_000L;
    /** Исходник после декодирования сразу уменьшается до этого размера — больше для стилизации не нужно. */
    private static final int MAX_SOURCE_SIZE = 2048;
    /** Кадры анимации: каждый не больше этого по большей стороне, а все вместе — не больше ANIMATION_PIXELS пикселей (кадров бывают сотни). */
    private static final int MAX_ANIMATION_SIZE = 512;
    private static final long ANIMATION_PIXELS = 16L * 1024 * 1024;
    /** Готовые текстуры всех кадров одной картины вместе — не больше стольких пикселей: иначе показываем каждый k-й кадр. */
    private static final long STYLED_PIXELS = 16L * 1024 * 1024;
    static final int MAX_DOWNLOAD_BYTES = 256 * 1024 * 1024;
    private static final String USER_AGENT = MinePapers.NAME + "/" + MinePapers.VERSION;
    private static final String YANDEX_API = "https://cloud-api.yandex.net/v1/disk/public/resources";

    /** .jfif/.jpe/.jif/.pjpeg/.pjp — это тоже JPEG (так их часто сохраняют браузеры). */
    private static final String[] IMAGE_EXTENSIONS = { ".png", ".jpg", ".jpeg", ".jfif", ".jpe", ".jif", ".pjpeg",
        ".pjp", ".gif", ".bmp" };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "MinePapers loader #" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    });

    private static final Map<String, Source> SOURCES = new HashMap<String, Source>();

    /** Миниатюры для иконок предметов — крошечные, поэтому живут дольше и не держат исходник в памяти. */
    private static final long THUMB_EVICT_AFTER_MS = 5 * 60_000L;
    private static final Map<String, Thumb> THUMBS = new HashMap<String, Thumb>();

    private PictureManager() {}

    /** Миниатюра первой картинки источника для иконки предмета (null, пока не готова или если не вышло). Источник загружается только до тех пор, пока миниатюра не посчитана, дальше его можно выгрузить. */
    public static Variant thumbnail(String sourceKey, ImageStylizer.Layout layout) {
        String key = sourceKey + "|" + layout.key;
        Thumb thumb = THUMBS.get(key);
        if (thumb == null) {
            thumb = new Thumb();
            THUMBS.put(key, thumb);
        }
        thumb.lastUsed = System.currentTimeMillis();
        if (thumb.variant == null && !thumb.failed) {
            Source source = get(sourceKey);
            Picture first = source.first();
            if (first == null) {
                thumb.failed = !source.isLoading();
            } else if (first.request()) {
                thumb.variant = first.newVariant(layout, true);
            } else {
                thumb.failed = first.isFailed();
            }
        }
        Variant v = thumb.variant;
        if (v == null) return null;
        v.upload();
        return v.hasTexture() ? v : null;
    }

    private static final class Thumb {
        Variant variant;
        boolean failed;
        long lastUsed;
    }

    /** Возвращает источник по ключу, запуская его загрузку при первом обращении. */
    public static Source get(String key) {
        Source source = SOURCES.get(key);
        if (source == null) {
            source = new Source(key);
            SOURCES.put(key, source);
            source.startLoading();
        }
        return source;
    }

    /** Источник без запуска загрузки (для экрана настроек). */
    public static Source peek(String key) {
        return key == null ? null : SOURCES.get(key);
    }

    /** Сбросить кэш источника, чтобы заново прочитать папку / список файлов. */
    public static void reload(String key) {
        if (key.startsWith("shared:")) ImageSync.retryFailed();
        Source source = SOURCES.remove(key);
        if (source != null) {
            source.release();
        }
        for (Iterator<Map.Entry<String, Thumb>> it = THUMBS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Thumb> e = it.next();
            if (e.getKey().startsWith(key + "|")) {
                if (e.getValue().variant != null) e.getValue().variant.release();
                it.remove();
            }
        }
    }

    /** Периодически вызывается из клиентского тика: выгружает давно не используемые текстуры. */
    public static void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Source> it = SOURCES.values().iterator(); it.hasNext();) {
            Source source = it.next();
            List<Picture> pictures = source.pictures;
            if (pictures != null) {
                for (Picture p : pictures) p.evict(now);
            }
            // Источник, который давно никто не рисует, забываем целиком — при следующем показе список перечитается.
            if (now - source.lastUsed > EVICT_AFTER_MS && !source.isLoading()) {
                source.release();
                it.remove();
            }
        }
        for (Iterator<Thumb> it = THUMBS.values().iterator(); it.hasNext();) {
            Thumb thumb = it.next();
            if (now - thumb.lastUsed > THUMB_EVICT_AFTER_MS) {
                if (thumb.variant != null) thumb.variant.release();
                it.remove();
            }
        }
    }

    /** Файлы-картинки и подпапки в config/minepapers (для выбора в интерфейсе). */
    public static List<String> listLocalEntries() {
        List<String> result = new ArrayList<String>();
        File[] files = MinePapers.picturesDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    result.add(f.getName() + "/");
                } else if (isImageFile(f)) {
                    result.add(f.getName());
                }
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private static boolean isImageFile(File f) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    // =====================================================================

    public static final class Source {
        private final String key;
        volatile List<Picture> pictures;
        /** Ключ локализации ошибки или null. */
        volatile String errorKey;
        /** Текст ошибки (сообщение исключения) или null. */
        volatile String errorDetail;
        long lastUsed = System.currentTimeMillis();

        Source(String key) {
            this.key = key;
        }

        public boolean isLoading() {
            return pictures == null && errorKey == null && errorDetail == null;
        }

        public int count() {
            List<Picture> list = pictures;
            return list == null ? 0 : list.size();
        }

        public String getErrorKey() {
            return errorKey;
        }

        public String getErrorDetail() {
            return errorDetail;
        }

        void startLoading() {
            EXECUTOR.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        List<Picture> list;
                        if (key.startsWith("yandex:")) {
                            list = listYandex(key.substring("yandex:".length()));
                        } else if (key.startsWith("url:")) {
                            list = listUrl(key.substring("url:".length()));
                        } else if (key.startsWith("shared:")) {
                            list = listShared(key.substring("shared:".length()));
                        } else {
                            list = new ArrayList<Picture>();
                            for (File f : sourceFiles(key)) list.add(filePicture(f));
                        }
                        if (list.isEmpty()) {
                            errorKey = "minepapers.error.noImages";
                        } else {
                            pictures = list;
                        }
                    } catch (SourceException e) {
                        errorKey = e.getMessage();
                    } catch (Throwable t) {
                        LOG.warn("Не удалось загрузить список картинок: " + key, t);
                        errorDetail = String.valueOf(t.getMessage() != null ? t.getMessage() : t);
                    }
                }
            });
        }

        /** Картинка для текущего момента слайдшоу (null, если список ещё не готов). Порядок случайный, но считается от игрового времени и seed рамки — у всех игроков одинаковый. @param offset 0 — текущий слайд, 1 — следующий (для предзагрузки) @param seed   своё значение у каждой рамки, чтобы рамки с одной папкой не показывали одно и то же */
        public Picture slide(long worldTime, int intervalSeconds, int offset, long seed) {
            lastUsed = System.currentTimeMillis();
            List<Picture> list = pictures;
            if (list == null || list.isEmpty()) return null;
            int n = list.size();
            if (n == 1) return offset == 0 ? list.get(0) : null;
            long slot = worldTime / (Math.max(1, intervalSeconds) * 20L) + offset;
            return list.get(shuffledIndex(slot, n, seed));
        }

        /** Первая картинка списка (для иконки предмета) или null, если список ещё не готов. */
        public Picture first() {
            lastUsed = System.currentTimeMillis();
            List<Picture> list = pictures;
            return list == null || list.isEmpty() ? null : list.get(0);
        }

        /** Каждый "круг" из n слайдов — своя случайная перестановка: за круг каждая картинка показывается ровно один раз, а на стыке кругов одна и та же картинка не повторяется дважды подряд. */
        private int shuffledIndex(long slot, int n, long seed) {
            if (n == 2) return (int) (slot % 2);
            long cycle = slot / n;
            int pos = (int) (slot % n);
            int[] perm = permutation(cycle, n, seed);
            if (pos == 0 && cycle > 0 && perm[0] == permutation(cycle - 1, n, seed)[n - 1]) {
                return perm[1];
            }
            if (pos == 1 && cycle > 0 && perm[0] == permutation(cycle - 1, n, seed)[n - 1]) {
                return perm[0];
            }
            return perm[pos];
        }

        /** Небольшой кэш перестановок: их запрашивают каждый кадр. */
        private final Map<String, int[]> permCache = new HashMap<String, int[]>();

        private int[] permutation(long cycle, int n, long seed) {
            String cacheKey = seed + ":" + cycle + ":" + n;
            int[] perm = permCache.get(cacheKey);
            if (perm == null) {
                if (permCache.size() > 32) permCache.clear();
                perm = new int[n];
                for (int i = 0; i < n; i++) perm[i] = i;
                java.util.Random rnd = new java.util.Random(seed * 0x9E3779B97F4A7C15L + cycle);
                for (int i = n - 1; i > 0; i--) {
                    int j = rnd.nextInt(i + 1);
                    int t = perm[i];
                    perm[i] = perm[j];
                    perm[j] = t;
                }
                permCache.put(cacheKey, perm);
            }
            return perm;
        }

        void release() {
            List<Picture> list = pictures;
            if (list != null) {
                for (Picture p : list) p.release();
            }
        }
    }

    // =====================================================================

    /** Одна картинка: исходные пиксели в памяти и набор стилизованных текстур под разные холсты. */
    public static final class Picture {
        private static final int IDLE = 0, LOADING = 1, READY = 2, FAILED = 3;

        private final String name;
        /** Возвращает File или byte[] с закодированной картинкой. */
        private final Callable<Object> loader;
        /** Картинка с сервера: загружать можно, только когда она скачана в кэш. */
        private final String sharedHash;
        private volatile int state = IDLE;
        /** Пиксели кадров; у обычной картинки кадр один. */
        private volatile int[][] frames;
        /** Длительности кадров в мс (null, если кадр один). */
        private volatile int[] delays;
        private volatile int srcW, srcH, origW, origH;
        private final Map<String, Variant> variants = new HashMap<String, Variant>();
        long lastUsed;

        Picture(String name, Callable<Object> loader) {
            this(name, loader, null);
        }

        Picture(String name, Callable<Object> loader, String sharedHash) {
            this.name = name;
            this.loader = loader;
            this.sharedHash = sharedHash;
        }

        /** Исходный размер картинки (до любых уменьшений). Имеет смысл после request() == true. */
        public int getOrigWidth() {
            return origW;
        }

        public int getOrigHeight() {
            return origH;
        }

        /** Запускает загрузку исходника, если нужно. true — исходник готов. */
        public boolean request() {
            lastUsed = System.currentTimeMillis();
            if (state == IDLE && sharedHash != null && !ImageSync.ensure(sharedHash)) {
                // Ещё качается с сервера: потоки загрузки не ждут, проверим снова в следующем кадре.
                if (ImageSync.isFailed(sharedHash)) state = FAILED;
                return false;
            }
            if (state == IDLE) {
                state = LOADING;
                EXECUTOR.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Decoded d = decodeFrames(loader.call());
                            srcW = d.width;
                            srcH = d.height;
                            origW = d.origW;
                            origH = d.origH;
                            delays = d.delays;
                            frames = d.frames;
                            state = READY;
                        } catch (Throwable t) {
                            LOG.warn("Не удалось загрузить картинку " + name + ": " + t);
                            state = FAILED;
                        }
                    }
                });
            }
            return state == READY;
        }

        /** Текстура под заданный холст; стилизуется в фоне, пока не готова — isReady() == false. */
        public Variant variant(final ImageStylizer.Layout layout) {
            Variant v = variants.get(layout.key);
            if (v == null) {
                v = newVariant(layout, false);
                variants.put(layout.key, v);
            }
            lastUsed = v.lastUsed = System.currentTimeMillis();
            v.upload();
            return v;
        }

        /** Запускает стилизацию в фоне; вариант не запоминается в картинке (этим занимается вызывающий). @param icon для иконки предмета: только первый кадр */
        Variant newVariant(final ImageStylizer.Layout layout, boolean icon) {
            final Variant created = new Variant(this);
            final int[][] src = frames;
            final int[] ms = icon ? null : delays;
            final int sw = srcW, sh = srcH, ow = origW, oh = origH;
            if (src == null) {
                created.failed = true;
            } else {
                EXECUTOR.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            created.result = Styled.of(src, ms, sw, sh, ow, oh, layout);
                        } catch (Throwable t) {
                            LOG.warn("Не удалось обработать картинку " + name, t);
                            created.failed = true;
                        }
                    }
                });
            }
            return created;
        }

        boolean isFailed() {
            return state == FAILED;
        }

        void evict(long now) {
            for (Iterator<Variant> it = variants.values().iterator(); it.hasNext();) {
                Variant v = it.next();
                if (now - v.lastUsed > EVICT_AFTER_MS) {
                    v.release();
                    it.remove();
                }
            }
            if (now - lastUsed > EVICT_AFTER_MS) {
                release();
            }
        }

        void release() {
            for (Variant v : variants.values()) v.release();
            variants.clear();
            if (state == READY) {
                frames = null;
                delays = null;
                state = IDLE;
            }
        }
    }

    /** Стилизованные кадры одного варианта: у всех один размер и одно место на холсте. */
    static final class Styled {
        ImageStylizer.Result[] frames;
        /** Длительности кадров в мс (null, если кадр один). */
        int[] delays;

        /** @param delays длительности кадров или null — тогда берётся только первый кадр */
        static Styled of(int[][] src, int[] delays, int sw, int sh, int ow, int oh, ImageStylizer.Layout layout) {
            ImageStylizer.Result first = ImageStylizer.process(src[0], sw, sh, ow, oh, layout);
            int n = delays == null || first.empty ? 1 : src.length;
            // Текстуры всех кадров вместе — не больше STYLED_PIXELS: иначе берём каждый k-й кадр, он длится за всех пропущенных.
            int step = n == 1 ? 1 : (int) Math.max(1, Math.ceil((double) n * first.width * first.height / STYLED_PIXELS));
            List<ImageStylizer.Result> out = new ArrayList<ImageStylizer.Result>();
            List<Integer> times = new ArrayList<Integer>();
            for (int i = 0; i < n; i += step) {
                out.add(i == 0 ? first : ImageStylizer.process(src[i], sw, sh, ow, oh, layout));
                int t = 0;
                for (int j = i; j < Math.min(n, i + step); j++) t += delays == null ? 0 : delays[j];
                times.add(t);
            }
            Styled s = new Styled();
            s.frames = out.toArray(new ImageStylizer.Result[0]);
            if (s.frames.length > 1) {
                s.delays = new int[times.size()];
                for (int i = 0; i < s.delays.length; i++) s.delays[i] = times.get(i);
            }
            return s;
        }
    }

    /** Готовые текстуры (по одной на кадр) и прямоугольник на холсте (в блоках от левого нижнего угла холста). */
    public static final class Variant {
        private final Picture owner;
        volatile Styled result;
        volatile boolean failed;
        private int[] textureIds;
        /** Момент конца каждого кадра от начала анимации, мс (null, если кадр один). */
        private int[] frameEnds;
        private boolean empty;
        public float x0, y0, x1, y1;
        /** Размер текстуры в пикселях. */
        public int width, height;
        long lastUsed;

        Variant(Picture owner) {
            this.owner = owner;
        }

        /** Готово к показу (в т.ч. "пусто" — когда картинка целиком вне холста). */
        public boolean isReady() {
            return textureIds != null || empty;
        }

        public boolean hasTexture() {
            return textureIds != null;
        }

        /** Первый кадр. */
        public int getTextureId() {
            return textureIds[0];
        }

        /** Кадр анимации в момент timeMs (анимация идёт по кругу); у обычной картинки — единственный кадр. */
        public int getTextureId(long timeMs) {
            if (frameEnds == null) return textureIds[0];
            long t = Math.floorMod(timeMs, (long) frameEnds[frameEnds.length - 1]);
            int i = 0;
            while (t >= frameEnds[i]) i++;
            return textureIds[i];
        }

        public void touch() {
            lastUsed = owner.lastUsed = System.currentTimeMillis();
        }

        void upload() {
            Styled s = result;
            if (s == null || textureIds != null || empty) return;
            result = null;
            ImageStylizer.Result first = s.frames[0];
            x0 = first.x0;
            y0 = first.y0;
            x1 = first.x1;
            y1 = first.y1;
            if (first.empty) {
                empty = true;
                return;
            }
            width = first.width;
            height = first.height;
            int[] ids = new int[s.frames.length];
            for (int i = 0; i < ids.length; i++) ids[i] = uploadFrame(s.frames[i]);
            if (s.delays != null) {
                frameEnds = new int[s.delays.length];
                int end = 0;
                for (int i = 0; i < frameEnds.length; i++) frameEnds[i] = end += s.delays[i];
            }
            textureIds = ids;
        }

        private static int uploadFrame(ImageStylizer.Result r) {
            BufferedImage img = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, r.width, r.height, r.pixels, 0, r.width);
            int id = GL11.glGenTextures();
            // blur = false: чёткие пиксели, как у карт и картин
            TextureUtil.uploadTextureImageAllocate(id, img, false, true);
            return id;
        }

        void release() {
            if (textureIds != null) {
                for (int id : textureIds) GL11.glDeleteTextures(id);
                textureIds = null;
                frameEnds = null;
            }
            empty = false;
        }
    }

    // ===================================================================== Локальные файлы

    /** Файлы-картинки локального источника ("local:" или "folder:") в порядке показа. */
    static List<File> sourceFiles(String key) throws IOException {
        if (key.startsWith("folder:")) return listFolder(key.substring("folder:".length()));
        if (key.startsWith("local:")) return listLocal(key.substring("local:".length()));
        throw new SourceException("minepapers.error.notFound");
    }

    /** Файл или подпапка внутри config/minepapers. */
    private static List<File> listLocal(String relative) throws IOException {
        File base = MinePapers.picturesDir.getCanonicalFile();
        File target = new File(base, relative).getCanonicalFile();
        if (!target.getPath().startsWith(base.getPath())) {
            throw new SourceException("minepapers.error.outside");
        }
        return listFiles(target);
    }

    /** Произвольная папка (или файл) на компьютере игрока. */
    private static List<File> listFolder(String path) throws IOException {
        String cleaned = path.trim();
        // Пути, скопированные из проводника "как путь", приходят в кавычках.
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        File target = new File(cleaned);
        if (!target.isAbsolute()) {
            throw new SourceException("minepapers.error.notAbsolute");
        }
        return listFiles(target.getCanonicalFile());
    }

    private static List<File> listFiles(File target) throws IOException {
        if (!target.exists()) {
            throw new SourceException("minepapers.error.notFound");
        }
        List<File> result = new ArrayList<File>();
        if (target.isDirectory()) {
            File[] files = target.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && isImageFile(f)) result.add(f);
                }
                Collections.sort(result);
            }
        } else {
            result.add(target);
        }
        return result;
    }

    /** Картинки, выложенные на сервер: ключ — подряд идущие SHA-1 в hex. */
    private static List<Picture> listShared(String hexes) {
        List<Picture> result = new ArrayList<Picture>();
        for (int i = 0; i + SharedImage.HEX_CHARS <= hexes.length(); i += SharedImage.HEX_CHARS) {
            final String hex = hexes.substring(i, i + SharedImage.HEX_CHARS);
            result.add(new Picture(hex, new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return ImageSync.read(hex);
                }
            }, hex));
        }
        return result;
    }

    private static Picture filePicture(final File file) {
        return new Picture(file.getName(), new Callable<Object>() {
            @Override
            public Object call() {
                return file;
            }
        });
    }

    /** Распакованная картинка для показа: один кадр или кадры анимации. */
    static final class Decoded {
        int[][] frames;
        /** Длительности кадров в мс (null, если кадр один). */
        int[] delays;
        int width, height, origW, origH;
    }

    /** Картинка для показа: исходник уменьшен до MAX_SOURCE_SIZE, у анимированного GIF — все кадры (см. GifFrames). */
    static Decoded decodeFrames(Object input) throws IOException {
        final int[] size = new int[2];
        Decoded d = withReader(input, size, new ReaderTask<Decoded>() {
            @Override
            public Decoded run(ImageReader reader) throws IOException {
                Decoded r = new Decoded();
                if (GifFrames.isAnimated(reader)) {
                    GifFrames.Animation a = GifFrames.read(reader, MAX_ANIMATION_SIZE, ANIMATION_PIXELS);
                    r.frames = a.frames.toArray(new int[0][]);
                    r.delays = new int[a.delays.size()];
                    for (int i = 0; i < r.delays.length; i++) r.delays[i] = a.delays.get(i);
                    r.width = a.width;
                    r.height = a.height;
                    size[0] = a.origW;
                    size[1] = a.origH;
                } else {
                    BufferedImage img = limitSize(readFirst(reader), MAX_SOURCE_SIZE);
                    r.width = img.getWidth();
                    r.height = img.getHeight();
                    r.frames = new int[][] { img.getRGB(0, 0, r.width, r.height, null, 0, r.width) };
                }
                return r;
            }
        });
        d.origW = size[0];
        d.origH = size[1];
        return d;
    }

    /** Первый (или единственный) кадр картинки. @param origSize сюда записывается исходный размер {ширина, высота} */
    static BufferedImage decode(Object input, int[] origSize) throws IOException {
        return withReader(input, origSize, new ReaderTask<BufferedImage>() {
            @Override
            public BufferedImage run(ImageReader reader) throws IOException {
                return readFirst(reader);
            }
        });
    }

    /** Огромные изображения (например 7000x5000) читаются сразу прореженными, чтобы не держать в памяти сотни мегабайт ради текстуры, которая всё равно будет маленькой. */
    private static BufferedImage readFirst(ImageReader reader) throws IOException {
        int w = reader.getWidth(0), h = reader.getHeight(0);
        ImageReadParam param = reader.getDefaultReadParam();
        // Прореживаем так, чтобы осталось не меньше MAX_SOURCE_SIZE — дальше аккуратно уменьшит limitSize.
        int step = Math.max(w, h) / MAX_SOURCE_SIZE;
        if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
        BufferedImage img = reader.read(0, param);
        if (img == null) throw new IOException("Unsupported image format");
        return img;
    }

    private interface ReaderTask<T> {
        T run(ImageReader reader) throws IOException;
    }

    /** Открывает картинку — файл или байты (в т.ч. с заголовком SharedImage) — и отдаёт читатель задаче. Читатель открыт с метаданными и возможностью вернуться назад: без этого не прочитать кадры GIF. @param origSize сюда записывается исходный размер {ширина, высота}; задача может его уточнить */
    private static <T> T withReader(Object input, int[] origSize, ReaderTask<T> task) throws IOException {
        int[] header = null;
        if (input instanceof byte[]) {
            byte[] bytes = (byte[]) input;
            header = SharedImage.readHeader(bytes);
            input = header == null ? new ByteArrayInputStream(bytes)
                : new ByteArrayInputStream(bytes, SharedImage.HEADER_BYTES, bytes.length - SharedImage.HEADER_BYTES);
        }
        ImageInputStream in = ImageIO.createImageInputStream(input);
        if (in == null) throw new IOException("Cannot open image");
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, false, false);
                origSize[0] = reader.getWidth(0);
                origSize[1] = reader.getHeight(0);
                T result = task.run(reader);
                // Картинку с сервера могли уменьшить перед отправкой — вписывание считаем по исходному размеру.
                if (header != null && header[0] > 0 && header[1] > 0) {
                    origSize[0] = header[0];
                    origSize[1] = header[1];
                }
                return result;
            } finally {
                reader.dispose();
            }
        } finally {
            in.close();
        }
    }

    // ===================================================================== Яндекс Диск (публичный API, авторизация не нужна)

    private static List<Picture> listYandex(final String publicUrl) throws IOException {
        JsonObject root = getJson(YANDEX_API + "?public_key=" + encode(publicUrl) + "&limit=1000&sort=name");
        List<Picture> result = new ArrayList<Picture>();

        if ("file".equals(getString(root, "type"))) {
            // Ссылка на один файл, а не на папку
            if (isSupportedMime(root)) {
                result.add(yandexPicture(publicUrl, null, getString(root, "name")));
            }
            return result;
        }

        JsonObject embedded = root.getAsJsonObject("_embedded");
        if (embedded == null) return result;
        JsonArray items = embedded.getAsJsonArray("items");
        if (items == null) return result;

        for (JsonElement el : items) {
            JsonObject item = el.getAsJsonObject();
            if ("file".equals(getString(item, "type")) && isSupportedMime(item)) {
                result.add(yandexPicture(publicUrl, getString(item, "path"), getString(item, "name")));
            }
        }
        return result;
    }

    private static boolean isSupportedMime(JsonObject item) {
        String mime = getString(item, "mime_type");
        if (mime != null) {
            mime = mime.toLowerCase(Locale.ROOT);
            return mime.equals("image/jpeg") || mime.equals("image/pjpeg") || mime.equals("image/jpg")
                || mime.equals("image/png") || mime.equals("image/gif")
                || mime.equals("image/bmp") || mime.equals("image/x-ms-bmp");
        }
        String name = getString(item, "name");
        return name != null && isImageFile(new File(name));
    }

    private static Picture yandexPicture(final String publicUrl, final String path, String name) {
        return new Picture(name, new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                // Прямые ссылки на скачивание живут недолго, поэтому получаем свежую при каждой загрузке.
                String api = YANDEX_API + "/download?public_key=" + encode(publicUrl)
                    + (path != null ? "&path=" + encode(path) : "");
                String href = getString(getJson(api), "href");
                if (href == null) throw new IOException("Yandex API: no download link");
                return download(href);
            }
        });
    }

    // ===================================================================== Прямая ссылка на картинку

    private static List<Picture> listUrl(final String url) throws IOException {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new SourceException("minepapers.error.badUrl");
        }
        // Скачиваем сразу: если по ссылке нет файла или это не картинка, в окне настроек будет понятная ошибка.
        byte[] data = download(url);
        ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
        try {
            if (in == null || !ImageIO.getImageReaders(in).hasNext()) throw new SourceException("minepapers.error.notImage");
        } finally {
            if (in != null) in.close();
        }
        // Первый показ берёт уже скачанное, после выгрузки из памяти картинка скачивается заново.
        final AtomicReference<byte[]> first = new AtomicReference<byte[]>(data);
        String name = url.substring(url.lastIndexOf('/') + 1);
        List<Picture> list = new ArrayList<Picture>();
        list.add(new Picture(name.isEmpty() ? url : name, () -> {
            byte[] cached = first.getAndSet(null);
            return cached != null ? cached : download(url);
        }));
        return list;
    }

    // ===================================================================== HTTP / утилиты

    private static JsonObject getJson(String url) throws IOException {
        HttpURLConnection conn = open(url);
        int code = conn.getResponseCode();
        if (code >= 400) {
            String body = readAll(conn.getErrorStream(), 1024 * 1024);
            String description = null;
            try {
                description = getString(new JsonParser().parse(body).getAsJsonObject(), "description");
            } catch (Exception ignored) {}
            if (code == 404) throw new SourceException("minepapers.error.notFound");
            throw new IOException("HTTP " + code + (description != null ? ": " + description : ""));
        }
        return new JsonParser().parse(readAll(conn.getInputStream(), 16 * 1024 * 1024)).getAsJsonObject();
    }

    private static byte[] download(String url) throws IOException {
        HttpURLConnection conn = open(url);
        int code = conn.getResponseCode();
        if (code == 404) throw new SourceException("minepapers.error.notFound");
        if (code >= 400) throw new IOException("HTTP " + code);
        return readBytes(conn.getInputStream(), MAX_DOWNLOAD_BYTES);
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        return conn;
    }

    private static String readAll(InputStream in, int limit) throws IOException {
        return in == null ? "" : new String(readBytes(in, limit), "UTF-8");
    }

    private static byte[] readBytes(InputStream in, int limit) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
                if (out.size() > limit) throw new IOException("File is too large");
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static String getString(JsonObject obj, String name) {
        JsonElement el = obj.get(name);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static String encode(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    /** Уменьшает слишком большие картинки (с сохранением пропорций). */
    static BufferedImage limitSize(BufferedImage img, int max) {
        int w = img.getWidth(), h = img.getHeight();
        if (w <= max && h <= max) return img;

        double scale = (double) max / Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    /** Ошибка с ключом локализации вместо текста. */
    private static final class SourceException extends IOException {
        SourceException(String langKey) {
            super(langKey);
        }
    }
}
