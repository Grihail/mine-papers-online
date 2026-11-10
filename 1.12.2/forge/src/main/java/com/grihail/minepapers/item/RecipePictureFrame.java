package com.grihail.minepapers.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;

import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.FrameRecipe;
import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraft.item.Item;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraft.init.Items;

/** Рецепты без формы, как копирование карт (заприваченные и подписанные картины не копируются, а разбираются, только если это разрешено в config/minepapers.json): одна настроенная рамка — разбор (если в ней лежат материалы — сначала только они: картина остаётся с настройками в виде граффити; иначе сброс до пустой); настроенная + пустые — столько же копий настроенной без материалов. Блоки из слотов материалов возвращаются игроку (см. Events). */
public class RecipePictureFrame extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    /** Рецепт новой рамки из config/minepapers.json (ModConfig.recipe) — обычный рецепт Forge, его видно в JEI. Нет какого-то предмета в игре — рецепт по умолчанию. */
    public static IRecipe frameRecipe() {
        IRecipe recipe = build(ModConfig.recipe);
        if (recipe != null) return recipe;
        LogManager.getLogger("minepapers").warn("В рецепте рамки из config/minepapers.json есть предмет, которого нет в игре; действует рецепт по умолчанию");
        return build(FrameRecipe.DEFAULT);
    }

    private static IRecipe build(FrameRecipe recipe) {
        ItemStack result = new ItemStack(MinePapers.frame);
        List<Object> args = new ArrayList<Object>();
        if (!recipe.shaped()) {
            for (String id : recipe.ingredients) {
                ItemStack stack = stackOf(id);
                if (stack == null) return null;
                args.add(stack);
            }
            return new ShapelessOreRecipe(null, result, args.toArray());
        }
        args.addAll(Arrays.asList((Object[]) recipe.pattern));
        for (Map.Entry<Character, String> e : recipe.key.entrySet()) {
            ItemStack stack = stackOf(e.getValue());
            if (stack == null) return null;
            args.add(e.getKey());
            args.add(stack);
        }
        return new ShapedOreRecipe(null, result, args.toArray());
    }

    /** Предмет рецепта "id" или "id:meta" (без метаданных — любые); null — такого нет. */
    private static ItemStack stackOf(String item) {
        String[] p = FrameRecipe.split(item);
        Item found = Item.getByNameOrId(p[0]);
        if (found == null || found == Items.AIR) return null;
        return new ItemStack(found, 1, p[1] != null ? Integer.parseInt(p[1]) : OreDictionary.WILDCARD_VALUE);
    }


    /** Настроенная рамка из сетки или null, если сетка не подходит под рецепт. Пустые пишутся в empty[0]. */
    private static ItemStack find(InventoryCrafting inv, int[] empty) {
        ItemStack configured = null;
        empty[0] = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() != MinePapers.frame) return null;
            if (ItemPictureFrame.settings(stack) != null) {
                if (configured != null) return null; // две настроенные — непонятно, чью копировать
                configured = stack;
            } else {
                empty[0]++;
            }
        }
        // Заприваченную и подписанную не копируют никогда, а разбирают — только если это разрешено (ModConfig.mayCraft).
        if (configured != null && !ModConfig.mayCraft(ItemPictureFrame.isLocked(configured), ItemPictureFrame.isSigned(configured), empty[0] > 0)) {
            return null;
        }
        return configured;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        return !getCraftingResult(inv).isEmpty();
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        int[] empty = new int[1];
        ItemStack configured = find(inv, empty);
        if (configured == null) return ItemStack.EMPTY;
        ItemStack copy = configured.copy();
        copy.setCount(empty[0] + 1);
        NBTTagCompound settings = copy.getTagCompound().getCompoundTag(ItemPictureFrame.ITEM_TAG);
        // Блоки из слотов материалов в единственном числе: они вернутся игроку (Events), картины остаются без них.
        boolean hadMaterials = stripMaterials(settings);
        if (empty[0] == 0) {
            // Разбор: сначала только вынимаем материалы — картина остаётся с настройками, но без рамки и холста (граффити). Повторный крафт сбрасывает её полностью.
            if (!hadMaterials) return new ItemStack(MinePapers.frame);
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
    public boolean canFit(int width, int height) {
        return width * height >= 1;
    }

    /** В книге рецептов не показывается: результат зависит от настроек картины. */
    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(MinePapers.frame);
    }

    /** Картина ушла в крафт (сброс, копирование, чужой рецепт) — блоки из её слотов материалов возвращаются в инвентарь (нет места — выпадают рядом), а не пропадают и не размножаются. Событие приходит до того, как ингредиенты списаны: каждый крафт тратит по одной картине из ячейки. */
    public static class Events {

        @SubscribeEvent
        public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
            if (event.player.world.isRemote) return;
            IInventory grid = event.craftMatrix;
            for (int i = 0; i < grid.getSizeInventory(); i++) {
                for (ItemStack stored : ItemPictureFrame.storedMaterials(grid.getStackInSlot(i))) {
                    if (!event.player.inventory.addItemStackToInventory(stored)) {
                        event.player.dropItem(stored, false);
                    }
                }
            }
        }
    }
}
