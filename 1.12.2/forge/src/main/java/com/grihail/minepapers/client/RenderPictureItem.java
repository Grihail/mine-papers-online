package com.grihail.minepapers.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.vecmath.Matrix4f;

import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

/** Иконка настроенной рамки: рамка из выбранной текстуры по пропорциям холста и уменьшенная картинка внутри (на сетке 16x16, как у обычных предметов). В руке и на земле — пластинка толщиной в пиксель. Ненастроенные рамки рисуются обычной моделью, без этого класса: настроенным модель подменяет ClientProxy (Model — встроенный рендер, он и сообщает, где рисуется предмет). */
public class RenderPictureItem extends TileEntityItemStackRenderer {

    private static final int ICON = IconLayout.ICON, RING = IconLayout.RING;
    private static final float THICKNESS = 1f / 16f;
    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");
    /** Шахматка под картинкой без холста в инвентаре: прозрачный фон картинки виден как сетка, и картина не теряется в слоте. Клетка — пиксель иконки. */
    private static final ResourceLocation ALPHA_GRID = new ResourceLocation(MinePapers.MODID, "textures/misc/alpha_grid.png");
    /** Значок "18+" на скрытой картине NSFW (как на холсте в мире). */
    private static final ResourceLocation NSFW_MARK = new ResourceLocation(MinePapers.MODID, "textures/misc/nsfw.png");
    /** Обычная иконка предмета — пейзаж, пока картинки нет. */
    private static final ResourceLocation PLACEHOLDER = new ResourceLocation(MinePapers.MODID, "items/picture_frame");

    /** Где рисуется предмет: TEISR этого не знает, его перед отрисовкой запоминает модель (Model.handlePerspective). */
    private static TransformType transform = TransformType.NONE;

    private boolean flat;
    private final Tessellator t = Tessellator.getInstance();
    private BufferBuilder b;
    private float r = 1f, g = 1f, bl = 1f, a = 1f;

    @Override
    public void renderByItem(ItemStack stack) {
        NBTTagCompound settings = ItemPictureFrame.settings(stack);
        if (settings == null) return;
        PictureFrame s = new PictureFrame();
        s.readSettings(settings);
        IconLayout l = new IconLayout(s);
        boolean canvasBlock = l.canvasBlock;
        int ring = l.ring, iw = l.iw, ih = l.ih, x0 = l.x0, y0 = l.y0, x1 = l.x1, y1 = l.y1;
        int cx0 = l.cx0, cy0 = l.cy0, cx1 = l.cx1, cy1 = l.cy1;
        PictureManager.Variant thumb = l.thumbnail();

        flat = transform == TransformType.GUI;
        b = t.getBuffer();
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        surfaces.clear();

        // 1. Рамка (и торцы пластинки)
        if (ring > 0) {
            FrameSkin skin = FrameSkin.border(s.border);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            begin(skin.r, skin.g, skin.b, 1f);
            for (int face = 0; face < faces(); face++) ring(skin.icon, face, x0, y0, x1, y1, iw, ih);
            if (!flat) sides(skin.icon, x0, y0, x1, y1);
            t.draw();
        } else if (canvasBlock && !flat) {
            // Холст без рамки: торцы — из самого холста.
            FrameSkin canvas = FrameSkin.canvas(s.canvas);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            begin(canvas.r, canvas.g, canvas.b, 1f);
            sides(canvas.icon, x0, y0, x1, y1);
            t.draw();
        }

        // 2. Картинка. На холсте из блока — на его фоне, как в мире. Без холста и у пластины прозрачный фон картинки остаётся прозрачным (в инвентаре под ним шахматка), а в руке и на земле она выдавливается по пикселям, как обычные предметы.
        if (thumb != null) {
            if (canvasBlock) {
                FrameSkin canvas = FrameSkin.canvas(s.canvas);
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                begin(canvas.r, canvas.g, canvas.b, 1f);
                for (int face = 0; face < faces(); face++) atlasRect(canvas.icon, face, cx0, cy0, cx1, cy1);
                t.draw();
            }
            if (!canvasBlock && flat) {
                mc.getTextureManager().bindTexture(ALPHA_GRID);
                begin(1f, 1f, 1f, 1f);
                rect(0, cx0, cy0, cx1, cy1, cx0 / (double) ICON, cy0 / (double) ICON, cx1 / (double) ICON, cy1 / (double) ICON, 0);
                t.draw();
            }
            GlStateManager.bindTexture((Integer) thumb.getTexture());
            double[] p = l.picture(thumb);
            if (p != null) {
                begin(1f, 1f, 1f, 1f);
                if (ring == 0 && !canvasBlock && !flat) {
                    extruded(p[0], p[1], p[2], p[3], 0, 0, 1, 1, thumb.width, thumb.height);
                    surface(p[0], p[1], p[2], p[3], 0);
                } else {
                    for (int face = 0; face < faces(); face++) rect(face, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], 1);
                }
                t.draw();
            }
        } else {
            // Пока картинки нет — пейзаж с обычной иконки (без рамки — без её деревянной рамки)
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            TextureAtlasSprite placeholder = mc.getTextureMapBlocks().getAtlasSprite(PLACEHOLDER.toString());
            begin(1f, 1f, 1f, 1f);
            if (ring > 0 || canvasBlock) {
                for (int face = 0; face < faces(); face++) atlasRect(placeholder, face, cx0, cy0, cx1, cy1);
            } else {
                double pu0 = placeholder.getInterpolatedU(RING), pu1 = placeholder.getInterpolatedU(ICON - RING);
                double pv0 = placeholder.getInterpolatedV(RING), pv1 = placeholder.getInterpolatedV(ICON - RING);
                if (flat) {
                    rect(0, cx0, cy0, cx1, cy1, pu0, pv0, pu1, pv1, 0);
                } else {
                    extruded(cx0, cy0, cx1, cy1, pu0, pv0, pu1, pv1, ICON - 2 * RING, ICON - 2 * RING);
                    surface(cx0, cy0, cx1, cy1, 0);
                }
            }
            t.draw();
        }

        // 3. Скрытая картина NSFW: поверх заглушки — значок "18+" по центру холста, квадратом по меньшей стороне.
        if (l.hidden) {
            double[] m = l.nsfwMark();
            mc.getTextureManager().bindTexture(NSFW_MARK);
            begin(1f, 1f, 1f, 1f);
            for (int face = 0; face < faces(); face++) rect(face, m[0], m[1], m[2], m[3], 0, 0, 1, 1, 2);
            t.draw();
        }
        if (stack.hasEffect()) glint(mc);

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    /** Новая порция вершин с цветом (r, g, b, a). */
    private void begin(float r, float g, float bl, float a) {
        this.r = r;
        this.g = g;
        this.bl = bl;
        this.a = a;
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
    }

    /** Все нарисованные лицевые прямоугольники {сторона, x0, y0, x1, y1, слой}: блеск повторяет их один в один. Проверка глубины у блеска — "равно", и одинаковая глубина гарантирована только у тех же самых вершин; один большой прямоугольник поверх рамки из девяти частей мерцал бы. */
    private final List<double[]> surfaces = new ArrayList<double[]>();
    /** Записывать прямоугольники (выключено, пока рисуется сам блеск). */
    private boolean recording = true;

    /** Плоскость, нарисованная не через rect() (объёмная пластинка, extruded), — обе её стороны. */
    private void surface(double x0, double y0, double x1, double y1, int layer) {
        for (int face = 0; face < faces(); face++) surfaces.add(new double[] { face, x0, y0, x1, y1, layer });
    }

    /** Блеск зачарования (заприваченная или подписанная картина). Встроенный рендер предмета ванильного блеска не получает (RenderItem.renderEffect рисует его только у обычных моделей), поэтому рисуем его так же: два бегущих слоя текстуры блеска поверх уже нарисованных плоскостей (глубина "равно"), с одними координатами по всей иконке. */
    private void glint(Minecraft mc) {
        mc.getTextureManager().bindTexture(GLINT);
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.depthMask(false);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_COLOR, GlStateManager.DestFactor.ONE);
        float k = 0.76f;
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        for (int pass = 0; pass < 2; pass++) {
            long period = pass == 0 ? 3000L : 4873L;
            float shift = (Minecraft.getSystemTime() % period) / (float) period * 8f;
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.125f, 0.125f, 0.125f);
            GlStateManager.translate(pass == 0 ? shift : -shift, 0f, 0f);
            GlStateManager.rotate(pass == 0 ? -50f : 10f, 0f, 0f, 1f);
            begin(0.5f * k, 0.25f * k, 0.8f * k, 1f);
            recording = false;
            for (double[] q : surfaces) {
                rect((int) q[0], q[1], q[2], q[3], q[4], q[1] / ICON, q[2] / ICON, q[3] / ICON, q[4] / ICON, (int) q[5]);
            }
            recording = true;
            t.draw();
            GlStateManager.popMatrix();
        }
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
    }

    /** Объёмная пластинка по пикселям текстуры (прозрачные пиксели — дыры, торцы повторяют форму), как у ванильных предметов: лицевая и задняя стороны и полоски по каждому столбцу и ряду пикселей (прозрачные отсекает тест альфы). Прямоугольник (x0, y0)–(x1, y1) — в пикселях иконки, (u0, v0)–(u1, v1) — его часть текстуры размером texW x texH пикселей. */
    private void extruded(double x0, double y0, double x1, double y1,
                          double u0, double v0, double u1, double v1, int texW, int texH) {
        float front = 0, back = -THICKNESS;
        vertex(x0, y0, front, u0, v0);
        vertex(x1, y0, front, u1, v0);
        vertex(x1, y1, front, u1, v1);
        vertex(x0, y1, front, u0, v1);
        vertex(x0, y1, back, u0, v1);
        vertex(x1, y1, back, u1, v1);
        vertex(x1, y0, back, u1, v0);
        vertex(x0, y0, back, u0, v0);
        double pw = (x1 - x0) / texW, ph = (y1 - y0) / texH;
        for (int i = 0; i < texW; i++) {
            double u = u0 + (u1 - u0) * (i + 0.5) / texW;
            for (double x : new double[] { x0 + i * pw, x0 + (i + 1) * pw }) {
                vertex(x, y0, front, u, v0);
                vertex(x, y1, front, u, v1);
                vertex(x, y1, back, u, v1);
                vertex(x, y0, back, u, v0);
            }
        }
        for (int j = 0; j < texH; j++) {
            double v = v0 + (v1 - v0) * (j + 0.5) / texH;
            for (double y : new double[] { y0 + j * ph, y0 + (j + 1) * ph }) {
                vertex(x0, y, front, u0, v);
                vertex(x1, y, front, u1, v);
                vertex(x1, y, back, u1, v);
                vertex(x0, y, back, u0, v);
            }
        }
    }

    /** В инвентаре — одна лицевая сторона, в мире — лицевая и задняя. */
    private int faces() {
        return flat ? 1 : 2;
    }

    /** Рамка из 9 частей: углы 2x2 из углов текстуры, стороны — из её краёв. */
    private void ring(TextureAtlasSprite icon, int face, int x0, int y0, int x1, int y1, int iw, int ih) {
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
        double u0 = icon.getInterpolatedU(x0), u1 = icon.getInterpolatedU(x1);
        double v0 = icon.getInterpolatedV(y0), v1 = icon.getInterpolatedV(y1);
        double top0 = icon.getInterpolatedV(0), top1 = icon.getInterpolatedV(1);
        double bot0 = icon.getInterpolatedV(ICON - 1), bot1 = icon.getInterpolatedV(ICON);
        double left0 = icon.getInterpolatedU(0), left1 = icon.getInterpolatedU(1);
        double right0 = icon.getInterpolatedU(ICON - 1), right1 = icon.getInterpolatedU(ICON);
        // y иконки идёт вниз, а в мире — вверх (см. vertex)
        sideH(x0, x1, y0, u0, u1, top0, top1);
        sideH(x0, x1, y1, u0, u1, bot0, bot1);
        sideV(x0, y0, y1, v0, v1, left0, left1);
        sideV(x1, y0, y1, v0, v1, right0, right1);
    }

    /** Горизонтальный торец на высоте y: u вдоль ребра, v — поперёк (по толщине). */
    private void sideH(int x0, int x1, int y, double u0, double u1, double va, double vb) {
        vertex(x0, y, 0, u0, va);
        vertex(x1, y, 0, u1, va);
        vertex(x1, y, -THICKNESS, u1, vb);
        vertex(x0, y, -THICKNESS, u0, vb);
    }

    /** Вертикальный торец на x: v вдоль ребра, u — поперёк. */
    private void sideV(int x, int y0, int y1, double v0, double v1, double ua, double ub) {
        vertex(x, y0, 0, ua, v0);
        vertex(x, y1, 0, ua, v1);
        vertex(x, y1, -THICKNESS, ub, v1);
        vertex(x, y0, -THICKNESS, ub, v0);
    }

    /** Кусок текстуры того же размера, что и на иконке, начиная с пикселя (tu, tv). */
    private void texRect(TextureAtlasSprite icon, int face, int x0, int y0, int x1, int y1, int tu, int tv) {
        if (x1 <= x0 || y1 <= y0) return;
        rect(face, x0, y0, x1, y1, icon.getInterpolatedU(tu), icon.getInterpolatedV(tv),
            icon.getInterpolatedU(tu + x1 - x0), icon.getInterpolatedV(tv + y1 - y0), 0);
    }

    /** Кусок текстуры с тех же пикселей, что и на иконке. */
    private void atlasRect(TextureAtlasSprite icon, int face, int x0, int y0, int x1, int y1) {
        rect(face, x0, y0, x1, y1, icon.getInterpolatedU(x0), icon.getInterpolatedV(y0),
            icon.getInterpolatedU(x1), icon.getInterpolatedV(y1), 0);
    }

    /** Прямоугольник в пикселях иконки (x вправо, y вниз). layer > 0 — чуть ближе к зрителю, чтобы не мерцать. face 0 — лицевая сторона, 1 — задняя (в инвентаре только лицевая). */
    private void rect(int face, double x0, double y0, double x1, double y1,
                      double u0, double v0, double u1, double v1, int layer) {
        if (recording) surfaces.add(new double[] { face, x0, y0, x1, y1, layer });
        float z;
        if (flat) {
            z = layer * 0.001f;
        } else if (face == 0) {
            z = layer * 0.001f;
        } else {
            z = -THICKNESS - layer * 0.001f;
        }
        vertex(x0, y0, z, u0, v0);
        vertex(x1, y0, z, u1, v0);
        vertex(x1, y1, z, u1, v1);
        vertex(x0, y1, z, u0, v1);
    }

    /** Пиксели иконки — в пространство модели предмета 0..1 (как у item/generated): x вправо, y вверх, лицевая сторона — на z = 8.5/16, к зрителю в инвентаре. */
    private void vertex(double x, double y, float z, double u, double v) {
        b.pos(x / ICON, 1 - y / ICON, 0.5 + THICKNESS / 2 + z).tex(u, v).color(r, g, bl, a).endVertex();
    }

    /** Модель настроенной картины: встроенный рендер (рисует RenderPictureItem) с положением в руке, на земле и в рамке, как у обычной иконки (plain). */
    public static final class Model implements IBakedModel {

        private final IBakedModel plain;

        public Model(IBakedModel plain) {
            this.plain = plain;
        }

        @Override
        public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            return Collections.emptyList();
        }

        @Override
        public boolean isAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return true;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return plain.getParticleTexture();
        }

        @Override
        @SuppressWarnings("deprecation")
        public ItemCameraTransforms getItemCameraTransforms() {
            return plain.getItemCameraTransforms();
        }

        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(TransformType type) {
            transform = type;
            return Pair.of(this, plain.handlePerspective(type).getRight());
        }
    }
}
