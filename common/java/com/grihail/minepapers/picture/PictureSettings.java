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

    /** Вид картины — сочетание рамки (FRAME_*) и холста (CANVAS_*), см. getFrameShape и getCanvasKind. Первые четыре — виды прежних версий, дальше — новые сочетания. */
    public static final int MODE_PANEL = 0;     // холст-панель, торцы обшиты рамкой
    public static final int MODE_REALISTIC = 1; // настоящая рамка: заходит на картину и выступает над ней
    public static final int MODE_DECAL = 2;     // граффити: без рамки, вплотную к стене
    public static final int MODE_PLATE = 3;     // пластина: сама картинка толщиной в пиксель, торцы — её крайние пиксели
    public static final int LOOK_COUNT = 8;

    /** Рамка: нет, обшивка торцов холста, багет (заходит на картину и выступает над ней). */
    public static final int FRAME_NONE = 0, FRAME_EDGES = 1, FRAME_MOLDING = 2, FRAME_COUNT = 3;
    /** Холст: нет (картинка прямо на стене), из блока материала, сама картинка толщиной в пиксель. */
    public static final int CANVAS_NONE = 0, CANVAS_BLOCK = 1, CANVAS_PICTURE = 2, CANVAS_COUNT = 3;
    /** Рамка и холст каждого вида (по номеру вида). Обшивки без холста нет: обшивать нечего. */
    private static final int[] LOOK_FRAME = { FRAME_EDGES, FRAME_MOLDING, FRAME_NONE, FRAME_NONE,
        FRAME_NONE, FRAME_MOLDING, FRAME_EDGES, FRAME_MOLDING };
    private static final int[] LOOK_CANVAS = { CANVAS_BLOCK, CANVAS_BLOCK, CANVAS_NONE, CANVAS_PICTURE,
        CANVAS_BLOCK, CANVAS_NONE, CANVAS_PICTURE, CANVAS_PICTURE };
    /** Ближайший вид прежних версий (для конфига, который вставят в версию без новых видов). */
    private static final int[] LOOK_CLASSIC = { MODE_PANEL, MODE_REALISTIC, MODE_DECAL, MODE_PLATE,
        MODE_PANEL, MODE_REALISTIC, MODE_PANEL, MODE_REALISTIC };

    public static final float PIXEL = 1f / 16f;
    public static final float MIN_SIZE_BLOCKS = PIXEL;
    public static final float MAX_SIZE_BLOCKS = 64f;
    public static final int MIN_PPB = 1;
    public static final int MAX_PPB = 256;
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

    /** Сервер только что прочитал настройки от клиента игрока player (PacketSaveFrame) — возвращает то, что клиент сам менять не может: список картинок на сервере (его меняет только сервер, PacketOfferFiles) и владельца. Владельца клиент только просит: замок закрыт — картина становится его (если ничья), открыт — снова ничья; чужое имя подставить нельзя. Параметры — значения до чтения. */
    public void keepServerFields(String origin, byte[] files, UUID owner, String ownerName, UUID player, String playerName) {
        this.files = origin != null && origin.equals(getOriginKey()) ? files : NO_FILES;
        boolean lock = this.owner != null;
        this.owner = owner;
        this.ownerName = ownerName;
        if (lock && owner == null) {
            this.owner = player;
            this.ownerName = playerName;
        } else if (!lock) {
            this.owner = null; // сюда доходит только владелец или когда картина ничья
            this.ownerName = "";
        }
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
        return facing;
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
        if (getFrameShape() == FRAME_MOLDING) return 2 * PIXEL;
        return getCanvasKind() == CANVAS_NONE ? PIXEL / 2 : PIXEL;
    }

    public int getFrameShape() {
        return LOOK_FRAME[mode];
    }

    public int getCanvasKind() {
        return LOOK_CANVAS[mode];
    }

    /** Вид из рамки и холста; обшивка без холста — без рамки. */
    public static int look(int frameShape, int canvasKind) {
        if (canvasKind == CANVAS_NONE && frameShape == FRAME_EDGES) frameShape = FRAME_NONE;
        for (int i = 0; i < LOOK_COUNT; i++) {
            if (LOOK_FRAME[i] == frameShape && LOOK_CANVAS[i] == canvasKind) return i;
        }
        return MODE_PANEL;
    }

    public static int frameShapeOf(int look) {
        return LOOK_FRAME[look];
    }

    public static int canvasKindOf(int look) {
        return LOOK_CANVAS[look];
    }

    public static int classicLook(int look) {
        return LOOK_CLASSIC[look];
    }

    /** Нужен ли этому виду блок рамки. */
    public boolean usesBorder() {
        return getFrameShape() != FRAME_NONE;
    }

    /** Нужен ли этому виду блок холста. */
    public boolean usesCanvas() {
        return getCanvasKind() == CANVAS_BLOCK;
    }

    // ---------- NBT ----------

    /** NBT-тег глазами общего кода: у каждой версии свой класс тега, версия оборачивает его в этот адаптер. Отсутствующий ключ читается как 0, "" или пустой массив — как в ванильных тегах. */
    public interface Tag {
        boolean contains(String key);
        /** Любое число тега (byte, short, int) — как int. */
        int getInt(String key);
        float getFloat(String key);
        String getString(String key);
        byte[] getByteArray(String key);
        /** null — ключа нет или он испорчен. Формат записи у версий разный: до 1.13 — строка, после — массив чисел. */
        UUID getUuid(String key);

        void putInt(String key, int value);
        void putFloat(String key, float value);
        void putString(String key, String value);
        void putByteArray(String key, byte[] value);
        void putUuid(String key, UUID value);
    }

    public void writePlacement(Tag tag) {
        tag.putInt("FX", x);
        tag.putInt("FY", y);
        tag.putInt("FZ", z);
        tag.putInt("Facing", getFacing());
        tag.putInt("Rot", getRotation());
        tag.putFloat("Surface", surface);
    }

    public void readPlacement(Tag tag) {
        x = tag.getInt("FX");
        y = tag.getInt("FY");
        z = tag.getInt("FZ");
        facing = tag.getInt("Facing");
        rotation = tag.getInt("Rot");
        surface = tag.getFloat("Surface");
    }

    /** Настройки без материалов (их пишет версия). */
    public void writeSettings(Tag tag) {
        tag.putInt("Src", source);
        tag.putString("Local", localPath);
        tag.putString("Url", yandexUrl);
        tag.putString("Folder", folderPath);
        tag.putString("ImageUrl", imageUrl);
        tag.putInt("Interval", interval);
        tag.putFloat("W", width);
        tag.putFloat("H", height);
        tag.putInt("SizePx", sizeInPixels ? 1 : 0);
        tag.putFloat("OX", offsetX);
        tag.putFloat("OY", offsetY);
        tag.putInt("PosPx", offsetInPixels ? 1 : 0);
        tag.putFloat("Pad", padding);
        tag.putInt("PadPx", paddingInPixels ? 1 : 0);
        if (!title.isEmpty()) tag.putString("Title", title);
        if (!description.isEmpty()) tag.putString("Desc", description);
        if (owner != null) {
            tag.putUuid("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        tag.putInt("Fit", fitMode);
        tag.putInt("PPB", pixelsPerBlock);
        tag.putInt("Style", style);
        tag.putInt("Mode", mode);
        if (files.length > 0) tag.putByteArray("Files", files);
    }

    /** Читает настройки без материалов. Размер и место холста приводятся к допустимым: от них зависит хитбокс. */
    public void readSettings(Tag tag) {
        source = tag.getInt("Src");
        localPath = tag.getString("Local");
        yandexUrl = tag.getString("Url");
        folderPath = tag.getString("Folder");
        imageUrl = tag.getString("ImageUrl");
        interval = Math.max(1, tag.getInt("Interval"));

        // Размер всегда явный: от него зависит хитбокс. Старое "0 = авто" превращаем в квадрат.
        sizeInPixels = tag.getInt("SizePx") != 0;
        float sizeScale = sizeInPixels ? 16f : 1f;
        float w = tag.contains("W") ? tag.getFloat("W") : 1f;
        float h = tag.contains("H") ? tag.getFloat("H") : 1f;
        if (!(w > 0)) w = h > 0 ? h : sizeScale;
        if (!(h > 0)) h = w;
        width = clamp(w, MIN_SIZE_BLOCKS * sizeScale, MAX_SIZE_BLOCKS * sizeScale);
        height = clamp(h, MIN_SIZE_BLOCKS * sizeScale, MAX_SIZE_BLOCKS * sizeScale);

        // Холст должен перекрывать клетку рамки (где кликнули при установке) хотя бы на пиксель.
        offsetInPixels = tag.getInt("PosPx") != 0;
        float offsetScale = offsetInPixels ? 16f : 1f;
        float minX = (PIXEL - getWidthBlocks()) * offsetScale, maxX = (1 - PIXEL) * offsetScale;
        float minY = (PIXEL - getHeightBlocks()) * offsetScale, maxY = (1 - PIXEL) * offsetScale;
        offsetX = clamp(tag.getFloat("OX"), minX, maxX);
        offsetY = clamp(tag.getFloat("OY"), minY, maxY);

        paddingInPixels = !tag.contains("PadPx") || tag.getInt("PadPx") != 0;
        padding = clamp(tag.getFloat("Pad"), 0, MAX_SIZE_BLOCKS / 2 * (paddingInPixels ? 16f : 1f));

        title = tag.getString("Title").replace("\r", "").replace("\n", " ");
        description = tag.getString("Desc").replace("\r", "");
        owner = tag.getUuid("Owner");
        ownerName = tag.getString("OwnerName");

        fitMode = tag.contains("Fit") ? tag.getInt("Fit") : FIT_COVER;
        pixelsPerBlock = tag.contains("PPB") ? range(tag.getInt("PPB"), MIN_PPB, MAX_PPB) : 128;
        style = tag.contains("Style") ? tag.getInt("Style") : STYLE_MAP_SOFT_DITHER;
        mode = range(tag.getInt("Mode"), 0, LOOK_COUNT - 1);
        files = tag.getByteArray("Files");
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

    private static int range(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
