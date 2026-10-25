package com.grihail.minepapers.client;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.grihail.minepapers.picture.MapTag;
import com.grihail.minepapers.picture.Numbers;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.PictureSettings;

/** Окно настройки картины без виджетов: выбранные в нём значения, переключатели, копирование и вставка конфига, сохранение в картину и текст состояния — одинаково для всех версий. Версия рисует виджеты и даёт доступ к тексту полей ввода (text, setText), переводам (tr) и материалам картины. Настройки применяются к картине при закрытии окна (save). */
public abstract class FrameEditor {

    public static final int TAB_SOURCE = 0, TAB_SIZE = 1, TAB_STYLE = 2, TAB_FRAME = 3, TAB_INFO = 4, TAB_COUNT = 5;

    /** Поля ввода по порядку: версия хранит их в массиве и переносит набранное, когда пересоздаёт виджеты. Путь и ссылки свои у каждого источника, видно только поле выбранного (pathField). */
    public static final int F_PATH = 0, F_URL = 1, F_FOLDER = 2, F_IMAGE = 3, F_INTERVAL = 4, F_WIDTH = 5, F_HEIGHT = 6,
        F_OFF_X = 7, F_OFF_Y = 8, F_PAD = 9, F_ZOOM = 10, F_IMAGE_X = 11, F_IMAGE_Y = 12, F_MOLDING = 13, F_TITLE = 14, F_DESC = 15,
        FIELD_COUNT = 16;
    private static final int[] FIELD_TAB = { TAB_SOURCE, TAB_SOURCE, TAB_SOURCE, TAB_SOURCE, TAB_SOURCE, TAB_SIZE, TAB_SIZE,
        TAB_SIZE, TAB_SIZE, TAB_SIZE, TAB_SIZE, TAB_SIZE, TAB_SIZE, TAB_FRAME, TAB_INFO, TAB_INFO };

    /** Последняя открытая вкладка — с неё окно откроется в следующий раз. */
    private static int lastTab = TAB_SOURCE;

    public final PictureSettings tile;
    /** Картина для пакетов (PictureTarget). */
    public final int target;
    public final UUID me;
    public final String myName;
    /** Версия игры: пишется в копируемый конфиг, по ней же переводятся названия предметов из вставленного. */
    private final String gameVersion;
    /** Чужая заприваченная картина: без вкладок, только заголовок и описание. */
    public final boolean readOnly;

    public int tab = lastTab;
    public int source, fitMode, frameShape, canvasKind;
    /** Что светится (PictureSettings.GLOW_*) — вкладка "Рамка", строка свечения. */
    public int glowPart;
    /** Резкие пиксели и палитра карт (галочки), число цветов, сила и вид дизеринга, пикселей на блок — вкладка "Стиль". */
    public boolean sharp, mapPalette;
    public int colors, dither, ditherMode, ppb;
    /** Галочки "px": размер, смещение холста, отступ, масштаб и сдвиг картинки в пикселях. */
    public boolean sizePx, posPx, padPx, zoomPx, imagePx;
    /** Замок закрыт (картина заприватится при закрытии окна). */
    public boolean locked;
    /** Подписать картину при закрытии окна (подпись ставит сервер, и только на картину без привата). */
    public boolean sign;
    /** Пометка NSFW (18+); применится при закрытии окна. */
    public boolean nsfw;
    /** Последний statusText — об ошибке. */
    public boolean statusError;

    protected FrameEditor(PictureSettings tile, int target, UUID me, String myName, String gameVersion) {
        this.tile = tile;
        this.target = target;
        this.me = me;
        this.myName = myName;
        this.gameVersion = gameVersion;
        this.readOnly = !tile.mayEdit(me);
        source = tile.source;
        fitMode = tile.fitMode;
        glowPart = tile.glowPart;
        sharp = tile.sharpPixels;
        mapPalette = tile.mapPalette;
        colors = tile.colors;
        dither = tile.dither;
        ditherMode = tile.ditherMode;
        ppb = tile.pixelsPerBlock;
        frameShape = tile.getFrameShape();
        canvasKind = tile.getCanvasKind();
        sizePx = tile.sizeInPixels;
        posPx = tile.offsetInPixels;
        padPx = tile.paddingInPixels;
        zoomPx = tile.zoomInPixels;
        imagePx = tile.imageInPixels;
        locked = tile.isLocked();
        nsfw = tile.nsfw;
    }

    // ---------- От версии ----------

    /** Текст поля ввода field (F_*). */
    protected abstract String text(int field);

    protected abstract void setText(int field, String value);

    /** Перевод по ключу словаря. */
    protected abstract String tr(String key, Object... args);

    /** Сколько картин настраивается: вся стопка картин-предметов или одна висящая картина. */
    protected abstract int count();

    /** Предмет в слоте материала index (по порядку PictureConfig.MATERIALS) или null — слот пуст. */
    protected abstract PictureConfig.Item material(int index);

    /** Есть ли в игре предмет с таким id. */
    protected abstract boolean itemExists(String id);

    /** Время мира в тиках (для слайдшоу). */
    protected abstract long worldTime();

    // ---------- Поля и вкладки ----------

    /** Что стоит в поле при открытии окна. */
    public String initialText(int field) {
        switch (field) {
            case F_PATH: return tile.localPath;
            case F_URL: return tile.yandexUrl;
            case F_FOLDER: return tile.folderPath;
            case F_IMAGE: return tile.imageUrl;
            case F_INTERVAL: return String.valueOf(tile.interval);
            case F_WIDTH: return Numbers.fmt(tile.width);
            case F_HEIGHT: return Numbers.fmt(tile.height);
            case F_OFF_X: return Numbers.fmt(tile.offsetX);
            case F_OFF_Y: return Numbers.fmt(tile.offsetY);
            case F_PAD: return Numbers.fmt(tile.padding);
            case F_ZOOM: return Numbers.fmt(tile.zoom);
            case F_IMAGE_X: return Numbers.fmt(tile.imageX);
            case F_IMAGE_Y: return Numbers.fmt(tile.imageY);
            case F_MOLDING: return tile.getFrameShape() == PictureSettings.FRAME_MOLDING ? String.valueOf(tile.moldingWidth) : "0";
            case F_TITLE: return tile.title;
            default: return tile.description;
        }
    }

    /** Числовое поле (набрать можно только число, стрелки и колёсико меняют его на шаг) или null. */
    public static Numbers.Field number(int field) {
        switch (field) {
            case F_INTERVAL: return Numbers.INTERVAL;
            case F_WIDTH: case F_HEIGHT: return Numbers.SIZE;
            case F_OFF_X: case F_OFF_Y: return Numbers.OFFSET;
            case F_PAD: return Numbers.PADDING;
            case F_ZOOM: return Numbers.ZOOM;
            case F_IMAGE_X: case F_IMAGE_Y: return Numbers.IMAGE;
            case F_MOLDING: return Numbers.MOLDING;
            default: return null;
        }
    }

    /** Подсказка числового поля вкладки размера — только стрелки направления (null — у поля её нет): ширина и высота, сдвиги вправо и вверх, отступ внутрь, масштаб наружу. */
    public static String fieldArrows(int field) {
        switch (field) {
            case F_WIDTH: return "\u2194";
            case F_HEIGHT: return "\u2195";
            case F_OFF_X: case F_IMAGE_X: return "\u25C0 \u25B6";
            case F_OFF_Y: case F_IMAGE_Y: return "\u25B2 \u25BC";
            case F_PAD: return "\u25B6 \u25C0";
            case F_ZOOM: return "\u25C0 \u25B6";
            default: return null;
        }
    }

    /** Ряды вкладки размера сверху вниз: подпись и короткое пояснение, как работает параметр (подсказка подписи). */
    public static final String[] SIZE_ROW_LABELS = { "minepapers.gui.size", "minepapers.gui.offset", "minepapers.gui.padding",
        "minepapers.gui.zoom", "minepapers.gui.image" };

    public static int fieldTab(int field) {
        return FIELD_TAB[field];
    }

    /** Поле пути или ссылки источника source. */
    public static int pathField(int source) {
        switch (source) {
            case PictureSettings.SOURCE_YANDEX: return F_URL;
            case PictureSettings.SOURCE_FOLDER: return F_FOLDER;
            case PictureSettings.SOURCE_URL: return F_IMAGE;
            default: return F_PATH;
        }
    }

    /** Поле видно и принимает ввод: окно не только для чтения, его вкладка открыта, а путь — только у выбранного источника. */
    public boolean fieldShown(int field) {
        if (readOnly || FIELD_TAB[field] != tab) return false;
        return field > F_IMAGE || field == pathField(source);
    }

    public void selectTab(int index) {
        tab = lastTab = index;
    }

    /** Шаг числа стрелкой или колёсиком (dir — 1 или -1): размер, отступ и масштаб — в единицах, выбранных галочкой "px". */
    public void step(int field, int dir) {
        boolean px = field == F_WIDTH || field == F_HEIGHT ? sizePx : field == F_PAD ? padPx : field == F_ZOOM ? zoomPx
            : (field == F_IMAGE_X || field == F_IMAGE_Y) && imagePx;
        setText(field, number(field).step(text(field), dir, px ? 16f : 1f));
        if (field == F_MOLDING) moldingChanged();
    }

    /** Путь или ссылка без переводов строк (их можно вставить через Ctrl+V). */
    public String line(int field) {
        return text(field).replace("\r", "").replace("\n", "").trim();
    }

    // ---------- Переключатели ----------

    /** Галочки "px": значение пересчитывается, чтобы картинка не "прыгала". */
    public void toggleSizePx() {
        sizePx = !sizePx;
        convert(F_WIDTH, sizePx);
        convert(F_HEIGHT, sizePx);
    }

    public void togglePosPx() {
        posPx = !posPx;
        convert(F_OFF_X, posPx);
        convert(F_OFF_Y, posPx);
    }

    public void togglePadPx() {
        padPx = !padPx;
        convert(F_PAD, padPx);
    }

    public void toggleZoomPx() {
        zoomPx = !zoomPx;
        convert(F_ZOOM, zoomPx);
    }

    public void toggleImagePx() {
        imagePx = !imagePx;
        convert(F_IMAGE_X, imagePx);
        convert(F_IMAGE_Y, imagePx);
    }

    private void convert(int field, boolean toPixels) {
        Float v = Numbers.parse(text(field));
        if (v != null) setText(field, Numbers.fmt(toPixels ? v * 16f : v / 16f));
    }

    /** Вид, выбранный в окне. */
    public int look() {
        return PictureSettings.look(frameShape, canvasKind);
    }

    /** "Нет" или "обшивка" — багета больше нет. */
    public void chooseFrame(int value) {
        frameShape = value;
        setText(F_MOLDING, "0");
    }

    public void chooseCanvas(int value) {
        canvasKind = value;
    }

    /** Ширина багета больше нуля — рамка становится багетом, ноль — рамки нет (если был багет). */
    public void moldingChanged() {
        if (moldingWidth() > 0) {
            frameShape = PictureSettings.FRAME_MOLDING;
        } else if (frameShape == PictureSettings.FRAME_MOLDING) {
            frameShape = PictureSettings.FRAME_NONE;
        }
    }

    public int moldingWidth() {
        Float v = Numbers.parse(text(F_MOLDING));
        return v == null ? 0 : Math.round(v);
    }

    /** Нужен ли выбранному в окне виду материал index (рамка, холст, свечение). Ненужные блоки вернутся игроку при закрытии окна. */
    public boolean materialUsed(int index) {
        if (index == 0) return frameShape != PictureSettings.FRAME_NONE;
        if (index == 1) return canvasKind == PictureSettings.CANVAS_BLOCK;
        return true;
    }

    private boolean hasUnusedMaterials() {
        for (int i = 0; i < PictureConfig.MATERIALS.length; i++) {
            if (material(i) != null && !materialUsed(i)) return true;
        }
        return false;
    }

    // ---------- Замок и подпись ----------

    /** Владелец закрытого замка (сам игрок, если замок закроется при закрытии окна); null — замок открыт. */
    public UUID lockOwner() {
        return !locked ? null : tile.owner != null ? tile.owner : me;
    }

    public String lockOwnerName() {
        return !locked ? "" : tile.owner != null ? tile.ownerName : myName;
    }

    /** Кто подписал картину или подпишет её при закрытии окна; null — никто. */
    public UUID signer() {
        return tile.signer != null ? tile.signer : sign ? me : null;
    }

    public String signerName() {
        return tile.signer != null ? tile.signerName : myName;
    }

    /** Подписать можно только одну картину без привата и только один раз; подтверждённую подпись уже не отменить (поставится при закрытии окна, даже если закрыть и замок). */
    public boolean canSign() {
        return !readOnly && !tile.isSigned() && !sign && !locked && count() == 1;
    }

    // ---------- Источник ----------

    public String currentKey() {
        return PictureSettings.makeSourceKey(source, line(F_PATH), line(F_URL), line(F_FOLDER), line(F_IMAGE));
    }

    /** Что на самом деле показывает рамка: если источник не меняли и картинки уже на сервере — их. */
    public String shownKey() {
        String key = currentKey();
        return key != null && key.equals(tile.getOriginKey()) && tile.hasSharedFiles() ? tile.getSourceKey() : key;
    }

    /** Следующая (dir = 1) или предыдущая картинка или папка из config/minepapers. */
    public void cycleLocal(int dir) {
        List<String> entries = PictureManager.listLocalEntries();
        if (entries.isEmpty()) return;
        int index = entries.indexOf(line(F_PATH));
        if (index < 0) {
            index = dir > 0 ? 0 : entries.size() - 1;
        } else {
            index = (index + dir + entries.size()) % entries.size();
        }
        setText(F_PATH, entries.get(index));
    }

    /** Вставка (Ctrl+V) в поле пути field: путь или ссылка другого источника в это поле не попадает — источник переключается, и текст ложится в поле своего источника вместо того, что там было. Возвращает это поле (версия переводит на него фокус) или -1 — вставка обычная. */
    public int pastePath(int field, String clipboard) {
        String value = clipboard.replace("\r", "").replace("\n", "").trim();
        int detected = detectSource(value);
        if (detected < 0 || pathField(detected) == field) return -1;
        source = detected;
        setText(pathField(detected), value);
        return pathField(detected);
    }

    /** Источник, которому подходит путь или ссылка (PictureSettings.SOURCE_*), или -1 — не понять. Ссылка на папку поддерживаемого сервиса — онлайн-папка, другая http-ссылка — ссылка на картинку, абсолютный путь — папка на компьютере, имя из config/minepapers — оттуда. */
    static int detectSource(String value) {
        if (value.isEmpty()) return -1;
        String lower = value.toLowerCase(Locale.ROOT);
        if (PictureManager.isYandexLink(value) || PictureManager.googleDriveId(value) != null) return PictureSettings.SOURCE_YANDEX;
        if (lower.startsWith("http://") || lower.startsWith("https://")) return PictureSettings.SOURCE_URL;
        // Пути, скопированные из проводника "как путь", приходят в кавычках.
        String path = value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
        if (path.matches("^[A-Za-z]:[\\\\/].*") || path.startsWith("\\\\") || path.startsWith("/")) return PictureSettings.SOURCE_FOLDER;
        return new File(ClientHost.get().picturesDir(), path).exists() ? PictureSettings.SOURCE_LOCAL : -1;
    }

    /** Загрузить картинки заново, а если источник тот же, что у картины, — выложить на сервер то, что в нём появилось. */
    public void reload() {
        String key = currentKey();
        if (key == null) return;
        PictureManager.reload(key);
        PictureManager.get(key);
        if (key.equals(tile.getOriginKey())) {
            if (tile.hasSharedFiles()) PictureManager.reload(tile.getSourceKey());
            ImageSync.offer(target, tile);
        }
    }

    /** Состояние источника под полем пути: сколько картинок, загрузка, выкладывание на сервер или ошибка (тогда statusError). */
    public String statusText() {
        statusError = false;
        String key = currentKey();
        if (key == null) return tr("minepapers.gui.status.none");
        int[] job = ImageSync.jobStatus(key);
        if (job != null) {
            String stage = job[0] == ImageSync.STAGE_PREPARING ? "preparing" : "uploading";
            return tr("minepapers.gui.status." + stage, job[1], job[2]);
        }
        key = shownKey();
        PictureManager.Source s = PictureManager.peek(key);
        if (s == null) return tr("minepapers.gui.status.unknown");
        if (s.isLoading()) return tr("minepapers.gui.status.loading");
        if (s.getErrorKey() != null || s.getErrorDetail() != null) {
            statusError = true;
            String detail = s.getErrorKey() != null ? tr(s.getErrorKey()) : s.getErrorDetail();
            return tr("minepapers.gui.status.error", detail);
        }
        return tr(key.startsWith("shared:") ? "minepapers.gui.status.shared" : "minepapers.gui.status.count", s.count());
    }

    // ---------- Копировать и вставить ----------

    /** Все настройки окна (как они введены сейчас) и материалы картины — конфигом для буфера обмена. */
    public PictureConfig copyConfig() {
        PictureConfig c = new PictureConfig();
        c.minecraft = gameVersion;
        c.title = text(F_TITLE).trim();
        c.description = text(F_DESC).trim();
        c.nsfw = nsfw;
        c.source = source;
        c.path = line(pathField(source));
        Float interval = Numbers.parse(text(F_INTERVAL));
        if (interval != null) c.interval = Math.round(interval);
        c.width = Numbers.parse(text(F_WIDTH));
        c.height = Numbers.parse(text(F_HEIGHT));
        c.sizePixels = sizePx;
        c.offsetRight = Numbers.parse(text(F_OFF_X));
        c.offsetUp = Numbers.parse(text(F_OFF_Y));
        c.offsetPixels = posPx;
        c.padding = Numbers.parse(text(F_PAD));
        c.paddingPixels = padPx;
        c.zoom = Numbers.parse(text(F_ZOOM));
        c.zoomPixels = zoomPx;
        c.imageRight = Numbers.parse(text(F_IMAGE_X));
        c.imageUp = Numbers.parse(text(F_IMAGE_Y));
        c.imagePixels = imagePx;
        c.fit = fitMode;
        c.glowPart = glowPart;
        c.look = PictureSettings.classicLook(look());
        c.frame = frameShape;
        if (frameShape == PictureSettings.FRAME_MOLDING) c.moldingWidth = moldingWidth();
        c.canvas = canvasKind;
        c.sharpPixels = sharp;
        c.mapPalette = mapPalette;
        c.colors = colors;
        c.dither = dither;
        c.ditherMode = ditherMode;
        c.style = PictureSettings.legacyStyle(mapPalette, dither);
        c.pixelsPerBlock = ppb;
        for (int i = 0; i < PictureConfig.MATERIALS.length; i++) {
            c.hasMaterial[i] = true;
            c.materials[i] = material(i);
        }
        return c;
    }

    /** Конфиг из буфера обмена — в окно (применится при закрытии, как и введённое руками). Возвращает его с предметами материалов в названиях этой версии игры — их сервер возьмёт из инвентаря; null — это не конфиг. */
    public PictureConfig paste(String clipboard) {
        PictureConfig c;
        try {
            c = PictureConfig.fromJson(clipboard);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (c.title != null) setText(F_TITLE, c.title);
        if (c.description != null) setText(F_DESC, c.description);
        if (c.nsfw != null) nsfw = c.nsfw;
        if (c.source != null) source = c.source;
        if (c.path != null) setText(pathField(source), c.path);
        if (c.interval != null) setText(F_INTERVAL, String.valueOf(c.interval));
        if (c.sizePixels != null) sizePx = c.sizePixels;
        if (c.width != null) setText(F_WIDTH, Numbers.fmt(c.width));
        if (c.height != null) setText(F_HEIGHT, Numbers.fmt(c.height));
        if (c.offsetPixels != null) posPx = c.offsetPixels;
        if (c.offsetRight != null) setText(F_OFF_X, Numbers.fmt(c.offsetRight));
        if (c.offsetUp != null) setText(F_OFF_Y, Numbers.fmt(c.offsetUp));
        if (c.paddingPixels != null) padPx = c.paddingPixels;
        if (c.padding != null) setText(F_PAD, Numbers.fmt(c.padding));
        if (c.zoomPixels != null) zoomPx = c.zoomPixels;
        if (c.zoom != null) setText(F_ZOOM, Numbers.fmt(c.zoom));
        if (c.imagePixels != null) imagePx = c.imagePixels;
        if (c.imageRight != null) setText(F_IMAGE_X, Numbers.fmt(c.imageRight));
        if (c.imageUp != null) setText(F_IMAGE_Y, Numbers.fmt(c.imageUp));
        if (c.fit != null) fitMode = c.fit;
        if (c.glowPart != null) glowPart = c.glowPart;
        if (c.look != null) {
            frameShape = PictureSettings.frameShapeOf(c.look);
            canvasKind = PictureSettings.canvasKindOf(c.look);
        }
        if (c.frame != null) frameShape = c.frame;
        if (c.canvas != null) canvasKind = c.canvas;
        // Багет без ширины (конфиг прежних версий) — обычной ширины.
        setText(F_MOLDING, frameShape != PictureSettings.FRAME_MOLDING ? "0" : String.valueOf(
            c.moldingWidth != null && c.moldingWidth > 0 ? c.moldingWidth : PictureSettings.DEFAULT_MOLDING));
        if (c.style != null) {
            mapPalette = sharp = c.style != PictureSettings.STYLE_ORIGINAL;
            dither = PictureSettings.legacyDither(c.style);
        }
        if (c.sharpPixels != null) sharp = c.sharpPixels;
        if (c.mapPalette != null) mapPalette = c.mapPalette;
        if (c.colors != null) colors = Math.max(PictureSettings.MIN_COLORS, Math.min(PictureSettings.MAX_COLORS, c.colors));
        if (c.dither != null) dither = Math.max(0, Math.min(PictureSettings.MAX_DITHER, c.dither));
        if (c.ditherMode != null) ditherMode = c.ditherMode;
        if (c.pixelsPerBlock != null) ppb = Math.max(PictureSettings.MIN_PPB, Math.min(PictureSettings.MAX_PPB, c.pixelsPerBlock));
        // Конфиг из другой эпохи: предметы там названы иначе (grass вместо grass_block, wool с номером цвета) — переводим.
        boolean flattened = PictureConfig.isFlattened(gameVersion);
        for (int i = 0; i < c.materials.length; i++) {
            c.materials[i] = c.translate(c.materials[i], flattened, this::itemExists);
        }
        return c;
    }

    // ---------- Сохранение ----------

    /** Записать введённое в окне в картину (через те же ограничения, что и на сервере). true — изменилось что-то, что надо отправить серверу (или ему надо вернуть игроку ненужные виду блоки материалов: materialsEditable — их можно менять). */
    public boolean save(boolean materialsEditable) {
        MapTag before = MapTag.of(tile);

        tile.title = text(F_TITLE).trim();
        tile.description = text(F_DESC).trim();
        tile.nsfw = nsfw;
        // Владельца решает сервер (PacketSaveFrame); здесь — только просьба закрыть или открыть замок.
        if (locked && tile.owner == null) {
            tile.owner = me;
            tile.ownerName = myName;
        } else if (!locked) {
            tile.owner = null;
            tile.ownerName = "";
        }
        // Подпись тоже только просьба: её ставит сервер.
        if (sign && tile.signer == null) {
            tile.signer = me;
            tile.signerName = myName;
        }
        tile.source = source;
        tile.localPath = line(F_PATH);
        tile.yandexUrl = line(F_URL);
        tile.folderPath = line(F_FOLDER);
        tile.imageUrl = line(F_IMAGE);
        Float interval = Numbers.parse(text(F_INTERVAL));
        if (interval != null) tile.interval = Math.round(interval);
        tile.sizeInPixels = sizePx;
        float[] size = resolveSize(Numbers.parse(text(F_WIDTH)), Numbers.parse(text(F_HEIGHT)));
        tile.width = size[0];
        tile.height = size[1];
        tile.offsetInPixels = posPx;
        tile.offsetX = Numbers.orDefault(Numbers.parse(text(F_OFF_X)), tile.offsetX);
        tile.offsetY = Numbers.orDefault(Numbers.parse(text(F_OFF_Y)), tile.offsetY);
        tile.paddingInPixels = padPx;
        tile.padding = Numbers.orDefault(Numbers.parse(text(F_PAD)), tile.padding);
        tile.zoomInPixels = zoomPx;
        tile.zoom = Numbers.orDefault(Numbers.parse(text(F_ZOOM)), tile.zoom);
        tile.imageInPixels = imagePx;
        tile.imageX = Numbers.orDefault(Numbers.parse(text(F_IMAGE_X)), tile.imageX);
        tile.imageY = Numbers.orDefault(Numbers.parse(text(F_IMAGE_Y)), tile.imageY);
        tile.fitMode = fitMode;
        tile.glowPart = glowPart;
        tile.sharpPixels = sharp;
        tile.mapPalette = mapPalette;
        tile.colors = colors;
        tile.dither = dither;
        tile.ditherMode = ditherMode;
        tile.mode = look();
        if (frameShape == PictureSettings.FRAME_MOLDING) tile.moldingWidth = moldingWidth();
        tile.pixelsPerBlock = ppb;

        // Прогоняем через те же ограничения, что и сервер.
        MapTag after = MapTag.of(tile);
        tile.readSettings(after);
        after = MapTag.of(tile);
        // Окно открыли и закрыли — серверу отправлять нечего, если только не надо вернуть ненужные виду блоки.
        return !after.equals(before) || materialsEditable && hasUnusedMaterials();
    }

    /** Хитбокс должен быть известен серверу, поэтому "0 = авто" превращаем в конкретный размер по пропорциям текущей картинки (если она ещё не загружена — квадрат). Округляем до пикселя. */
    private float[] resolveSize(Float w, Float h) {
        float unit = sizePx ? 16f : 1f;
        boolean wAuto = w == null || w <= 0, hAuto = h == null || h <= 0;
        if (wAuto || hAuto) {
            double aspect = currentAspect();
            if (wAuto && hAuto) {
                w = unit;
                h = (float) (unit * aspect);
            } else if (hAuto) {
                h = (float) (w * aspect);
            } else {
                w = (float) (h / aspect);
            }
        }
        return new float[] { Numbers.snap(w, sizePx), Numbers.snap(h, sizePx) };
    }

    /** Высота / ширина картинки, которая сейчас показывается в рамке (1, если неизвестно). */
    private double currentAspect() {
        PictureManager.Source s = PictureManager.peek(shownKey());
        if (s != null) {
            PictureManager.Picture p = s.slide(worldTime(), tile.interval, 0, tile.getSlideSeed());
            if (p != null && p.request() && p.getOrigWidth() > 0) {
                return (double) p.getOrigHeight() / p.getOrigWidth();
            }
        }
        return 1.0;
    }
}
