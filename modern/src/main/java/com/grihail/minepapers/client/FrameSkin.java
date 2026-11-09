package com.grihail.minepapers.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
//? if >=26.1 {
/*import java.util.ArrayList;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.item.ItemDisplayContext;
*///?} else {
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.inventory.InventoryMenu;
//?}

/** Текстура рамки или холста: обычная или взятая с предмета. У блока — его боковая грань (с цветом, как у листвы), у остальных предметов — их иконка. Не нашли предмет или текстуру — обычная рамка. Спрайты блоков — из атласа блоков; с 26.1 у предметов свой атлас, поэтому атлас берётся у самого спрайта. */
public final class FrameSkin extends PictureMesh.Skin {

    public static final ResourceLocation BORDER = MinePapers.id("block/frame_border");
    public static final ResourceLocation CANVAS = MinePapers.id("block/frame_canvas");
    /** Обычная иконка предмета — пейзаж в деревянной рамке. */
    public static final ResourceLocation ICON = MinePapers.id("item/picture_frame");

    /** Найденные текстуры рамок; сбрасываются при перезагрузке ресурсов. */
    private static final Map<String, FrameSkin> CACHE = new HashMap<String, FrameSkin>();

    public final TextureAtlasSprite sprite;

    private FrameSkin(TextureAtlasSprite sprite, int color) {
        super(color);
        this.sprite = sprite;
    }

    @Override
    public Object atlas() {
        return sprite.atlasLocation();
    }

    @Override
    public double u(double px) {
        return u(sprite, px);
    }

    @Override
    public double v(double px) {
        return v(sprite, px);
    }

    /** Пиксель спрайта (0..16) -> координата в атласе. До 1.21 getU/getV принимали пиксели 0..16, с 1.21 — долю 0..1. */
    public static double u(TextureAtlasSprite sprite, double px) {
        //? if >=1.21 {
        return sprite.getU((float) (px / 16));
        //?} else {
        /*return sprite.getU(px);
        *///?}
    }

    public static double v(TextureAtlasSprite sprite, double px) {
        //? if >=1.21 {
        return sprite.getV((float) (px / 16));
        //?} else {
        /*return sprite.getV(px);
        *///?}
    }

    /** Спрайт из атласа блоков (block/...), а с 26.1 спрайты item/... - из атласа предметов. */
    public static TextureAtlasSprite sprite(ResourceLocation id) {
        //? if >=26.1 {
        /*boolean item = id.getPath().startsWith("item/");
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(item ? AtlasIds.ITEMS : AtlasIds.BLOCKS);
        return atlas.getSprite(id);
        *///?} else {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
        //?}
    }

    /** Текстура рамки из её материала. */
    public static FrameSkin border(PictureFrame.Material m) {
        return of(m.item, BORDER);
    }

    /** Текстура холста из его материала (обычная — frame_canvas). */
    public static FrameSkin canvas(PictureFrame.Material m) {
        return of(m.item, CANVAS);
    }

    /** Обычный холст — frame_canvas. */
    public static FrameSkin plainCanvas() {
        return of("", CANVAS);
    }

    private static FrameSkin of(String itemId, ResourceLocation fallback) {
        String key = fallback + "|" + itemId;
        FrameSkin skin = CACHE.get(key);
        if (skin == null) {
            skin = find(itemId, fallback);
            CACHE.put(key, skin);
        }
        return skin;
    }

    /** Атлас пересобран — спрайты стали другими. */
    public static void clearCache() {
        CACHE.clear();
    }

    //? if >=26.1 {
    /*private static FrameSkin find(String itemId, ResourceLocation fallback) {
        ItemStack stack = ItemPictureFrame.borderStack(itemId);
        if (stack.isEmpty()) return new FrameSkin(sprite(fallback), -1);
        Minecraft mc = Minecraft.getInstance();
        RandomSource random = RandomSource.create(42);
        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
            List<BlockStateModelPart> parts = new ArrayList<BlockStateModelPart>();
            model.collectParts(random, parts);
            BakedQuad quad = null;
            for (BlockStateModelPart part : parts) {
                quad = first(part.getQuads(Direction.NORTH));
                if (quad != null) break;
            }
            for (int i = 0; quad == null && i < parts.size(); i++) quad = first(parts.get(i).getQuads(null));
            if (quad != null) {
                BakedQuad.MaterialInfo info = quad.materialInfo();
                BlockTintSource tint = info.isTinted() ? mc.getBlockColors().getTintSource(state, info.tintIndex()) : null;
                return new FrameSkin(info.sprite(), tint != null ? tint.color(state) : -1);
            }
        }
        // Не блок: частица его модели - её иконка (цвет кожи и красок модель задаёт сама, здесь он не учитывается).
        ItemStackRenderState render = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(render, stack, ItemDisplayContext.GUI, null, null, 0);
        Material.Baked particle = render.pickParticleMaterial(random);
        return new FrameSkin(particle != null ? particle.sprite() : sprite(fallback), -1);
    }
    *///?} else {
    private static FrameSkin find(String itemId, ResourceLocation fallback) {
        ItemStack stack = ItemPictureFrame.borderStack(itemId);
        if (stack.isEmpty()) return new FrameSkin(sprite(fallback), -1);
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
            int color = quad.isTinted() ? ModClient.itemColors.get().getColor(stack, quad.getTintIndex()) : -1;
            return new FrameSkin(quad.getSprite(), color);
        }
        return new FrameSkin(model.getParticleIcon(), -1);
    }
    //?}

    private static BakedQuad first(List<BakedQuad> quads) {
        return quads.isEmpty() ? null : quads.get(0);
    }
}
