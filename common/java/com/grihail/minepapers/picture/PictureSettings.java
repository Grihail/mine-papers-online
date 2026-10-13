package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.grihail.minepapers.network.SharedImage;

/** Место и настройки картины — всё, кроме материалов (в них лежат предметы, а они у каждой версии свои). Версия наследует отсюда PictureFrame и добавляет материалы; NBT читается и пишется через адаптер Tag. Место: (x, y, z) — клетка "рамки", от левого нижнего угла которой отсчитывается холст; facing — сторона (0..5), куда смотрит картина. */
public class PictureSettings {

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
    public static final int MODE_PLATE = 3;     // пластина: сама картинка толщиной в пиксель, торцы — её крайние пиксели
    public static final int MODE_COUNT = 4;

    public static final float PIXEL = 1f / 16f;
    public static final float MIN_SIZE_BLOCKS = PIXEL;
    public static final float MAX_SIZE_BLOCKS = 64f;
    public static final int MIN_PPB = 1;
    public static final int MAX_PPB = 256;
    public static final int MAX_STRING = 512;
    public static final int MAX_TITLE = 64;
    /** Сколько картинок из локальной папки можно выложить на сервер для одной рамки. */
    public static final int MAX_FILES = 256;
    public static final byte[] NO_FILES = new byte[0];
    /** Насколько далеко от холста можно открыть настройки картины (см. editReach). */
    public static final double EDIT_DISTANCE = 10.0;

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

    /** Внутренний отступ картинки от краёв холста (со всех сторон): в блоках или в пикселях. */
    public float padding = 0f;
    public boolean paddingInPixels = true;

    /** Заголовок и описание картины (вкладка "Описание"), видны в подсказке предмета. */
    public String title = "";
    public String description = "";

    /** Владелец заприваченной картины (null — картину может менять любой) и его ник — для подсказок. Меняет только сервер (PacketSaveFrame), в конфиг JSON не попадает. */
    public UUID owner;
    public String ownerName = "";

    public boolean isLocked() {
        return owner != null;
    }

    /** Может ли игрок менять настройки и материалы картины. */
    public boolean mayEdit(UUID player) {
        return owner == null || owner.equals(player);
    }

    /** Как картинка вписывается в холст (масштабирование всегда от центра) */
    public int fitMode = FIT_COVER;
    /** Разрешение стилизации: пикселей картинки на один блок (128 = как карта, 16 = как ванильная картина) */
    public int pixelsPerBlock = 128;
    public int style = STYLE_MAP_SOFT_DITHER;
    public int mode = MODE_PANEL;

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

    /** Отступ в блоках; картинке остаётся хотя бы пиксель холста. */
    public float getPaddingBlocks() {
        float p = paddingInPixels ? padding / 16f : padding;
        float max = (Math.min(getWidthBlocks(), getHeightBlocks()) - PIXEL) / 2;
        return Math.max(0, Math.min(p, max));
    }

    /** С какого расстояния (в блоках) от центра картины игрок может открыть её настройки или сломать её: EDIT_DISTANCE от любой точки холста, поэтому к нему добавляется полудиагональ холста. Расстояние до центра версия считает ванильным расстоянием до сущности — его поправляют моды, которые переносят картину в свои координаты (движущиеся конструкции). */
    public double editReach() {
        double w = getWidthBlocks(), h = getHeightBlocks(), d = getDepth();
        return EDIT_DISTANCE + Math.sqrt(w * w + h * h + d * d) / 2;
    }

    /** Толщина картины от стены (и её хитбокса). */
    public float getDepth() {
        switch (mode) {
            case MODE_REALISTIC: return 2 * PIXEL;
            case MODE_DECAL: return PIXEL / 2;
            default: return PIXEL;
        }
    }

    // ---------- NBT ----------

    /** NBT-тег глазами общего кода: у каждой версии свой класс тега, версия оборачивает его в этот адаптер. Отсутствующий ключ читается как 0, "" или пустой массив — как в ванильных тегах. */
    public interface Tag {
        boolean contains(String key);
        byte getByte(String key);
        short getShort(String key);
        int getInt(String key);
        float getFloat(String key);
        boolean getBoolean(String key);
        String getString(String key);
        byte[] getByteArray(String key);
        /** null — ключа нет или он испорчен. Формат записи у версий разный: до 1.13 — строка, после — массив чисел. */
        UUID getUuid(String key);

        void putByte(String key, byte value);
        void putShort(String key, short value);
        void putInt(String key, int value);
        void putFloat(String key, float value);
        void putBoolean(String key, boolean value);
        void putString(String key, String value);
        void putByteArray(String key, byte[] value);
        void putUuid(String key, UUID value);
    }

    public void writePlacement(Tag tag) {
        tag.putInt("FX", x);
        tag.putInt("FY", y);
        tag.putInt("FZ", z);
        tag.putByte("Facing", (byte) getFacing());
        tag.putByte("Rot", (byte) getRotation());
        tag.putFloat("Surface", surface);
    }

    public void readPlacement(Tag tag) {
        x = tag.getInt("FX");
        y = tag.getInt("FY");
        z = tag.getInt("FZ");
        facing = tag.getByte("Facing");
        facing = getFacing();
        rotation = tag.getByte("Rot") & 3;
        float s = tag.getFloat("Surface");
        surface = s >= -1f && s <= 0f ? s : 0f;
    }

    /** Настройки без материалов (их пишет версия). */
    public void writeSettings(Tag tag) {
        tag.putByte("Src", (byte) source);
        tag.putString("Local", localPath);
        tag.putString("Url", yandexUrl);
        tag.putString("Folder", folderPath);
        tag.putString("ImageUrl", imageUrl);
        tag.putInt("Interval", interval);
        tag.putFloat("W", width);
        tag.putFloat("H", height);
        tag.putBoolean("SizePx", sizeInPixels);
        tag.putFloat("OX", offsetX);
        tag.putFloat("OY", offsetY);
        tag.putBoolean("PosPx", offsetInPixels);
        tag.putFloat("Pad", padding);
        tag.putBoolean("PadPx", paddingInPixels);
        if (!title.isEmpty()) tag.putString("Title", title);
        if (!description.isEmpty()) tag.putString("Desc", description);
        if (owner != null) {
            tag.putUuid("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        tag.putByte("Fit", (byte) fitMode);
        tag.putShort("PPB", (short) pixelsPerBlock);
        tag.putByte("Style", (byte) style);
        tag.putByte("Mode", (byte) mode);
        if (files.length > 0) tag.putByteArray("Files", files);
    }

    /** Читает настройки без материалов и приводит их к допустимым значениям (данные могут прийти от клиента). */
    public void readSettings(Tag tag) {
        source = range(tag.getByte("Src"), 0, SOURCE_COUNT - 1);
        localPath = limit(tag.getString("Local"));
        yandexUrl = limit(tag.getString("Url"));
        folderPath = limit(tag.getString("Folder"));
        imageUrl = limit(tag.getString("ImageUrl"));
        interval = range(tag.getInt("Interval"), 1, 86400);

        // Размер всегда явный: от него зависит хитбокс. Старое "0 = авто" превращаем в квадрат.
        sizeInPixels = tag.getBoolean("SizePx");
        float sizeScale = sizeInPixels ? 16f : 1f;
        float w = tag.contains("W") ? tag.getFloat("W") : 1f;
        float h = tag.contains("H") ? tag.getFloat("H") : 1f;
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

        paddingInPixels = !tag.contains("PadPx") || tag.getBoolean("PadPx");
        padding = clamp(tag.getFloat("Pad"), 0, MAX_SIZE_BLOCKS / 2 * (paddingInPixels ? 16f : 1f));

        title = limit(tag.getString("Title").replace("\r", "").replace("\n", " "), MAX_TITLE);
        description = limit(tag.getString("Desc").replace("\r", ""), MAX_STRING);
        owner = tag.getUuid("Owner");
        ownerName = owner != null ? limit(tag.getString("OwnerName"), MAX_TITLE) : "";

        fitMode = tag.contains("Fit") ? range(tag.getByte("Fit"), 0, FIT_COUNT - 1) : FIT_COVER;
        pixelsPerBlock = tag.contains("PPB") ? range(tag.getShort("PPB"), MIN_PPB, MAX_PPB) : 128;
        style = tag.contains("Style") ? range(tag.getByte("Style"), 0, STYLE_COUNT - 1) : STYLE_MAP_SOFT_DITHER;
        mode = range(tag.getByte("Mode"), 0, MODE_COUNT - 1);
        byte[] f = tag.getByteArray("Files");
        files = f.length % SharedImage.HASH_BYTES == 0 && f.length <= MAX_FILES * SharedImage.HASH_BYTES ? f : NO_FILES;
    }

    /** Текст, разбитый на строки не длиннее width символов (по словам; переводы строк сохраняются) — для подсказки предмета. */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<String>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split(" +")) {
                while (word.length() > width) {
                    if (line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    lines.add(word.substring(0, width));
                    word = word.substring(width);
                }
                if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            if (line.length() > 0) lines.add(line.toString());
        }
        return lines;
    }

    /** UUID из строки (пустая или испорченная — null). */
    public static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected static String limit(String s) {
        return limit(s, MAX_STRING);
    }

    protected static String limit(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    protected static int range(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    protected static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) return min > 0 ? min : Math.min(0, max);
        return Math.max(min, Math.min(max, v));
    }
}
