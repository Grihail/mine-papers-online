package com.grihail.minepapers.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Текстура рамки: обычная или взятая с предмета. У блока — его боковая грань (с цветом, как у листвы),
 * у остальных предметов — их иконка. Не нашли предмет или текстуру — обычная рамка.
 * В 1.21 текстуры блоков и предметов лежат в одном атласе, поэтому рамка рисуется одним типом отрисовки.
 */
public final class FrameSkin {

    public static final ResourceLocation BORDER = MinePapers.id("block/frame_border");
    public static final ResourceLocation CANVAS = MinePapers.id("block/frame_canvas");
    /** Обычная иконка предмета — пейзаж в деревянной рамке. */
    public static final ResourceLocation ICON = MinePapers.id("item/picture_frame");

    /** Поиск текстуры у модели дороже, чем в 1.7.10, поэтому кэшируем; сбрасывается при перезагрузке ресурсов. */
    private static final Map<String, FrameSkin> CACHE = new HashMap<String, FrameSkin>();

    public final TextureAtlasSprite sprite;
    public final float r, g, b;

    private FrameSkin(TextureAtlasSprite sprite, int color) {
        this.sprite = sprite;
        this.r = (color >> 16 & 0xFF) / 255f;
        this.g = (color >> 8 & 0xFF) / 255f;
        this.b = (color & 0xFF) / 255f;
    }

    public static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    public static FrameSkin standard() {
        return of("");
    }

    public static FrameSkin of(String itemId) {
        String key = itemId == null ? "" : itemId;
        FrameSkin skin = CACHE.get(key);
        if (skin == null) {
            skin = find(key);
            CACHE.put(key, skin);
        }
        return skin;
    }

    /** Атлас пересобран — спрайты стали другими. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static FrameSkin find(String itemId) {
        ItemStack stack = ItemPictureFrame.borderStack(itemId);
        if (stack != null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                RandomSource random = RandomSource.create(42);
                if (stack.getItem() instanceof BlockItem blockItem) {
                    BlockState state = blockItem.getBlock().defaultBlockState();
                    BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                    BakedQuad quad = first(model.getQuads(state, Direction.NORTH, random));
                    if (quad == null) quad = first(model.getQuads(state, null, random));
                    if (quad != null) {
                        int color = quad.isTinted() ? mc.getBlockColors().getColor(state, null, null, quad.getTintIndex()) : -1;
                        return new FrameSkin(quad.getSprite(), color);
                    }
                }
                BakedModel model = mc.getItemRenderer().getModel(stack, null, null, 0);
                BakedQuad quad = first(model.getQuads(null, null, random));
                if (quad != null) {
                    int color = quad.isTinted() ? mc.getItemColors().getColor(stack, quad.getTintIndex()) : -1;
                    return new FrameSkin(quad.getSprite(), color);
                }
                return new FrameSkin(model.getParticleIcon(), -1);
            } catch (Throwable ignored) {
                // Чужие моды бывают капризны к необычным вызовам — просто оставляем обычную рамку.
            }
        }
        return new FrameSkin(sprite(BORDER), -1);
    }

    private static BakedQuad first(List<BakedQuad> quads) {
        return quads.isEmpty() ? null : quads.get(0);
    }
}
