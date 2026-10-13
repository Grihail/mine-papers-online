package com.grihail.minepapers.picture;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Настройки и место картины (общая часть — PictureSettings) с материалами. Живёт внутри EntityPicture, а также служит "призраком" будущей картины (подсказка перед установкой, проверки) и разбором настроек из предмета. Картинки грузятся только на клиенте. */
public class PictureFrame extends PictureSettings {

    /** Материалы (слоты на вкладке "Рамка"): из текстуры лежащего в слоте блока сделаны рамка и холст; светящийся блок в слоте свечения делает картину источником света его силы. */
    public final Material border = new Material("Border", false);
    public final Material canvas = new Material("Canvas", false);
    public final Material glow = new Material("Glow", true);

    /** Сила света, который картина даёт миру (0..15), — как у блока в слоте свечения. */
    public int glowLevel() {
        return glow.stack != null ? lightOf(glow.stack) : 0;
    }

    /** Насколько ярко светится сама картина: как блок в слоте свечения, а с пылью светокамня — в полную силу, но без света вокруг. */
    public int selfGlow() {
        return isGlowDust(glow.stack) ? 15 : glowLevel();
    }

    /** Пыль светокамня: светится только сама картина. */
    public static boolean isGlowDust(ItemStack stack) {
        return stack != null && stack.getItem() == Items.glowstone_dust;
    }

    /** Сколько света даёт блок, из которого сделан предмет (0 — не блок или не светится). Без списков: берём у самого блока, а у ламп, печей и руды — у их включённого варианта (в 1.7.10 это отдельные блоки без своих предметов). */
    public static int lightOf(ItemStack stack) {
        if (stack == null) return 0;
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == Blocks.redstone_lamp) block = Blocks.lit_redstone_lamp;
        else if (block == Blocks.furnace) block = Blocks.lit_furnace;
        else if (block == Blocks.redstone_ore) block = Blocks.lit_redstone_ore;
        return block != null ? Math.max(0, Math.min(15, block.getLightValue())) : 0;
    }

    public Material[] materials() {
        return new Material[] { border, canvas, glow };
    }

    /** Материал рамки или холста: блок, лежащий в картине (его текстура и есть материал). В настройках — под ключами key ("" — обычный), key + "Meta" и key + "Item" (сам предмет). */
    public static final class Material {

        public final String key;
        /** Слот свечения: принимает только светящиеся блоки. */
        public final boolean light;
        /** Лежащий в картине предмет; null — обычный материал. */
        public ItemStack stack;
        /** Текстура: имя предмета в реестре ("" — обычная) и метаданные. Берутся из stack; без него остаются только у старых картин, где текстуру рамки выбирали без предмета. */
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

        /** Положить предмет в слот (null — вынуть): материал берёт его текстуру. */
        public void set(ItemStack s) {
            stack = s != null ? s.copy() : null;
            if (stack != null) stack.stackSize = 1;
            String name = stack != null ? Item.itemRegistry.getNameForObject(stack.getItem()) : null;
            item = name != null ? name : "";
            meta = stack != null ? stack.getItemDamage() : 0;
        }

        public String stackTag() {
            return key + "Item";
        }

        void write(NBTTagCompound tag) {
            tag.setString(key, item);
            tag.setShort(key + "Meta", (short) meta);
            if (stack != null) tag.setTag(stackTag(), stack.writeToNBT(new NBTTagCompound()));
        }

        /** Только этот материал из настроек tag. */
        public void read(NBTTagCompound tag) {
            item = limit(tag.getString(key));
            meta = range(tag.getShort(key + "Meta"), 0, Short.MAX_VALUE);
            if (tag.hasKey(stackTag())) {
                set(ItemStack.loadItemStackFromNBT(tag.getCompoundTag(stackTag())));
            } else {
                stack = null;
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
