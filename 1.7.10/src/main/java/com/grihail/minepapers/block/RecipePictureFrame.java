package com.grihail.minepapers.block;

import com.grihail.minepapers.MinePapers;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

/**
 * Рецепты без формы, как копирование карт:
 * одна настроенная рамка — сброс до пустой; настроенная + пустые — столько же копий настроенной.
 */
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
                configured = stack;
            } else {
                empty[0]++;
            }
        }
        return configured;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        return find(inv, new int[1]) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        int[] empty = new int[1];
        ItemStack configured = find(inv, empty);
        if (configured == null) return null;
        if (empty[0] == 0) return new ItemStack(MinePapers.frame);
        ItemStack copy = configured.copy();
        copy.stackSize = empty[0] + 1;
        return copy;
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(MinePapers.frame);
    }
}
