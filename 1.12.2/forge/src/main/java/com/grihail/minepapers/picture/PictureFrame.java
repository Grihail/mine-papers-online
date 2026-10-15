package com.grihail.minepapers.picture;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** Настройки и место картины (общая часть — PictureSettings) с материалами. Живёт внутри EntityPicture, а также служит "призраком" будущей картины (подсказка перед установкой, проверки) и разбором настроек из предмета. Картинки грузятся только на клиенте. */
public class PictureFrame extends PictureSettings {

    /** Материалы (слоты на вкладке "Рамка"): из текстуры лежащего в слоте блока сделаны рамка и холст; светящийся блок в слоте свечения делает картину источником света его силы. */
    public final Material border = new Material("Border", false);
    public final Material canvas = new Material("Canvas", false);
    public final Material glow = new Material("Glow", true);

    /** Сила света, который картина даёт миру (0..15), — как у блока в слоте свечения. */
    public int glowLevel() {
        return lightOf(glow.stack);
    }

    /** Насколько ярко светится сама картина: как блок в слоте свечения, а с пылью светокамня — в полную силу, но без света вокруг. */
    public int selfGlow() {
        return isGlowDust(glow.stack) ? 15 : glowLevel();
    }

    /** Пыль светокамня: светится только сама картина. */
    public static boolean isGlowDust(ItemStack stack) {
        return stack.getItem() == Items.GLOWSTONE_DUST;
    }

    /** Сколько света даёт блок, из которого сделан предмет (0 — не блок или не светится). Без списков: самое яркое из состояний блока, а у ламп, печей и руды — у их включённого варианта (в 1.12.2 это отдельные блоки без своих предметов). */
    @SuppressWarnings("deprecation")
    public static int lightOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == Blocks.REDSTONE_LAMP) block = Blocks.LIT_REDSTONE_LAMP;
        else if (block == Blocks.FURNACE) block = Blocks.LIT_FURNACE;
        else if (block == Blocks.REDSTONE_ORE) block = Blocks.LIT_REDSTONE_ORE;
        if (block == Blocks.AIR) return 0;
        int light = 0;
        for (IBlockState state : block.getBlockState().getValidStates()) light = Math.max(light, state.getLightValue());
        return Math.min(15, light);
    }

    public Material[] materials() {
        return new Material[] { border, canvas, glow };
    }

    /** Материал рамки или холста: блок, лежащий в картине (его текстура и есть материал). В настройках — под ключами key ("" — обычный), key + "Meta" и key + "Item" (сам предмет). */
    public static final class Material {

        public final String key;
        /** Слот свечения: принимает только светящиеся блоки. */
        public final boolean light;
        /** Лежащий в картине предмет; пустой — обычный материал. */
        public ItemStack stack = ItemStack.EMPTY;
        /** Текстура: имя предмета в реестре ("" — обычная) и метаданные. Берутся из stack; без него остаются только у картин, где текстуру рамки выбирали без предмета. */
        public String item = "";
        public int meta = 0;

        Material(String key, boolean light) {
            this.key = key;
            this.light = light;
        }

        /** Можно ли положить предмет в этот слот. */
        public boolean accepts(ItemStack s) {
            return !light || lightOf(s) > 0 || isGlowDust(s);
        }

        /** Положить предмет в слот (пустой — вынуть): материал берёт его текстуру. */
        public void set(ItemStack s) {
            stack = s.isEmpty() ? ItemStack.EMPTY : s.copy();
            if (!stack.isEmpty()) stack.setCount(1);
            ResourceLocation name = stack.isEmpty() ? null : stack.getItem().getRegistryName();
            item = name != null ? name.toString() : "";
            meta = stack.isEmpty() ? 0 : stack.getMetadata();
        }

        public String stackTag() {
            return key + "Item";
        }

        void write(NBTTagCompound tag) {
            tag.setString(key, item);
            tag.setShort(key + "Meta", (short) meta);
            if (!stack.isEmpty()) tag.setTag(stackTag(), stack.writeToNBT(new NBTTagCompound()));
        }

        /** Только этот материал из настроек tag. */
        public void read(NBTTagCompound tag) {
            item = tag.getString(key);
            meta = tag.getShort(key + "Meta");
            if (tag.hasKey(stackTag())) {
                set(new ItemStack(tag.getCompoundTag(stackTag())));
            } else {
                stack = ItemStack.EMPTY;
            }
        }

        /** Убрать материал из настроек (копии картины его не получают — предмет один). */
        public void remove(NBTTagCompound tag) {
            tag.removeTag(key);
            tag.removeTag(key + "Meta");
            tag.removeTag(stackTag());
        }
    }

    public void writePlacement(NBTTagCompound tag) {
        writePlacement(new NbtTag(tag));
    }

    public void readPlacement(NBTTagCompound tag) {
        readPlacement(new NbtTag(tag));
    }

    public void writeSettings(NBTTagCompound tag) {
        writeSettings(new NbtTag(tag));
        for (Material m : materials()) m.write(tag);
    }

    /** Читает настройки и приводит их к допустимым значениям (данные могут прийти от клиента). */
    public void readSettings(NBTTagCompound tag) {
        readSettings(new NbtTag(tag));
        for (Material m : materials()) m.read(tag);
    }

    /** true, если настройки отличаются от новой рамки (тогда их стоит сохранить в выпавшем предмете). */
    public boolean isCustomized() {
        NBTTagCompound mine = new NBTTagCompound(), def = new NBTTagCompound();
        writeSettings(mine);
        new PictureFrame().writeSettings(def);
        return !mine.equals(def);
    }
}
