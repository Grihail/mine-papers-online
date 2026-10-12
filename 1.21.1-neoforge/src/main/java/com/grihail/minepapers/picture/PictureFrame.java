package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.SharedImage;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Настройки и место картины. Живёт внутри EntityPicture, а также служит "призраком" будущей картины (подсказка перед установкой, проверки) и разбором настроек из предмета. Место: (x, y, z) — клетка "рамки", от левого нижнего угла которой отсчитывается холст; facing — сторона (0..5), куда смотрит картина. Картинки грузятся только на клиенте. */
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
    public static final int MODE_PLATE = 3;     // пластина: сама картинка толщиной в пиксель, торцы — её крайние пиксели
    public static final int MODE_COUNT = 4;

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

    /** Материалы (слоты на вкладке "Рамка"): из текстуры лежащего в слоте блока сделаны рамка и холст; светящийся блок в слоте свечения делает картину источником света его силы. */
    public final Material border = new Material("Border", false);
    public final Material canvas = new Material("Canvas", false);
    public final Material glow = new Material("Glow", true);

    /** Сила света, который картина даёт миру (0..15), — как у блока в слоте свечения. */
    public int glowLevel() {
        return lightOf(glow.stack());
    }

    /** Насколько ярко светится сама картина: как блок в слоте свечения, а с пылью светокамня — в полную силу, но без света вокруг. */
    public int selfGlow() {
        return isGlowDust(glow.stack()) ? 15 : glowLevel();
    }

    /** Пыль светокамня: светится только сама картина. */
    public static boolean isGlowDust(ItemStack stack) {
        return stack.is(Items.GLOWSTONE_DUST);
    }

    /** Сколько света даёт блок, из которого сделан предмет (0 — не блок или не светится). Без списков: берём у самого блока — самое яркое из его состояний (лампа, свеча, печь светятся только включёнными). */
    public static int lightOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return 0;
        int light = 0;
        for (BlockState state : blockItem.getBlock().getStateDefinition().getPossibleStates()) {
            light = Math.max(light, state.getLightEmission());
        }
        return Math.min(15, light);
    }

    /** Клиентский кэш последнего нарисованного варианта картинки (см. client.RenderPicture). На сервере не используется. */
    public Object clientRenderState;

    public Material[] materials() {
        return new Material[] { border, canvas, glow };
    }

    /** Материал рамки или холста: блок, лежащий в картине (его текстура и есть материал). В настройках — под ключами key (id текстуры, "" — обычная) и key + "Item" (сам предмет как сохранённый ItemStack). Разобрать ItemStack можно только с реестрами мира, поэтому он — лениво, в stack(). */
    public static final class Material {

        public final String key;
        /** Слот свечения: принимает только светящиеся блоки. */
        public final boolean light;
        /** Текстура: id предмета в реестре ("" — обычная). Берётся из предмета в слоте; без него остаётся только у старых картин, где текстуру рамки выбирали без предмета. */
        public String item = "";
        private CompoundTag tag;
        private ItemStack cache;

        Material(String key, boolean light) {
            this.key = key;
            this.light = light;
        }

        /** Можно ли положить предмет в этот слот. */
        public boolean accepts(ItemStack stack) {
            return !light || lightOf(stack) > 0 || isGlowDust(stack);
        }

        public String stackTag() {
            return key + "Item";
        }

        /** Предмет в слоте (пустой — обычный материал или реестры мира ещё недоступны). */
        public ItemStack stack() {
            if (tag == null) return ItemStack.EMPTY;
            if (cache == null) {
                HolderLookup.Provider registries = registries();
                if (registries == null) return ItemStack.EMPTY;
                cache = ItemStack.parseOptional(registries, tag);
            }
            return cache;
        }

        /** Положить предмет в слот (пустой — вынуть): материал берёт его текстуру. */
        public void set(ItemStack stack) {
            HolderLookup.Provider registries = registries();
            if (stack.isEmpty() || registries == null) {
                tag = null;
                cache = null;
                item = "";
                return;
            }
            ItemStack one = stack.copyWithCount(1);
            tag = (CompoundTag) one.save(registries);
            cache = one;
            item = BuiltInRegistries.ITEM.getKey(one.getItem()).toString();
        }

        void write(CompoundTag settings) {
            settings.putString(key, item);
            if (tag != null) settings.put(stackTag(), tag.copy());
        }

        /** Только этот материал из настроек settings. */
        public void read(CompoundTag settings) {
            item = limit(settings.getString(key));
            cache = null;
            if (settings.contains(stackTag())) {
                tag = settings.getCompound(stackTag()).copy();
                item = limit(tag.getString("id")); // текстура известна и без реестров
            } else {
                tag = null;
            }
        }

        /** Убрать материал из настроек (копии картины его не получают — предмет один). */
        public void remove(CompoundTag settings) {
            settings.remove(key);
            settings.remove(stackTag());
        }
    }

    /** Реестры мира: на сервере — его, на клиенте — текущего мира (null, если мира нет). */
    private static HolderLookup.Provider registries() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.registryAccess() : MinePapers.proxy.clientRegistries();
    }

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

    public void writePlacement(CompoundTag tag) {
        tag.putInt("FX", x);
        tag.putInt("FY", y);
        tag.putInt("FZ", z);
        tag.putByte("Facing", (byte) getFacing());
        tag.putByte("Rot", (byte) getRotation());
        tag.putFloat("Surface", surface);
    }

    public void readPlacement(CompoundTag tag) {
        x = tag.getInt("FX");
        y = tag.getInt("FY");
        z = tag.getInt("FZ");
        facing = tag.getByte("Facing");
        facing = getFacing();
        rotation = tag.getByte("Rot") & 3;
        float s = tag.getFloat("Surface");
        surface = s >= -1f && s <= 0f ? s : 0f;
    }

    public void writeSettings(CompoundTag tag) {
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
        tag.putByte("Fit", (byte) fitMode);
        tag.putShort("PPB", (short) pixelsPerBlock);
        tag.putByte("Style", (byte) style);
        tag.putByte("Mode", (byte) mode);
        for (Material m : materials()) m.write(tag);
        if (files.length > 0) tag.putByteArray("Files", files);
    }

    /** Читает настройки и приводит их к допустимым значениям (данные могут прийти от клиента). */
    public void readSettings(CompoundTag tag) {
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

        fitMode = tag.contains("Fit") ? range(tag.getByte("Fit"), 0, FIT_COUNT - 1) : FIT_COVER;
        pixelsPerBlock = tag.contains("PPB") ? range(tag.getShort("PPB"), MIN_PPB, MAX_PPB) : 128;
        style = tag.contains("Style") ? range(tag.getByte("Style"), 0, STYLE_COUNT - 1) : STYLE_MAP_SOFT_DITHER;
        mode = range(tag.getByte("Mode"), 0, MODE_COUNT - 1);
        for (Material m : materials()) m.read(tag);
        byte[] f = tag.getByteArray("Files");
        files = f.length % SharedImage.HASH_BYTES == 0 && f.length <= MAX_FILES * SharedImage.HASH_BYTES ? f : NO_FILES;
    }

    /** true, если настройки отличаются от новой рамки (тогда их стоит сохранить в выпавшем предмете). */
    public boolean isCustomized() {
        CompoundTag mine = new CompoundTag(), def = new CompoundTag();
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
