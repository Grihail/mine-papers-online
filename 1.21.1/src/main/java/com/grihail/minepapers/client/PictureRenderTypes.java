package com.grihail.minepapers.client;

import java.util.OptionalDouble;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Свои варианты ванильных типов отрисовки. */
public final class PictureRenderTypes {

    private PictureRenderTypes() {}

    /** Картинка-граффити: как entityTranslucent, но со смещением полигонов — прижата к стене без мерцания. */
    private static final Function<ResourceLocation, RenderType> DECAL = Util.memoize(texture -> RenderType.create(
        "minepapers_decal", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
            .createCompositeState(true)));

    /** Контур будущей картины: виден и сквозь блоки, линии потолще ванильной обводки. */
    public static final RenderType GHOST_LINES = RenderType.create(
        "minepapers_ghost_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.5)))
            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false));

    public static RenderType decal(ResourceLocation texture) {
        return DECAL.apply(texture);
    }
}
