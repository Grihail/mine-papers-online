package com.grihail.minepapers.client;

import java.util.List;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Текстура рамки или холста: обычная или взятая с предмета. У блока — его боковая грань (с цветом, как у листвы), у остальных предметов — их иконка. Не нашли предмет или иконку — обычная рамка. Всё лежит в атласе блоков (в 1.12.2 он общий с предметами). */
public final class FrameSkin extends PictureMesh.Skin {

    private static final ResourceLocation BORDER = new ResourceLocation(MinePapers.MODID, "blocks/frame_border");
    private static final ResourceLocation CANVAS = new ResourceLocation(MinePapers.MODID, "blocks/frame_canvas");
    private static final ResourceLocation GLASS = new ResourceLocation(MinePapers.MODID, "blocks/frame_glass");
    /** Обычные рамка, холст и стекло: кладутся в атлас блоков при его сборке (Events). */
    private static TextureAtlasSprite border, canvas, glass;

    public final TextureAtlasSprite icon;

    private FrameSkin(TextureAtlasSprite icon, int color) {
        super(color);
        this.icon = icon;
    }

    @Override
    public Object atlas() {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }

    @Override
    public double u(double px) {
        return icon.getInterpolatedU(px);
    }

    @Override
    public double v(double px) {
        return icon.getInterpolatedV(px);
    }

    public static FrameSkin standard() {
        return new FrameSkin(border, 0xFFFFFF);
    }

    /** Текстура рамки из её материала. */
    public static FrameSkin border(PictureFrame.Material m) {
        return of(m.item, m.meta, standard());
    }

    /** Текстура холста из его материала. */
    public static FrameSkin canvas(PictureFrame.Material m) {
        return of(m.item, m.meta, plainCanvas());
    }

    /** Текстура стекла из его материала (блок — его текстурой, плиткой); пусто — обычное стекло мода, растянутое по проёму. */
    public static FrameSkin glass(PictureFrame.Material m) {
        if (!m.item.isEmpty()) return of(m.item, m.meta, standard());
        FrameSkin plain = new FrameSkin(glass, 0xFFFFFF);
        plain.stretched = true;
        return plain;
    }

    /** Обычный холст — frame_canvas. */
    public static FrameSkin plainCanvas() {
        return new FrameSkin(canvas, 0xFFFFFF);
    }

    /** Текстуры после перезагрузки ресурсов меняются, поэтому не кэшируем — поиск в реестре и модели дешёвый. */
    @SuppressWarnings("deprecation")
    private static FrameSkin of(String itemName, int meta, FrameSkin fallback) {
        Item item = itemName.isEmpty() ? null : Item.getByNameOrId(itemName);
        if (item == null) return fallback;
        Minecraft mc = Minecraft.getMinecraft();
        Block block = Block.getBlockFromItem(item);
        if (block != Blocks.AIR) {
            IBlockState state = block.getStateFromMeta(item.getMetadata(meta));
            IBakedModel model = mc.getBlockRendererDispatcher().getModelForState(state);
            // Боковая грань (у травы — сама земля с каймой: цвет травы идёт только на накладку, второй квадрат).
            List<BakedQuad> quads = model.getQuads(state, EnumFacing.NORTH, 0);
            if (!quads.isEmpty()) {
                BakedQuad quad = quads.get(0);
                int color = quad.hasTintIndex() ? mc.getBlockColors().colorMultiplier(state, null, null, quad.getTintIndex()) : 0xFFFFFF;
                return new FrameSkin(quad.getSprite(), color);
            }
            TextureAtlasSprite particle = model.getParticleTexture();
            if (particle != null) return new FrameSkin(particle, 0xFFFFFF);
        }
        ItemStack stack = new ItemStack(item, 1, meta);
        TextureAtlasSprite icon = mc.getRenderItem().getItemModelWithOverrides(stack, null, null).getParticleTexture();
        if (icon == null) return fallback;
        return new FrameSkin(icon, mc.getItemColors().colorMultiplier(stack, 0));
    }

    /** Свои текстуры рамки и холста — в атлас блоков. */
    public static final class Events {

        @SubscribeEvent
        public void onStitch(TextureStitchEvent.Pre event) {
            border = event.getMap().registerSprite(BORDER);
            canvas = event.getMap().registerSprite(CANVAS);
            glass = event.getMap().registerSprite(GLASS);
        }
    }
}
