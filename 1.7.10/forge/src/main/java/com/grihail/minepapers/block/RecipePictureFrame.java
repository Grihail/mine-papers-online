package com.grihail.minepapers.block;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.PictureFrame;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Рецепты без формы, как копирование карт (заприваченные картины в них не участвуют): одна настроенная рамка — разбор (если в ней лежат материалы — сначала только они: картина остаётся с настройками в виде граффити; иначе сброс до пустой); настроенная + пустые — столько же копий настроенной без материалов. Блоки из слотов материалов возвращаются игроку (см. Events). */
public class RecipePictureFrame implements IRecipe {

    private static boolean isConfigured(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(BlockPictureFrame.ITEM_TAG);
    }

    /** Настроенная рамка из сетки или null, если сетка не подходит под рецепт. Пустые пишутся в empty[0]. */
    private static ItemStack find(InventoryCrafting inv, int[] empty) {
        Item frame = Item.getItemFromBlock(MinePapers.frame);
        ItemStack configured = null;
        empty[0] = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack == null) continue;
            if (stack.getItem() != frame) return null;
            if (isConfigured(stack)) {
                if (configured != null) return null; // две настроенные — непонятно, чью копировать
                if (ItemPictureFrame.isLocked(stack)) return null; // заприваченную не разбирают и не копируют
                configured = stack;
            } else {
                empty[0]++;
            }
        }
        // Подписанную не копируют: подпись уникальна.
        if (configured != null && empty[0] > 0 && ItemPictureFrame.isSigned(configured)) return null;
        return configured;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        return getCraftingResult(inv) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        int[] empty = new int[1];
        ItemStack configured = find(inv, empty);
        if (configured == null) return null;
        ItemStack copy = configured.copy();
        copy.stackSize = empty[0] + 1;
        NBTTagCompound settings = copy.getTagCompound().getCompoundTag(BlockPictureFrame.ITEM_TAG);
        // Блоки из слотов материалов в единственном числе: они вернутся игроку (Events), картины остаются без них.
        boolean hadMaterials = stripMaterials(settings);
        if (empty[0] == 0) {
            // Разбор: сначала только вынимаем материалы — картина остаётся с настройками, но без рамки и холста (граффити). Повторный крафт сбрасывает её полностью.
            if (!hadMaterials) return ItemPictureFrame.isSigned(configured) ? null : new ItemStack(MinePapers.frame); // подпись навсегда
            settings.setInteger("Mode", PictureFrame.MODE_DECAL);
        }
        return copy;
    }

    /** Убрать из настроек предметы материалов; true — что-то было. */
    private static boolean stripMaterials(NBTTagCompound settings) {
        boolean had = false;
        for (PictureFrame.Material m : new PictureFrame().materials()) {
            if (settings.hasKey(m.stackTag())) {
                m.remove(settings);
                had = true;
            }
        }
        return had;
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(MinePapers.frame);
    }

    /** Картина ушла в крафт (сброс, копирование, чужой рецепт) — блоки из её слотов материалов возвращаются в инвентарь (нет места — выпадают рядом), а не пропадают и не размножаются. Событие приходит до того, как ингредиенты списаны: каждый крафт тратит по одной картине из ячейки. */
    public static class Events {

        @SubscribeEvent
        public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
            if (event.player.worldObj.isRemote) return;
            IInventory grid = event.craftMatrix;
            for (int i = 0; i < grid.getSizeInventory(); i++) {
                for (ItemStack stored : ItemPictureFrame.storedMaterials(grid.getStackInSlot(i))) {
                    if (!event.player.inventory.addItemStackToInventory(stored)) {
                        event.player.dropPlayerItemWithRandomChoice(stored, false);
                    }
                }
            }
        }
    }
}
