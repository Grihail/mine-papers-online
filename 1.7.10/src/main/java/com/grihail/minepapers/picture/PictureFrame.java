package com.grihail.minepapers.picture;

import com.grihail.minepapers.network.SharedImage;

import net.minecraft.nbt.NBTTagCompound;

/** Настройки и место картины. Живёт внутри EntityPicture, а также служит "призраком" будущей картины (подсказка перед установкой, проверки) и разбором настроек из предмета. Место: (x, y, z) — клетка "рамки", от левого нижнего угла которой отсчитывается холст; facing — сторона (2..5), куда смотрит картина. Картинки грузятся только на клиенте. */
public class PictureFrame {

    public static final int SOURCE_LOCAL = 0;
    public static final int SOURCE_YANDEX = 1;
    public static final int SOURCE_FOLDER = 2;
    /** Одна картинка по прямой ссылке (http/https). */
    public static final int SOURCE_URL = 3;
    public static final int SOURCE_COUNT = 4;

    /** Режимы вписывания картинки в холст — как CSS object-fit. */
    public static final int FIT_FILL = 0;
    public static final int FIT_CONTAIN = 1;
    public static final int FIT_COVER = 2;
    public static final int FIT_NONE = 3;
    public static final int FIT_SCALE_DOWN = 4;
    public static final int FIT_COUNT = 5;

    /** Стилизация цветов. */
    public static final int STYLE_ORIGINAL = 0;
    public static final int STYLE_MAP = 1;
    public static final int STYLE_MAP_DITHER = 2;
    public static final int STYLE_MAP_SOFT_DITHER = 3;
    public static final int STYLE_COUNT = 4;

    /** Вид картины. */
    public static final int MODE_PANEL = 0;     // холст-панель, торцы обшиты рамкой
    public static final int MODE_REALISTIC = 1; // настоящая рамка: заходит на картину и выступает над ней
    public static final int MODE_DECAL = 2;     // граффити: без рамки, вплотную к стене
    public static final int MODE_COUNT = 3;

    public static final float PIXEL = 1f / 16f;
    public static final float MIN_SIZE_BLOCKS = PIXEL;
    public static final float MAX_SIZE_BLOCKS = 64f;
    public static final int MIN_PPB = 1;
    public static final int MAX_PPB = 256;
    public static final int MAX_STRING = 512;
    /** Сколько картинок из локальной папки можно выложить на сервер для одной рамки. */
    public static final int MAX_FILES = 256;
    public static final byte[] NO_FILES = new byte[0];

    // ---------- Место ----------

    public int x, y, z;
    /** Сторона (0..5, как у граней блока), куда смотрит картина: 0 — с потолка вниз, 1 — с пола вверх, 2..5 — со стен. */
    public int facing = 3;
    /** На полу и потолке: куда смотрел игрок при установке (0 — юг, 1 — запад, 2 — север, 3 — восток), там верх картинки. */
    public int rotation;
    /** Где задняя сторона картины относительно границы клетки рамки, в блоках вдоль нормали (0 или меньше): картина лежит ровно на грани, по которой кликнули, — на полублоке, ковре, боковине забора. */
    public float surface;

    // ---------- Настройки ----------

    public int source = SOURCE_LOCAL;
    /** Картинки локального источника, выложенные на сервер: подряд идущие SHA-1 (по 20 байт). Их видят все игроки; пусто — файлы ещё не отправлены, и картинку видит только тот, у кого они есть. */
    public byte[] files = NO_FILES;
    private byte[] keyFiles;
    private String filesKey;
    /** Имя файла или подпапки внутри config/minepapers */
    public String localPath = "";
    /** Ссылка на публичную папку Яндекс Диска */
    public String yandexUrl = "";
    /** Абсолютный путь к папке (или файлу) на компьютере игрока */
    public String folderPath = "";
    /** Прямая ссылка на картинку */
    public String imageUrl = "";
    /** Интервал слайдшоу в секундах */
    public int interval = 10;

    /** Ширина и высота холста (и хитбокса): в блоках или в пикселях (1/16 блока). */
    public float width = 1f;
    public float height = 1f;
    public boolean sizeInPixels = false;

    /** Смещение левого нижнего угла холста от левого нижнего угла клетки рамки: вправо и вверх */
    public float offsetX = 0f;
    public float offsetY = 0f;
    public boolean offsetInPixels = false;

    /** Как картинка вписывается в холст (масштабирование всегда от центра) */
    public int fitMode = FIT_COVER;
    /** Разрешение стилизации: пикселей картинки на один блок (128 = как карта, 16 = как ванильная картина) */
    public int pixelsPerBlock = 128;
    public int style = STYLE_MAP_SOFT_DITHER;
    public int mode = MODE_PANEL;

    /** Текстура рамки из предмета: имя в реестре предметов ("" — обычная рамка) и его метаданные. */
    public String borderItem = "";
    public int borderMeta = 0;

    /** Клиентский кэш последнего нарисованного варианта картинки (см. client.RenderPicture). На сервере не используется. */
    public Object clientRenderState;

    /** Сторона (0..5), в которую смотрит картина. */
    public int getFacing() {
        return facing >= 0 && facing <= 5 ? facing : 3;
    }

    public int getRotation() {
        return rotation & 3;
    }

    /** Своё зерно случайного порядка слайдшоу у каждой рамки (одинаковое у всех игроков). */
    public long getSlideSeed() {
        return x * 73856093L ^ y * 19349663L ^ z * 83492791L;
    }

    /** Ключ источника картинок, по которому клиент кэширует загруженные изображения. */
    public String getSourceKey() {
        if (hasSharedFiles()) {
            if (keyFiles != files) {
                filesKey = "shared:" + SharedImage.hex(files);
                keyFiles = files;
            }
            return filesKey;
        }
        return getOriginKey();
    }

    /** Источник, как его задал игрок (путь на его компьютере или ссылка) — без учёта копий на сервере. */
    public String getOriginKey() {
        return makeSourceKey(source, localPath, yandexUrl, folderPath, imageUrl);
    }

    public boolean hasSharedFiles() {
        return !isNetworkSource() && files.length > 0;
    }

    /** Картинки из интернета (Яндекс Диск, прямая ссылка): каждый игрок скачивает их сам, выкладывать на сервер не нужно. */
    public boolean isNetworkSource() {
        return source == SOURCE_YANDEX || source == SOURCE_URL;
    }

    public static String makeSourceKey(int source, String localPath, String yandexUrl, String folderPath, String imageUrl) {
        switch (source) {
            case SOURCE_YANDEX:
                return yandexUrl.trim().isEmpty() ? null : "yandex:" + yandexUrl.trim();
            case SOURCE_FOLDER:
                return folderPath.trim().isEmpty() ? null : "folder:" + folderPath.trim();
            case SOURCE_URL:
                return imageUrl.trim().isEmpty() ? null : "url:" + imageUrl.trim();
            default:
                return localPath.trim().isEmpty() ? null : "local:" + localPath.trim();
        }
    }

    /** Путь или ссылка текущего источника (для подсказки предмета). */
    public String getSourcePath() {
        switch (source) {
            case SOURCE_YANDEX: return yandexUrl;
            case SOURCE_FOLDER: return folderPath;
            case SOURCE_URL: return imageUrl;
            default: return localPath;
        }
    }

    public float getWidthBlocks() {
        return sizeInPixels ? width / 16f : width;
    }

    public float getHeightBlocks() {
        return sizeInPixels ? height / 16f : height;
    }

    public float getOffsetXBlocks() {
        return offsetInPixels ? offsetX / 16f : offsetX;
    }

    public float getOffsetYBlocks() {
        return offsetInPixels ? offsetY / 16f : offsetY;
    }

    /** Толщина картины от стены (и её хитбокса). */
    public float getDepth() {
        switch (mode) {
            case MODE_REALISTIC: return 2 * PIXEL;
            case MODE_DECAL: return PIXEL / 2;
            default: return PIXEL;
        }
    }

    public void writePlacement(NBTTagCompound tag) {
        tag.setInteger("FX", x);
        tag.setInteger("FY", y);
        tag.setInteger("FZ", z);
        tag.setByte("Facing", (byte) getFacing());
        tag.setByte("Rot", (byte) getRotation());
        tag.setFloat("Surface", surface);
    }

    public void readPlacement(NBTTagCompound tag) {
        x = tag.getInteger("FX");
        y = tag.getInteger("FY");
        z = tag.getInteger("FZ");
        facing = tag.getByte("Facing");
        facing = getFacing();
        rotation = tag.getByte("Rot") & 3;
        float s = tag.getFloat("Surface");
        surface = s >= -1f && s <= 0f ? s : 0f;
    }

    public void writeSettings(NBTTagCompound tag) {
        tag.setByte("Src", (byte) source);
        tag.setString("Local", localPath);
        tag.setString("Url", yandexUrl);
        tag.setString("Folder", folderPath);
        tag.setString("ImageUrl", imageUrl);
        tag.setInteger("Interval", interval);
        tag.setFloat("W", width);
        tag.setFloat("H", height);
        tag.setBoolean("SizePx", sizeInPixels);
        tag.setFloat("OX", offsetX);
        tag.setFloat("OY", offsetY);
        tag.setBoolean("PosPx", offsetInPixels);
        tag.setByte("Fit", (byte) fitMode);
        tag.setShort("PPB", (short) pixelsPerBlock);
        tag.setByte("Style", (byte) style);
        tag.setByte("Mode", (byte) mode);
        tag.setString("Border", borderItem);
        tag.setShort("BorderMeta", (short) borderMeta);
        if (files.length > 0) tag.setByteArray("Files", files);
    }

    /** Читает настройки и приводит их к допустимым значениям (данные могут прийти от клиента). */
    public void readSettings(NBTTagCompound tag) {
        source = range(tag.getByte("Src"), 0, SOURCE_COUNT - 1);
        localPath = limit(tag.getString("Local"));
        yandexUrl = limit(tag.getString("Url"));
        folderPath = limit(tag.getString("Folder"));
        imageUrl = limit(tag.getString("ImageUrl"));
        interval = range(tag.getInteger("Interval"), 1, 86400);

        // Размер всегда явный: от него зависит хитбокс. Старое "0 = авто" превращаем в квадрат.
        sizeInPixels = tag.getBoolean("SizePx");
        float sizeScale = sizeInPixels ? 16f : 1f;
        float w = tag.hasKey("W") ? tag.getFloat("W") : 1f;
        float h = tag.hasKey("H") ? tag.getFloat("H") : 1f;
        if (!(w > 0)) w = h > 0 ? h : sizeScale;
        if (!(h > 0)) h = w;
        width = clamp(w, MIN_SIZE_BLOCKS * sizeScale, MAX_SIZE_BLOCKS * sizeScale);
        height = clamp(h, MIN_SIZE_BLOCKS * sizeScale, MAX_SIZE_BLOCKS * sizeScale);

        // Холст должен перекрывать клетку рамки (где кликнули при установке) хотя бы на пиксель.
        offsetInPixels = tag.getBoolean("PosPx");
        float offsetScale = offsetInPixels ? 16f : 1f;
        float minX = (PIXEL - getWidthBlocks()) * offsetScale, maxX = (1 - PIXEL) * offsetScale;
        float minY = (PIXEL - getHeightBlocks()) * offsetScale, maxY = (1 - PIXEL) * offsetScale;
        offsetX = clamp(tag.getFloat("OX"), minX, maxX);
        offsetY = clamp(tag.getFloat("OY"), minY, maxY);

        fitMode = tag.hasKey("Fit") ? range(tag.getByte("Fit"), 0, FIT_COUNT - 1) : FIT_COVER;
        pixelsPerBlock = tag.hasKey("PPB") ? range(tag.getShort("PPB"), MIN_PPB, MAX_PPB) : 128;
        style = tag.hasKey("Style") ? range(tag.getByte("Style"), 0, STYLE_COUNT - 1) : STYLE_MAP_SOFT_DITHER;
        mode = range(tag.getByte("Mode"), 0, MODE_COUNT - 1);
        borderItem = limit(tag.getString("Border"));
        borderMeta = range(tag.getShort("BorderMeta"), 0, Short.MAX_VALUE);
        byte[] f = tag.getByteArray("Files");
        files = f.length % SharedImage.HASH_BYTES == 0 && f.length <= MAX_FILES * SharedImage.HASH_BYTES ? f : NO_FILES;
    }

    /** true, если настройки отличаются от новой рамки (тогда их стоит сохранить в выпавшем предмете). */
    public boolean isCustomized() {
        NBTTagCompound mine = new NBTTagCompound(), def = new NBTTagCompound();
        writeSettings(mine);
        new PictureFrame().writeSettings(def);
        return !mine.equals(def);
    }

    private static String limit(String s) {
        return s.length() > MAX_STRING ? s.substring(0, MAX_STRING) : s;
    }

    private static int range(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) return min > 0 ? min : Math.min(0, max);
        return Math.max(min, Math.min(max, v));
    }
}
