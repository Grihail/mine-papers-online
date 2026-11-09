package com.grihail.minepapers;

import java.util.List;
import java.util.UUID;

import com.grihail.minepapers.picture.NbtTag;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Мелочи, которые в разных версиях игры называются по-разному: чтобы условия версий были в одном месте, а не в каждом вызове. */
public final class Compat {

    private Compat() {}

    /** Выбросить предмет из игрока под ноги (как при нехватке места в инвентаре). */
    public static void drop(Player player, ItemStack stack) {
        //? if >=26.3 {
        /*player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.drop(stack, false);
        //?}
    }

    /** Предмет по id ("" или неизвестный - воздух). */
    public static Item item(ResourceLocation id) {
        //? if >=26.1 {
        /*return BuiltInRegistries.ITEM.getValue(id);
        *///?} else {
        return BuiltInRegistries.ITEM.get(id);
        //?}
    }

    /** Выбранная ячейка хотбара. */
    public static int selectedSlot(Inventory inventory) {
        //? if >=26.1 {
        /*return inventory.getSelectedSlot();
        *///?} else {
        return inventory.selected;
        //?}
    }

    /** Основные ячейки инвентаря (хотбар и рюкзак, без брони и второй руки). */
    public static List<ItemStack> mainItems(Inventory inventory) {
        //? if >=26.1 {
        /*return inventory.getNonEquipmentItems();
        *///?} else {
        return inventory.items;
        //?}
    }

    /** UUID из тега (null - нет или испорчен). */
    public static UUID uuid(CompoundTag tag, String key) {
        return tag == null ? null : new NbtTag(tag).getUuid(key);
    }
}
