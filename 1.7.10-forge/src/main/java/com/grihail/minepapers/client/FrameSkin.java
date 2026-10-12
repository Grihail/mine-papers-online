package com.grihail.minepapers.client;

import com.grihail.minepapers.MinePapers;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

/** Текстура рамки: обычная или взятая с предмета. У блока — его боковая грань (с цветом, как у листвы), у остальных предметов — их иконка. Не нашли предмет или иконку — обычная рамка. */
public final class FrameSkin {

    public final IIcon icon;
    public final ResourceLocation atlas;
    public final float r, g, b;

    private FrameSkin(IIcon icon, ResourceLocation atlas, int color) {
        this.icon = icon;
        this.atlas = atlas;
        this.r = (color >> 16 & 0xFF) / 255f;
        this.g = (color >> 8 & 0xFF) / 255f;
        this.b = (color & 0xFF) / 255f;
    }

    public static FrameSkin standard() {
        return new FrameSkin(MinePapers.frame.getIcon(0, 0), TextureMap.locationBlocksTexture, 0xFFFFFF);
    }

    /** Иконки после перезагрузки ресурсов меняются, поэтому не кэшируем — поиск в реестре дешёвый. */
    public static FrameSkin of(String itemName, int meta) {
        if (itemName == null || itemName.isEmpty()) return standard();
        try {
            Item item = (Item) Item.itemRegistry.getObject(itemName);
            if (item == null) return standard();
            Block block = Block.getBlockFromItem(item);
            if (item instanceof ItemBlock && block != null && block != Blocks.air) {
                int blockMeta = item.getMetadata(meta);
                IIcon icon = block.getIcon(2, blockMeta);
                if (icon != null) return new FrameSkin(icon, TextureMap.locationBlocksTexture, block.getRenderColor(blockMeta));
            }
            IIcon icon = item.getIconFromDamage(meta);
            if (icon != null) {
                ResourceLocation atlas = item.getSpriteNumber() == 0 ? TextureMap.locationBlocksTexture
                    : TextureMap.locationItemsTexture;
                return new FrameSkin(icon, atlas, item.getColorFromItemStack(new ItemStack(item, 1, meta), 0));
            }
        } catch (Throwable ignored) {
            // Чужие моды бывают капризны к необычным метаданным — просто оставляем обычную рамку.
        }
        return standard();
    }

    /** Предмет, с которого взята текстура (для подсказок), или null. */
    public static ItemStack stack(String itemName, int meta) {
        if (itemName == null || itemName.isEmpty()) return null;
        Item item = (Item) Item.itemRegistry.getObject(itemName);
        return item != null ? new ItemStack(item, 1, meta) : null;
    }
}
