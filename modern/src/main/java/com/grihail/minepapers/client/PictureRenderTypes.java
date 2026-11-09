package com.grihail.minepapers.client;

//? if >=26.1 {
/*import java.util.function.Function;

import com.grihail.minepapers.MinePapers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.Util;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
*///?} else {
import java.util.OptionalDouble;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
//?}
//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
*///?} else if >=26.2 {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.BindGroupLayouts;
*///?} else if >=26.1 {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
*///?}

/** Типы отрисовки картины и иконки: ванильные (под общими именами - они называются по-разному в разных версиях) и свои: граффити и контур будущей картины. */
//? if >=26.1 {
/*public final class PictureRenderTypes {

    private PictureRenderTypes() {}
*///?} else {
// Наследник RenderType - только ради доступа к его защищённым частям (в старых версиях они не открыты); экземпляров нет.
public final class PictureRenderTypes extends RenderType {

    private PictureRenderTypes() {
        super("minepapers", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
    }
//?}

    //? if >=26.3 {
    /*// С 26.2 глубина обратная: ближе - больше, смещение к зрителю - положительное.
    private static final RenderPipeline DECAL_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation(MinePapers.id("pipeline/decal"))
        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        .withShaderDefine("PER_FACE_LIGHTING")
        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 1f, 10f))
        .build();
    private static final RenderPipeline GHOST_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(MinePapers.id("pipeline/ghost_lines"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();
    *///?} else if >=26.2 {
    /*private static final RenderPipeline DECAL_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation(MinePapers.id("pipeline/decal"))
        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        .withShaderDefine("PER_FACE_LIGHTING")
        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 1f, 10f))
        .build();
    private static final RenderPipeline GHOST_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(MinePapers.id("pipeline/ghost_lines"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();
    *///?} else if >=26.1 {
    /*private static final RenderPipeline DECAL_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation(MinePapers.id("pipeline/decal"))
        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        .withShaderDefine("PER_FACE_LIGHTING")
        .withSampler("Sampler1")
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1f, -10f))
        .build();
    private static final RenderPipeline GHOST_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(MinePapers.id("pipeline/ghost_lines"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();
    *///?}

    //? if >=26.1 {
    /*/^* Картинка-граффити: как entityTranslucent, но со смещением глубины - прижата к стене без мерцания. *^/
    private static final Function<ResourceLocation, RenderType> DECAL = Util.memoize(texture -> RenderType.create("minepapers_decal",
        RenderSetup.builder(DECAL_PIPELINE).withTexture("Sampler0", texture).useLightmap().useOverlay().sortOnUpload().createRenderSetup()));

    /^* Контур будущей картины: виден и сквозь блоки, линии потолще ванильной обводки (толщину задают вершины, см. lineBox). *^/
    public static final RenderType GHOST_LINES = RenderType.create("minepapers_ghost_lines",
        RenderSetup.builder(GHOST_PIPELINE).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());
    *///?} else {
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
    //?}

    public static RenderType decal(ResourceLocation texture) {
        return DECAL.apply(texture);
    }

    /** Непрозрачное с вырезами, обе стороны (материалы рамки и холста, значок "18+"). */
    public static RenderType cutout(ResourceLocation texture) {
        //? if >=26.1 {
        /*return RenderTypes.entityCutout(texture);
        *///?} else {
        return RenderType.entityCutoutNoCull(texture);
        //?}
    }

    /** Полупрозрачное (сама картинка). */
    public static RenderType translucent(ResourceLocation texture) {
        //? if >=26.1 {
        /*return RenderTypes.entityTranslucent(texture);
        *///?} else {
        return RenderType.entityTranslucent(texture);
        //?}
    }

    /** Обводка хитбокса - как у блоков. */
    public static RenderType lines() {
        //? if >=26.1 {
        /*return RenderTypes.lines();
        *///?} else {
        return RenderType.lines();
        //?}
    }

    /** Блеск предметов отдельным слоем поверх иконки; null - отдельного слоя нет (с 26.3 блеск рисует сам тип отрисовки, см. foil). */
    public static RenderType glint() {
        //? if >=26.3 {
        /*return null;
        *///?} else if >=26.1 {
        /*return RenderTypes.glint();
        *///?} else {
        return RenderType.glint();
        //?}
    }

    /** С 26.3: тот же тип, но с блеском; узор блеска берётся из третьих координат текстуры (setUv3), одних на всю иконку. До 26.3 - сам тип (блеск рисуется слоем, см. glint). */
    public static RenderType foil(RenderType type, ResourceLocation texture, boolean translucent) {
        //? if >=26.3 {
        /*return translucent ? RenderTypes.itemTranslucentGlintSpecial(texture) : RenderTypes.itemCutoutGlintSpecial(texture);
        *///?} else {
        return type;
        //?}
    }

    //? if >=26.1 {
    /*/^* Рёбра коробки линиями толщиной width (с 26.1 толщину линии задаёт сама вершина). *^/
    public static void lineBox(VertexConsumer out, PoseStack.Pose pose, AABB b, float r, float g, float bl, float a, float width) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        float[][] edges = {
            { x0, y0, z0, x1, y0, z0 }, { x0, y1, z0, x1, y1, z0 }, { x0, y0, z1, x1, y0, z1 }, { x0, y1, z1, x1, y1, z1 },
            { x0, y0, z0, x0, y1, z0 }, { x1, y0, z0, x1, y1, z0 }, { x0, y0, z1, x0, y1, z1 }, { x1, y0, z1, x1, y1, z1 },
            { x0, y0, z0, x0, y0, z1 }, { x1, y0, z0, x1, y0, z1 }, { x0, y1, z0, x0, y1, z1 }, { x1, y1, z0, x1, y1, z1 },
        };
        for (float[] e : edges) {
            float nx = e[3] - e[0], ny = e[4] - e[1], nz = e[5] - e[2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= len;
            ny /= len;
            nz /= len;
            out.addVertex(pose, e[0], e[1], e[2]).setColor(r, g, bl, a).setNormal(pose, nx, ny, nz).setLineWidth(width);
            out.addVertex(pose, e[3], e[4], e[5]).setColor(r, g, bl, a).setNormal(pose, nx, ny, nz).setLineWidth(width);
        }
    }
    *///?}
}
