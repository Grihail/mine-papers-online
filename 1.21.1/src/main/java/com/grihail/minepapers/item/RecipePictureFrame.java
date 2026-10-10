package com.grihail.minepapers.item;

import com.grihail.minepapers.MinePapers;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Рецепты без формы, как копирование карт: одна настроенная рамка — сброс до пустой; настроенная + пустые — столько же копий настроенной. */
public class RecipePictureFrame extends CustomRecipe {

    public RecipePictureFrame(CraftingBookCategory category) {
        super(category);
    }

    /** Настроенная рамка из сетки или null, если сетка не подходит под рецепт. Пустые пишутся в empty[0]. */
    private static ItemStack find(CraftingInput input, int[] empty) {
        ItemStack configured = null;
        empty[0] = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (!stack.is(MinePapers.FRAME.get())) return null;
            if (ItemPictureFrame.settings(stack) != null) {
                if (configured != null) return null; // две настроенные — непонятно, чью копировать
                configured = stack;
            } else {
                empty[0]++;
            }
        }
        return configured;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input, new int[1]) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int[] empty = new int[1];
        ItemStack configured = find(input, empty);
        if (configured == null) return ItemStack.EMPTY;
        if (empty[0] == 0) return new ItemStack(MinePapers.FRAME.get());
        return configured.copyWithCount(empty[0] + 1);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MinePapers.FRAME_COPY.get();
    }
}
