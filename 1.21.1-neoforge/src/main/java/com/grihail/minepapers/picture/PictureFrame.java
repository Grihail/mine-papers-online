package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Настройки и место картины (общая часть — PictureSettings) с материалами. Живёт внутри EntityPicture, а также служит "призраком" будущей картины (подсказка перед установкой, проверки) и разбором настроек из предмета. Картинки грузятся только на клиенте. */
public class PictureFrame extends PictureSettings {

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

    public void writePlacement(CompoundTag tag) {
        writePlacement(new NbtTag(tag));
    }

    public void readPlacement(CompoundTag tag) {
        readPlacement(new NbtTag(tag));
    }

    public void writeSettings(CompoundTag tag) {
        writeSettings(new NbtTag(tag));
        for (Material m : materials()) m.write(tag);
    }

    /** Читает настройки и приводит их к допустимым значениям (данные могут прийти от клиента). */
    public void readSettings(CompoundTag tag) {
        readSettings(new NbtTag(tag));
        for (Material m : materials()) m.read(tag);
    }

    /** true, если настройки отличаются от новой рамки (тогда их стоит сохранить в выпавшем предмете). */
    public boolean isCustomized() {
        CompoundTag mine = new CompoundTag(), def = new CompoundTag();
        writeSettings(mine);
        new PictureFrame().writeSettings(def);
        return !mine.equals(def);
    }
}
