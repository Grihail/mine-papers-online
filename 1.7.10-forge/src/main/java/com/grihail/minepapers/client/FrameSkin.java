package com.grihail.minepapers.client;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.block.Block;
import net.minecraft.block.BlockGrass;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

/** Текстура рамки или холста: обычная или взятая с предмета. У блока — его боковая грань (с цветом, как у листвы), у остальных предметов — их иконка. Не нашли предмет или иконку — обычная рамка. */
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

    /** Текстура рамки из её материала. */
    public static FrameSkin border(PictureFrame.Material m) {
        return of(m.item, m.meta, standard());
    }

    /** Текстура холста из его материала (обычная — frame_canvas). */
    public static FrameSkin canvas(PictureFrame.Material m) {
        return of(m.item, m.meta, new FrameSkin(MinePapers.frame.canvasIcon, TextureMap.locationBlocksTexture, 0xFFFFFF));
    }

    /** Иконки после перезагрузки ресурсов меняются, поэтому не кэшируем — поиск в реестре дешёвый. */
    private static FrameSkin of(String itemName, int meta, FrameSkin fallback) {
        if (itemName == null || itemName.isEmpty()) return fallback;
        try {
            Item item = (Item) Item.itemRegistry.getObject(itemName);
            if (item == null) return fallback;
            Block block = Block.getBlockFromItem(item);
            if (item instanceof ItemBlock && block != null && block != Blocks.air) {
                int blockMeta = item.getMetadata(meta);
                IIcon icon = block.getIcon(2, blockMeta);
                // Боковину травы игра не красит: зелёная кайма уже нарисована в текстуре, а цвет травы идёт только на накладку. Покрасим целиком — позеленеет и земля.
                int color = block instanceof BlockGrass ? 0xFFFFFF : block.getRenderColor(blockMeta);
                if (icon != null) return new FrameSkin(icon, TextureMap.locationBlocksTexture, color);
            }
            IIcon icon = item.getIconFromDamage(meta);
            if (icon != null) {
                ResourceLocation atlas = item.getSpriteNumber() == 0 ? TextureMap.locationBlocksTexture
                    : TextureMap.locationItemsTexture;
                return new FrameSkin(icon, atlas, item.getColorFromItemStack(new ItemStack(item, 1, meta), 0));
            }
        } catch (Throwable ignored) {
            // Чужие моды бывают капризны к необычным метаданным — просто оставляем обычную текстуру.
        }
        return fallback;
    }
}
