package com.grihail.minepapers.client;

import java.util.ArrayList;
import java.util.List;

import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Иконка настроенной рамки: рамка из выбранной текстуры по пропорциям холста и уменьшенная картинка внутри (на сетке 16x16, как у обычных предметов). В руке и на земле — пластинка толщиной в пиксель. Ненастроенные рамки рисуются обычной моделью предмета (см. models/item/picture_frame.json). Рисует в кубе 0..1, как модели предметов item/generated: пластинка между z = 7.5/16 и 8.5/16. */
public class RenderPictureItem extends BlockEntityWithoutLevelRenderer {

    private static final int ICON = 16;
    /** Ширина рамки на иконке в пикселях. */
    private static final int RING = 2;
    private static final float THICKNESS = 1f / 16f;
    private static final float FRONT = 0.5f + THICKNESS / 2, BACK = 0.5f - THICKNESS / 2;
    /** Шахматка под картинкой без холста в инвентаре: прозрачный фон картинки виден как сетка, и картина не теряется в слоте. Клетка — пиксель иконки. */
    private static final ResourceLocation ALPHA_GRID = MinePapers.id("textures/misc/alpha_grid.png");
    /** Значок "18+" на скрытой картине NSFW (как на холсте в мире). */
    private static final ResourceLocation NSFW_MARK = MinePapers.id("textures/misc/nsfw.png");

    private boolean flat;
    private PoseStack.Pose pose;
    private VertexConsumer out;
    private int light;
    private float colorR = 1f, colorG = 1f, colorB = 1f;

    public RenderPictureItem() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int packedLight, int packedOverlay) {
        CompoundTag settings = ItemPictureFrame.settings(stack);
        if (settings == null) return;
        PictureFrame s = new PictureFrame();
        s.readSettings(settings);

        // Холст вписывается в иконку с сохранением пропорций, рамка — вокруг него (если она есть).
        boolean plate = s.getCanvasKind() == PictureFrame.CANVAS_PICTURE;
        boolean canvasBlock = s.getCanvasKind() == PictureFrame.CANVAS_BLOCK;
        int ring = s.getFrameShape() != PictureFrame.FRAME_NONE ? RING : 0;
        int inner = ICON - 2 * ring;
        double w = s.getWidthBlocks(), h = s.getHeightBlocks();
        int iw = w >= h ? inner : Math.max(1, (int) Math.round(inner * w / h));
        int ih = h >= w ? inner : Math.max(1, (int) Math.round(inner * h / w));
        int x0 = (ICON - iw - 2 * ring) / 2, y0 = (ICON - ih - 2 * ring) / 2;
        int x1 = x0 + iw + 2 * ring, y1 = y0 + ih + 2 * ring;
        int cx0 = x0 + ring, cy0 = y0 + ring, cx1 = x1 - ring, cy1 = y1 - ring;

        String key = ModConfig.hides(s.nsfw) ? null : s.getSourceKey(); // NSFW скрыт - обычная иконка
        PictureManager.Variant thumb = key == null ? null
            : PictureManager.thumbnail(key, ImageStylizer.Layout.icon(s, inner));

        flat = context == ItemDisplayContext.GUI;
        pose = poseStack.last();
        light = packedLight;
        RenderType atlas = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
        surfaces.clear();

        // 1. Рамка (и торцы пластинки)
        if (ring > 0) {
            FrameSkin skin = FrameSkin.border(s.border);
            out = buffers.getBuffer(atlas);
            color(skin.r, skin.g, skin.b);
            for (int face = 0; face < faces(); face++) ring(skin.sprite, face, x0, y0, x1, y1);
            if (!flat) sides(skin.sprite, x0, y0, x1, y1);
        } else if (canvasBlock && !flat) {
            // Холст без рамки: торцы — из самого холста.
            FrameSkin canvas = FrameSkin.canvas(s.canvas);
            out = buffers.getBuffer(atlas);
            color(canvas.r, canvas.g, canvas.b);
            sides(canvas.sprite, x0, y0, x1, y1);
        }

        // 2. Картинка. На холсте из блока — на его фоне, как в мире. Без холста прозрачный фон картинки остаётся прозрачным (в инвентаре под ним шахматка), а в руке и на земле она выдавливается по пикселям, как обычные предметы. Пластина — цельная, торцы только по краю.
        color(1f, 1f, 1f);
        if (thumb != null) {
            if (canvasBlock) {
                out = buffers.getBuffer(atlas);
                FrameSkin canvas = FrameSkin.canvas(s.canvas);
                color(canvas.r, canvas.g, canvas.b);
                for (int face = 0; face < faces(); face++) atlasRect(canvas.sprite, face, cx0, cy0, cx1, cy1);
                color(1f, 1f, 1f);
            } else if (flat) {
                out = buffers.getBuffer(RenderType.entityTranslucent(ALPHA_GRID));
                rect(0, cx0, cy0, cx1, cy1, cx0 / (double) ICON, cy0 / (double) ICON, cx1 / (double) ICON, cy1 / (double) ICON, 0);
            }
            out = buffers.getBuffer(RenderType.entityTranslucent((ResourceLocation) thumb.getTexture()));
            // Настоящая рамка в мире заходит на холст: на иконке её кольцо и есть эта часть, внутри — только то, что видно из-под неё.
            double ov = s.getFrameShape() == PictureFrame.FRAME_MOLDING ? Math.min(s.getMoldingBlocks(), Math.min(w, h) / 2) : 0;
            if (w - 2 * ov < 1e-6 || h - 2 * ov < 1e-6) ov = 0;
            double ww = w - 2 * ov, wh = h - 2 * ov;
            double px0 = Math.max(thumb.x0, ov), px1 = Math.min(thumb.x1, w - ov);
            double py0 = Math.max(thumb.y0, ov), py1 = Math.min(thumb.y1, h - ov);
            if (px1 - px0 > 1e-6 && py1 - py0 > 1e-6) {
                double a = cx0 + (px0 - ov) / ww * iw, b = cx0 + (px1 - ov) / ww * iw;
                double c = cy0 + (h - ov - py1) / wh * ih, d = cy0 + (h - ov - py0) / wh * ih;
                double tu0 = (px0 - thumb.x0) / (thumb.x1 - thumb.x0), tu1 = (px1 - thumb.x0) / (thumb.x1 - thumb.x0);
                double tv0 = (thumb.y1 - py1) / (thumb.y1 - thumb.y0), tv1 = (thumb.y1 - py0) / (thumb.y1 - thumb.y0);
                if (ring == 0 && !canvasBlock && !flat && thumb.pixels != null) {
                    int[] px = thumb.pixels;
                    int tw = thumb.width;
                    extruded(a, c, b, d, 0, 0, 1, 1, thumb.width, thumb.height,
                        (x, y) -> plate || (px[y * tw + x] >>> 24) != 0);
                } else {
                    for (int face = 0; face < faces(); face++) rect(face, a, c, b, d, tu0, tv0, tu1, tv1, 1);
                }
            }
        } else {
            // Пока картинки нет — пейзаж с обычной иконки (без рамки — без её деревянной рамки)
            TextureAtlasSprite placeholder = FrameSkin.sprite(FrameSkin.ICON);
            out = buffers.getBuffer(atlas);
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
        // 3. Картина NSFW, скрытая настройкой hideNsfw: поверх заглушки — значок "18+" по центру холста, квадратом по меньшей стороне.
        if (ModConfig.hides(s.nsfw)) {
            int size = Math.min(cx1 - cx0, cy1 - cy0);
            double mx = (cx0 + cx1 - size) / 2.0, my = (cy0 + cy1 - size) / 2.0;
            out = buffers.getBuffer(RenderType.entityCutoutNoCull(NSFW_MARK));
            color(1f, 1f, 1f);
            for (int face = 0; face < faces(); face++) rect(face, mx, my, mx + size, my + size, 0, 0, 1, 1, 2);
        }
        if (stack.hasFoil()) glint(buffers);
        out = null;
        pose = null;
    }

    /** Все нарисованные лицевые прямоугольники {сторона, x0, y0, x1, y1, слой}: блеск повторяет их один в один. Проверка глубины у блеска — "равно", и одинаковая глубина гарантирована только у тех же самых вершин; один большой прямоугольник поверх рамки из девяти частей мерцал бы. */
    private final List<double[]> surfaces = new ArrayList<double[]>();
    /** Записывать прямоугольники (выключено, пока рисуется сам блеск). */
    private boolean recording = true;


    /** Блеск зачарования (заприваченная картина) — как у щита и трезубца: свой слой поверх уже нарисованных плоскостей (проверка глубины "равно"), с одними координатами текстуры по всей иконке. Иначе каждая часть (атлас блоков, картинка) получила бы свой масштаб узора. */
    private void glint(MultiBufferSource buffers) {
        out = buffers.getBuffer(RenderType.entityGlintDirect());
        recording = false;
        for (double[] r : surfaces) {
            rect((int) r[0], r[1], r[2], r[3], r[4], r[1] / ICON, r[2] / ICON, r[3] / ICON, r[4] / ICON, (int) r[5]);
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
        out.addVertex(pose, (float) (x / ICON), (float) (1 - y / ICON), z)
            .setColor(colorR, colorG, colorB, 1f)
            .setUv((float) u, (float) v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    private void color(float r, float g, float b) {
        colorR = r;
        colorG = g;
        colorB = b;
    }

    private static double texU(TextureAtlasSprite sprite, double px) {
        return sprite.getU((float) (px / 16));
    }

    private static double texV(TextureAtlasSprite sprite, double px) {
        return sprite.getV((float) (px / 16));
    }
}
