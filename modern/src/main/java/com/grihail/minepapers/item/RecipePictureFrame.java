package com.grihail.minepapers.item;

import com.grihail.minepapers.Compat;
import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
//? if >=1.20.5 {
import net.minecraft.world.item.crafting.CraftingInput;
//?}
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Рецепты без формы, как копирование карт (заприваченные и подписанные картины не копируются, а разбираются, только если это разрешено в config/minepapers.json): одна настроенная рамка — разбор (если в ней лежат материалы — сначала только они: картина остаётся с настройками в виде граффити; иначе сброс до пустой); настроенная + пустые — столько же копий настроенной без материалов. Блоки из слотов материалов возвращаются игроку (см. onCrafted). */
public class RecipePictureFrame extends CustomRecipe {

    //? if >=26.1 {
    /*// С 26.1 у особых рецептов нет категории в конструкторе, а сериализатор - запись из кодеков (без полей: рецепт один).
    public static final RecipePictureFrame INSTANCE = new RecipePictureFrame();
    public static final RecipeSerializer<RecipePictureFrame> SERIALIZER = new RecipeSerializer<>(
        com.mojang.serialization.MapCodec.unit(INSTANCE), net.minecraft.network.codec.StreamCodec.unit(INSTANCE));

    public RecipePictureFrame() {}
    *///?} else if >=1.20.5 {
    public RecipePictureFrame(CraftingBookCategory category) {
        super(category);
    }
    //?} else {
    /*public RecipePictureFrame(net.minecraft.resources.ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }
    *///?}

    /** Настроенная рамка из сетки или null, если сетка не подходит под рецепт. Пустые пишутся в empty[0]. */
    //? if >=1.20.5 {
    private static ItemStack find(CraftingInput input, int[] empty) {
    //?} else {
    /*private static ItemStack find(net.minecraft.world.inventory.CraftingContainer input, int[] empty) {
    *///?}
        ItemStack configured = null;
        empty[0] = 0;
        for (int i = 0; i < size(input); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (!stack.is(MinePapers.FRAME.get())) return null;
            CompoundTag settings = ItemPictureFrame.settings(stack);
            if (settings != null) {
                if (configured != null) return null; // две настроенные — непонятно, чью копировать
                configured = stack;
            } else {
                empty[0]++;
            }
        }
        // Заприваченную и подписанную не копируют никогда, а разбирают — только если это разрешено (ModConfig.mayCraft).
        if (configured != null) {
            CompoundTag settings = ItemPictureFrame.settings(configured);
            if (!ModConfig.mayCraft(ItemPictureFrame.isLocked(settings), ItemPictureFrame.isSigned(settings), empty[0] > 0)) return null;
        }
        return configured;
    }

    /** Сетка крафта: с 1.20.5 — CraftingInput, раньше — сам контейнер верстака. */
    //? if >=1.20.5 {
    private static int size(CraftingInput input) {
        return input.size();
    }
    //?} else {
    /*private static int size(net.minecraft.world.inventory.CraftingContainer input) {
        return input.getContainerSize();
    }
    *///?}

    //? if >=26.1 {
    /*@Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
    *///?} else if >=1.20.5 {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
    //?} else {
    /*@Override
    public boolean matches(net.minecraft.world.inventory.CraftingContainer input, Level level) {
        return !assemble(input, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer input, net.minecraft.core.RegistryAccess registries) {
    *///?}
        int[] empty = new int[1];
        ItemStack configured = find(input, empty);
        if (configured == null) return ItemStack.EMPTY;
        ItemStack copy = configured.copyWithCount(empty[0] + 1);
        CompoundTag settings = ItemPictureFrame.settings(copy).copy();
        // Блоки из слотов материалов в единственном числе: они вернутся игроку (onCrafted), картины остаются без них.
        boolean hadMaterials = false;
        for (PictureFrame.Material m : new PictureFrame().materials()) {
            if (settings.contains(m.stackTag())) {
                m.remove(settings);
                hadMaterials = true;
            }
        }
        if (empty[0] == 0) {
            // Разбор: сначала только вынимаем материалы — картина остаётся с настройками, но без рамки и холста (граффити). Повторный крафт сбрасывает её полностью.
            if (!hadMaterials) {
                return new ItemStack(MinePapers.FRAME.get());
            }
            settings.putInt("Mode", PictureFrame.MODE_DECAL);
        }
        ItemPictureFrame.setSettings(copy, settings);
        return copy;
    }

    //? if <26.1 {
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }
    //?}

    @Override
    public RecipeSerializer<RecipePictureFrame> getSerializer() {
        return MinePapers.FRAME_COPY.get();
    }

    /** Картина ушла в крафт (сброс, копирование, чужой рецепт) — блоки из её слотов материалов возвращаются в инвентарь (нет места — выпадают рядом), а не пропадают и не размножаются. Движок вызывает это, когда игрок забрал результат крафта из сетки grid, до того как ингредиенты списаны: каждый крафт тратит по одной картине из ячейки. */
    public static void onCrafted(Player player, Container grid) {
        if (player.level().isClientSide()) return;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            for (ItemStack stored : ItemPictureFrame.storedMaterials(grid.getItem(i))) {
                if (!player.getInventory().add(stored)) Compat.drop(player, stored);
            }
        }
    }
}
