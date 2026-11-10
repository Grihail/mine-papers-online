package com.grihail.minepapers.client;

import java.util.ArrayList;
import java.util.List;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
//? if >=26.1 {
/*import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
*///?} else {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
//?}

/** Иконка настроенной рамки: рамка из выбранной текстуры по пропорциям холста и уменьшенная картинка внутри (на сетке 16x16, как у обычных предметов). В руке и на земле — пластинка толщиной в пиксель. Ненастроенные рамки рисуются обычной моделью предмета (см. models/item/picture_frame.json). Рисует в кубе 0..1, как модели предметов item/generated: пластинка между z = 7.5/16 и 8.5/16. С 26.1 - особый рендерер модели предмета (items/picture_frame.json в modern/src26): плоская иконка в инвентаре - его вариант flat. */
//? if >=26.1 {
/*public class RenderPictureItem implements SpecialModelRenderer<ItemStack> {
*///?} else {
public class RenderPictureItem extends BlockEntityWithoutLevelRenderer {
//?}

    private static final int ICON = IconLayout.ICON, RING = IconLayout.RING;
    private static final float THICKNESS = 1f / 16f;
    private static final float FRONT = 0.5f + THICKNESS / 2, BACK = 0.5f - THICKNESS / 2;
    /** Шахматка под картинкой без холста в инвентаре: прозрачный фон картинки виден как сетка, и картина не теряется в слоте. Клетка — пиксель иконки. */
    private static final ResourceLocation ALPHA_GRID = MinePapers.id("textures/misc/alpha_grid.png");
    /** Значок "18+" на скрытой картине NSFW (как на холсте в мире). */
    private static final ResourceLocation NSFW_MARK = MinePapers.id("textures/misc/nsfw.png");
    /** Метки светящегося материала (см. IconLayout.glowMark). */
    private static final ResourceLocation GLOW_MARKS = MinePapers.id("textures/misc/glow_marks.png");

    private boolean flat;
    private PoseStack.Pose pose;
    private VertexSink sink;
    /** Блестит (с 26.3 блеск рисуют сами типы отрисовки, см. buffer). */
    private boolean foil;
    private VertexConsumer out;
    private int light;
    private float colorR = 1f, colorG = 1f, colorB = 1f;

    //? if >=26.1 {
    /*public static final ResourceLocation ID = MinePapers.id("picture_frame");

    /^* Иконка в инвентаре (flat) или пластинка в руке и на земле: выбирает items/picture_frame.json по месту показа. *^/
    public record Unbaked(boolean flat) implements SpecialModelRenderer.Unbaked<ItemStack> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("flat", false).forGetter(Unbaked::flat)).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<ItemStack> bake(SpecialModelRenderer.BakingContext context) {
            return new RenderPictureItem(flat);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private RenderPictureItem(boolean flat) {
        this.flat = flat;
    }

    @Override
    public ItemStack extractArgument(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    @Override
    public void submit(ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
                       boolean hasFoil, int outlineColor) {
        if (stack == null) return;
        Recorder recorder = new Recorder();
        draw(stack, poseStack.last(), recorder, lightCoords, hasFoil);
        recorder.submit(collector, poseStack);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (float z : new float[] { BACK, FRONT }) {
            output.accept(new Vector3f(0, 0, z));
            output.accept(new Vector3f(1, 0, z));
            output.accept(new Vector3f(0, 1, z));
            output.accept(new Vector3f(1, 1, z));
        }
    }
    *///?} else {
    public RenderPictureItem() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        flat = context == ItemDisplayContext.GUI;
        draw(stack, poseStack.last(), buffers::getBuffer, packedLight, stack.hasFoil());
    }
    //?}

    /** Буфер для текстуры: непрозрачной с вырезами или полупрозрачной; у блестящей картины с 26.3 - тот же тип с блеском. */
    private VertexConsumer buffer(ResourceLocation texture, boolean translucent) {
        RenderType type = translucent ? PictureRenderTypes.translucent(texture) : PictureRenderTypes.cutout(texture);
        return sink.buffer(foil ? PictureRenderTypes.foil(type, texture, translucent) : type);
    }

    private VertexConsumer buffer(TextureAtlasSprite sprite) {
        return buffer(sprite.atlasLocation(), false);
    }

    private void draw(ItemStack stack, PoseStack.Pose pose, VertexSink sink, int packedLight, boolean foil) {
        CompoundTag settings = ItemPictureFrame.settings(stack);
        if (settings == null) return;
        PictureFrame s = new PictureFrame();
        s.readSettings(settings);
        IconLayout l = new IconLayout(s);
        boolean plate = l.plate, canvasBlock = l.canvasBlock;
        int ring = l.ring, x0 = l.x0, y0 = l.y0, x1 = l.x1, y1 = l.y1, cx0 = l.cx0, cy0 = l.cy0, cx1 = l.cx1, cy1 = l.cy1;
        PictureManager.Variant thumb = l.thumbnail();

        this.pose = pose;
        this.sink = sink;
        this.foil = foil;
        light = packedLight;
        surfaces.clear();

        // 1. Рамка (и торцы пластинки)
        if (ring > 0) {
            FrameSkin skin = FrameSkin.border(s.border);
            out = buffer(skin.sprite);
            color(skin.r, skin.g, skin.b);
            for (int face = 0; face < faces(); face++) ring(skin.sprite, face, x0, y0, x1, y1);
            if (!flat) sides(skin.sprite, x0, y0, x1, y1);
        } else if (canvasBlock && !flat) {
            // Холст без рамки: торцы — из самого холста.
            FrameSkin canvas = FrameSkin.canvas(s.canvas);
            out = buffer(canvas.sprite);
            color(canvas.r, canvas.g, canvas.b);
            sides(canvas.sprite, x0, y0, x1, y1);
        }

        // 2. Картинка. На холсте из блока — на его фоне, как в мире. Без холста прозрачный фон картинки остаётся прозрачным (в инвентаре под ним шахматка), а в руке и на земле она выдавливается по пикселям, как обычные предметы. Пластина — цельная, торцы только по краю.
        color(1f, 1f, 1f);
        if (thumb != null) {
            if (canvasBlock) {
                FrameSkin canvas = FrameSkin.canvas(s.canvas);
                out = buffer(canvas.sprite);
                color(canvas.r, canvas.g, canvas.b);
                for (int face = 0; face < faces(); face++) atlasRect(canvas.sprite, face, cx0, cy0, cx1, cy1);
                color(1f, 1f, 1f);
            } else if (flat) {
                out = buffer(ALPHA_GRID, true);
                rect(0, cx0, cy0, cx1, cy1, cx0 / (double) ICON, cy0 / (double) ICON, cx1 / (double) ICON, cy1 / (double) ICON, 0);
            }
            out = buffer((ResourceLocation) thumb.getTexture(), true);
            double[] p = l.picture(thumb);
            if (p != null) {
                if (ring == 0 && !canvasBlock && !flat && thumb.pixels != null) {
                    int[] px = thumb.pixels;
                    int tw = thumb.width;
                    extruded(p[0], p[1], p[2], p[3], 0, 0, 1, 1, thumb.width, thumb.height,
                        (x, y) -> plate || (px[y * tw + x] >>> 24) != 0);
                } else {
                    for (int face = 0; face < faces(); face++) rect(face, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], 1);
                }
            }
        } else {
            // Пока картинки нет — пейзаж с обычной иконки (без рамки — без её деревянной рамки)
            TextureAtlasSprite placeholder = FrameSkin.sprite(FrameSkin.ICON);
            out = buffer(placeholder);
            if (ring > 0 || canvasBlock) {
                for (int face = 0; face < faces(); face++) atlasRect(placeholder, face, cx0, cy0, cx1, cy1);
            } else {
                double pu0 = texU(placeholder, RING), pu1 = texU(placeholder, ICON - RING);
                double pv0 = texV(placeholder, RING), pv1 = texV(placeholder, ICON - RING);
                if (flat) {
                    rect(0, cx0, cy0, cx1, cy1, pu0, pv0, pu1, pv1, 0);
                } else {
                    int size = ICON - 2 * RING;
                    extruded(cx0, cy0, cx1, cy1, pu0, pv0, pu1, pv1, size, size,
                        (x, y) -> !placeholder.contents().isTransparent(0, RING + x, RING + y));
                }
            }
        }
        // 2б. Стекло поверх картинки, как в мире: обычное стекло мода или текстура блока из слота стекла — на весь холст.
        if (s.glass) {
            FrameSkin glass = FrameSkin.glass(s.pane);
            out = buffer(glass.sprite.atlasLocation(), true);
            color(glass.r, glass.g, glass.b);
            for (int face = 0; face < faces(); face++) {
                rect(face, cx0, cy0, cx1, cy1, texU(glass.sprite, 0), texV(glass.sprite, 0), texU(glass.sprite, ICON), texV(glass.sprite, ICON), 2);
            }
            color(1f, 1f, 1f);
        }
        // 3. Скрытая картина NSFW: поверх заглушки — значок "18+" по центру холста, квадратом по меньшей стороне.
        if (l.hidden) {
            double[] m = l.nsfwMark();
            out = buffer(NSFW_MARK, false);
            color(1f, 1f, 1f);
            for (int face = 0; face < faces(); face++) rect(face, m[0], m[1], m[2], m[3], 0, 0, 1, 1, 2);
        }
        // 4. В инвентаре — метка светящегося материала в правом верхнем углу.
        int glow = IconLayout.glowMarkKind(PictureFrame.isGlowDust(s.glow.stack()), s.glowLevel());
        if (flat && glow != IconLayout.GLOW_MARK_NONE) {
            double[] m = IconLayout.glowMark(), uv = IconLayout.glowMarkUv(glow);
            out = buffer(GLOW_MARKS, true); // ореол метки полупрозрачный
            color(1f, 1f, 1f);
            rect(0, m[0], m[1], m[2], m[3], uv[0], uv[1], uv[2], uv[3], 3);
        }
        if (foil && PictureRenderTypes.glint() != null) glint();
        out = null;
        this.pose = null;
        this.sink = null;
    }

    /** Все нарисованные лицевые прямоугольники {сторона, x0, y0, x1, y1, слой}: блеск повторяет их один в один. Проверка глубины у блеска — "равно", и одинаковая глубина гарантирована только у тех же самых вершин; один большой прямоугольник поверх рамки из девяти частей мерцал бы. */
    private final List<double[]> surfaces = new ArrayList<double[]>();
    /** Записывать прямоугольники (выключено, пока рисуется сам блеск). */
    private boolean recording = true;


    /** Узор блеска на всю иконку — как у ванильного предмета: его спрайт 16x16 занимает 1/64 атласа. */
    private static final double GLINT_SPAN = 1.0 / 64;

    /** Блеск зачарования (заприваченная картина): свой слой поверх уже нарисованных плоскостей (проверка глубины "равно"), с одними координатами текстуры по всей иконке. Иначе каждая часть (атлас блоков, картинка) получила бы свой масштаб узора. Блеск предметов, а не сущностей (entityGlint — тусклый, как у брони). */
    private void glint() {
        out = sink.buffer(PictureRenderTypes.glint());
        recording = false;
        double k = GLINT_SPAN / ICON;
        for (double[] r : surfaces) {
            rect((int) r[0], r[1], r[2], r[3], r[4], r[1] * k, r[2] * k, r[3] * k, r[4] * k, (int) r[5]);
        }
        recording = true;
    }

    /** Непрозрачен ли пиксель (x, y) текстуры (строки сверху вниз). */
    private interface Opaque {
        boolean at(int x, int y);
    }

    /** Объёмная пластинка по пикселям текстуры (прозрачные пиксели — дыры, торцы повторяют форму), как у ванильных предметов. Прямоугольник (x0, y0)–(x1, y1) — в пикселях иконки, (u0, v0)–(u1, v1) — его часть текстуры. */
    private void extruded(double x0, double y0, double x1, double y1, double u0, double v0, double u1, double v1,
                          int texW, int texH, Opaque opaque) {
        rect(0, x0, y0, x1, y1, u0, v0, u1, v1, 0);
        rect(1, x0, y0, x1, y1, u0, v0, u1, v1, 0);
        double sx = (x1 - x0) / texW, sy = (y1 - y0) / texH;
        double su = (u1 - u0) / texW, sv = (v1 - v0) / texH;
        for (int j = 0; j < texH; j++) {
            for (int i = 0; i < texW; i++) {
                if (!opaque.at(i, j)) continue;
                double ax = x0 + i * sx, bx = ax + sx, ay = y0 + j * sy, by = ay + sy;
                // Торец берёт цвет самого пикселя: середина его участка текстуры.
                double tu = u0 + (i + 0.5) * su, tv = v0 + (j + 0.5) * sv;
                if (j == 0 || !opaque.at(i, j - 1)) sideH(ax, bx, ay, tu, tu, tv, tv, 1f);
                if (j == texH - 1 || !opaque.at(i, j + 1)) sideH(bx, ax, by, tu, tu, tv, tv, -1f);
                if (i == 0 || !opaque.at(i - 1, j)) sideV(ax, by, ay, tu, tu, tv, tv, -1f);
                if (i == texW - 1 || !opaque.at(i + 1, j)) sideV(bx, ay, by, tu, tu, tv, tv, 1f);
            }
        }
    }

    /** В инвентаре — одна лицевая сторона, в мире — лицевая и задняя. */
    private int faces() {
        return flat ? 1 : 2;
    }

    /** Рамка из 8 частей: углы 2x2 из углов текстуры, стороны — из её краёв. */
    private void ring(TextureAtlasSprite icon, int face, int x0, int y0, int x1, int y1) {
        int r = RING;
        texRect(icon, face, x0, y0, x0 + r, y0 + r, 0, 0);
        texRect(icon, face, x1 - r, y0, x1, y0 + r, ICON - r, 0);
        texRect(icon, face, x0, y1 - r, x0 + r, y1, 0, ICON - r);
        texRect(icon, face, x1 - r, y1 - r, x1, y1, ICON - r, ICON - r);
        texRect(icon, face, x0 + r, y0, x1 - r, y0 + r, r, 0);
        texRect(icon, face, x0 + r, y1 - r, x1 - r, y1, r, ICON - r);
        texRect(icon, face, x0, y0 + r, x0 + r, y1 - r, 0, r);
        texRect(icon, face, x1 - r, y0 + r, x1, y1 - r, ICON - r, r);
    }

    /** Торцы пластинки: крайний ряд пикселей текстуры по всей толщине. */
    private void sides(TextureAtlasSprite icon, int x0, int y0, int x1, int y1) {
        double u0 = texU(icon, x0), u1 = texU(icon, x1);
        double v0 = texV(icon, y0), v1 = texV(icon, y1);
        double top = texV(icon, 0.5), bottom = texV(icon, ICON - 0.5);
        double left = texU(icon, 0.5), right = texU(icon, ICON - 0.5);
        sideH(x0, x1, y0, u0, u1, top, top, 1f);
        sideH(x1, x0, y1, u1, u0, bottom, bottom, -1f);
        sideV(x0, y1, y0, left, left, v1, v0, -1f);
        sideV(x1, y0, y1, right, right, v0, v1, 1f);
    }

    /** Горизонтальный торец на высоте y (y иконки идёт вниз): u вдоль ребра, v — по толщине (от лица к спинке). */
    private void sideH(double xa, double xb, double y, double ua, double ub, double va, double vb, float ny) {
        vertex(xa, y, FRONT, ua, va, 0f, ny, 0f);
        vertex(xb, y, FRONT, ub, va, 0f, ny, 0f);
        vertex(xb, y, BACK, ub, vb, 0f, ny, 0f);
        vertex(xa, y, BACK, ua, vb, 0f, ny, 0f);
    }

    /** Вертикальный торец на x: v вдоль ребра, u — по толщине. */
    private void sideV(double x, double ya, double yb, double ua, double ub, double va, double vb, float nx) {
        vertex(x, ya, FRONT, ua, va, nx, 0f, 0f);
        vertex(x, yb, FRONT, ub, vb, nx, 0f, 0f);
        vertex(x, yb, BACK, ub, vb, nx, 0f, 0f);
        vertex(x, ya, BACK, ua, va, nx, 0f, 0f);
    }

    /** Кусок текстуры того же размера, что и на иконке, начиная с пикселя (tu, tv). */
    private void texRect(TextureAtlasSprite icon, int face, int x0, int y0, int x1, int y1, int tu, int tv) {
        if (x1 <= x0 || y1 <= y0) return;
        rect(face, x0, y0, x1, y1, texU(icon, tu), texV(icon, tv),
            texU(icon, tu + x1 - x0), texV(icon, tv + y1 - y0), 0);
    }

    /** Кусок текстуры с тех же пикселей, что и на иконке. */
    private void atlasRect(TextureAtlasSprite icon, int face, int x0, int y0, int x1, int y1) {
        rect(face, x0, y0, x1, y1, texU(icon, x0), texV(icon, y0), texU(icon, x1), texV(icon, y1), 0);
    }

    /** Прямоугольник в пикселях иконки (x вправо, y вниз). layer > 0 — чуть ближе к зрителю, чтобы не мерцать. face 0 — лицевая сторона, 1 — задняя (в инвентаре только лицевая). */
    private void rect(int face, double x0, double y0, double x1, double y1,
                      double u0, double v0, double u1, double v1, int layer) {
        if (recording) surfaces.add(new double[] { face, x0, y0, x1, y1, layer });
        float nz = face == 0 ? 1f : -1f;
        float z = face == 0 ? FRONT + layer * 0.001f : BACK - layer * 0.001f;
        vertex(x0, y1, z, u0, v1, 0f, 0f, nz);
        vertex(x1, y1, z, u1, v1, 0f, 0f, nz);
        vertex(x1, y0, z, u1, v0, 0f, 0f, nz);
        vertex(x0, y0, z, u0, v0, 0f, 0f, nz);
    }

    /** Пиксели иконки -> куб 0..1: x вправо, y вверх. */
    private void vertex(double x, double y, float z, double u, double v, float nx, float ny, float nz) {
        //? if >=26.3 {
        /*// Узор блеска - по пикселям иконки, как у отдельного слоя блеска до 26.3 (см. glint).
        double k = GLINT_SPAN / ICON;
        out.addVertex(pose, (float) (x / ICON), (float) (1 - y / ICON), z)
            .setColor(colorR, colorG, colorB, 1f)
            .setUv((float) u, (float) v)
            .setUv3((float) (x * k), (float) (y * k))
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
        *///?} else if >=1.21 {
        out.addVertex(pose, (float) (x / ICON), (float) (1 - y / ICON), z)
            .setColor(colorR, colorG, colorB, 1f)
            .setUv((float) u, (float) v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
        //?} else {
        /*out.vertex(pose.pose(), (float) (x / ICON), (float) (1 - y / ICON), z)
            .color(colorR, colorG, colorB, 1f)
            .uv((float) u, (float) v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(pose.normal(), nx, ny, nz)
            .endVertex();
        *///?}
    }

    private void color(float r, float g, float b) {
        colorR = r;
        colorG = g;
        colorB = b;
    }

    private static double texU(TextureAtlasSprite sprite, double px) {
        return FrameSkin.u(sprite, px);
    }

    private static double texV(TextureAtlasSprite sprite, double px) {
        return FrameSkin.v(sprite, px);
    }
}
