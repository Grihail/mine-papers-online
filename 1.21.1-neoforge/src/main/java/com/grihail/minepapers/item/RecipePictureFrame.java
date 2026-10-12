package com.grihail.minepapers.item;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Рецепты без формы, как копирование карт (заприваченные картины в них не участвуют): одна настроенная рамка — разбор (если в ней лежат материалы — сначала только они: картина остаётся с настройками в виде граффити; иначе сброс до пустой); настроенная + пустые — столько же копий настроенной без материалов. Блоки из слотов материалов возвращаются игроку (см. Events). */
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
            CompoundTag settings = ItemPictureFrame.settings(stack);
            if (settings != null) {
                if (configured != null) return null; // две настроенные — непонятно, чью копировать
                if (ItemPictureFrame.isLocked(settings)) return null; // заприваченную не разбирают и не копируют
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
        ItemStack copy = configured.copyWithCount(empty[0] + 1);
        CompoundTag settings = ItemPictureFrame.settings(copy).copy();
        // Блоки из слотов материалов в единственном числе: они вернутся игроку (Events), картины остаются без них.
        boolean hadMaterials = false;
        for (PictureFrame.Material m : new PictureFrame().materials()) {
            if (settings.contains(m.stackTag())) {
                m.remove(settings);
                hadMaterials = true;
            }
        }
        if (empty[0] == 0) {
            // Разбор: сначала только вынимаем материалы — картина остаётся с настройками, но без рамки и холста (граффити). Повторный крафт сбрасывает её полностью.
            if (!hadMaterials) return new ItemStack(MinePapers.FRAME.get());
            settings.putByte("Mode", (byte) PictureFrame.MODE_DECAL);
        }
        copy.set(MinePapers.FRAME_SETTINGS.get(), settings);
        return copy;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MinePapers.FRAME_COPY.get();
    }

    /** Картина ушла в крафт (сброс, копирование, чужой рецепт) — блоки из её слотов материалов возвращаются в инвентарь (нет места — выпадают рядом), а не пропадают и не размножаются. Событие приходит до того, как ингредиенты списаны: каждый крафт тратит по одной картине из ячейки. */
    public static class Events {

        @SubscribeEvent
        public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
            Player player = event.getEntity();
            if (player.level().isClientSide) return;
            Container grid = event.getInventory();
            for (int i = 0; i < grid.getContainerSize(); i++) {
                for (ItemStack stored : ItemPictureFrame.storedMaterials(grid.getItem(i))) {
                    if (!player.getInventory().add(stored)) player.drop(stored, false);
                }
            }
        }
    }
}
