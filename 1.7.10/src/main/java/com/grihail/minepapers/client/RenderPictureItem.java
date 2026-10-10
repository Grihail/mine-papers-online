package com.grihail.minepapers.client;

import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.block.BlockPictureFrame;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;

/**
 * Иконка настроенной рамки: рамка из выбранной текстуры по пропорциям холста и уменьшенная картинка внутри
 * (на сетке 16x16, как у обычных предметов). В руке и на земле — пластинка толщиной в пиксель.
 * Ненастроенные рамки рисуются обычной иконкой, без этого класса.
 */
public class RenderPictureItem implements IItemRenderer {

    private static final int ICON = 16;
    /** Ширина рамки на иконке в пикселях. */
    private static final int RING = 2;
    private static final float THICKNESS = 1f / 16f;

    private boolean flat;
    private Tessellator t;

    @Override
    public boolean handleRenderType(ItemStack stack, ItemRenderType type) {
        if (type != ItemRenderType.INVENTORY && type != ItemRenderType.ENTITY
            && type != ItemRenderType.EQUIPPED && type != ItemRenderType.EQUIPPED_FIRST_PERSON) return false;
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(BlockPictureFrame.ITEM_TAG);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        return type == ItemRenderType.ENTITY
            && (helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        PictureFrame s = new PictureFrame();
        s.readSettings(stack.getTagCompound().getCompoundTag(BlockPictureFrame.ITEM_TAG));

        // Холст вписывается в иконку с сохранением пропорций, рамка — вокруг него (у граффити рамки нет).
        int ring = s.mode == PictureFrame.MODE_DECAL ? 0 : RING;
        int inner = ICON - 2 * ring;
        double w = s.getWidthBlocks(), h = s.getHeightBlocks();
        int iw = w >= h ? inner : Math.max(1, (int) Math.round(inner * w / h));
        int ih = h >= w ? inner : Math.max(1, (int) Math.round(inner * h / w));
        int x0 = (ICON - iw - 2 * ring) / 2, y0 = (ICON - ih - 2 * ring) / 2;
        int x1 = x0 + iw + 2 * ring, y1 = y0 + ih + 2 * ring;
        int cx0 = x0 + ring, cy0 = y0 + ring, cx1 = x1 - ring, cy1 = y1 - ring;

        String key = s.getSourceKey();
        PictureManager.Variant thumb = key == null ? null
            : PictureManager.thumbnail(key, ImageStylizer.Layout.icon(s, inner));

        flat = type == ItemRenderType.INVENTORY;
        t = Tessellator.instance;
        Minecraft mc = Minecraft.getMinecraft();
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_CULL_FACE);
        if (type == ItemRenderType.ENTITY) GL11.glTranslatef(-0.5f, -0.25f, THICKNESS / 2);

        // 1. Рамка (и торцы пластинки)
        if (ring > 0) {
            FrameSkin skin = FrameSkin.of(s.borderItem, s.borderMeta);
            mc.getTextureManager().bindTexture(skin.atlas);
            t.startDrawingQuads();
            t.setColorOpaque_F(skin.r, skin.g, skin.b);
            for (int face = 0; face < faces(); face++) ring(skin.icon, face, x0, y0, x1, y1, iw, ih);
            if (!flat) sides(skin.icon, x0, y0, x1, y1);
            t.draw();
        }

        // 2. Картинка. В рамке — на фоне холста, как в мире. У граффити холста нет: прозрачный фон картинки
        //    остаётся прозрачным, а в руке и на земле она выдавливается по пикселям, как обычные предметы.
        if (thumb != null) {
            if (ring > 0) {
                mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
                t.startDrawingQuads();
                t.setColorOpaque_F(1f, 1f, 1f);
                IIcon canvas = MinePapers.frame.canvasIcon;
                for (int face = 0; face < faces(); face++) atlasRect(canvas, face, cx0, cy0, cx1, cy1);
                t.draw();
            }

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, thumb.getTextureId());
            double a = cx0 + thumb.x0 / w * iw, b = cx0 + thumb.x1 / w * iw;
            double c = cy0 + (h - thumb.y1) / h * ih, d = cy0 + (h - thumb.y0) / h * ih;
            if (ring == 0 && !flat) {
                extruded(a, c, b, d, 0, 0, 1, 1, thumb.width, thumb.height);
            } else {
                t.startDrawingQuads();
                t.setColorOpaque_F(1f, 1f, 1f);
                for (int face = 0; face < faces(); face++) rect(face, a, c, b, d, 0, 0, 1, 1, 1);
                t.draw();
            }
            if (!blend) GL11.glDisable(GL11.GL_BLEND);
        } else {
            // Пока картинки нет — пейзаж с обычной иконки (у граффити — без её деревянной рамки)
            mc.getTextureManager().bindTexture(TextureMap.locationItemsTexture);
            IIcon placeholder = Item.getItemFromBlock(MinePapers.frame).getIconFromDamage(0);
            if (ring > 0) {
                t.startDrawingQuads();
                t.setColorOpaque_F(1f, 1f, 1f);
                for (int face = 0; face < faces(); face++) atlasRect(placeholder, face, cx0, cy0, cx1, cy1);
                t.draw();
            } else {
                double pu0 = placeholder.getInterpolatedU(RING), pu1 = placeholder.getInterpolatedU(ICON - RING);
                double pv0 = placeholder.getInterpolatedV(RING), pv1 = placeholder.getInterpolatedV(ICON - RING);
                if (flat) {
                    t.startDrawingQuads();
                    t.setColorOpaque_F(1f, 1f, 1f);
                    rect(0, cx0, cy0, cx1, cy1, pu0, pv0, pu1, pv1, 0);
                    t.draw();
                } else {
                    extruded(cx0, cy0, cx1, cy1, pu0, pv0, pu1, pv1, ICON - 2 * RING, ICON - 2 * RING);
                }
            }
        }

        GL11.glPopMatrix();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        mc.getTextureManager().bindTexture(TextureMap.locationItemsTexture);
    }

    /**
     * Объёмная пластинка по пикселям текстуры (прозрачные пиксели — дыры, торцы повторяют форму), как у ванильных
     * предметов. Прямоугольник (x0, y0)–(x1, y1) — в пикселях иконки, (u0, v0)–(u1, v1) — его часть текстуры.
     */
    private void extruded(double x0, double y0, double x1, double y1,
                          double u0, double v0, double u1, double v1, int texW, int texH) {
        GL11.glPushMatrix();
        GL11.glTranslated(1 - x1 / ICON, 1 - y1 / ICON, 0);
        GL11.glScaled((x1 - x0) / ICON, (y1 - y0) / ICON, 1);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        ItemRenderer.renderItemIn2D(t, (float) u1, (float) v0, (float) u0, (float) v1, texW, texH, THICKNESS);
        GL11.glPopMatrix();
    }

    /** В инвентаре — одна лицевая сторона, в мире — лицевая и задняя. */
    private int faces() {
        return flat ? 1 : 2;
    }

    /** Рамка из 9 частей: углы 2x2 из углов текстуры, стороны — из её краёв. */
    private void ring(IIcon icon, int face, int x0, int y0, int x1, int y1, int iw, int ih) {
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
    private void sides(IIcon icon, int x0, int y0, int x1, int y1) {
        double u0 = icon.getInterpolatedU(x0), u1 = icon.getInterpolatedU(x1);
        double v0 = icon.getInterpolatedV(y0), v1 = icon.getInterpolatedV(y1);
        double top0 = icon.getInterpolatedV(0), top1 = icon.getInterpolatedV(1);
        double bot0 = icon.getInterpolatedV(ICON - 1), bot1 = icon.getInterpolatedV(ICON);
        double left0 = icon.getInterpolatedU(0), left1 = icon.getInterpolatedU(1);
        double right0 = icon.getInterpolatedU(ICON - 1), right1 = icon.getInterpolatedU(ICON);
        // y иконки идёт вниз, а в мире — вверх; x зеркальный (см. vertex)
        t.setNormal(0f, 1f, 0f);
        sideH(x0, x1, y0, u0, u1, top0, top1);
        t.setNormal(0f, -1f, 0f);
        sideH(x0, x1, y1, u0, u1, bot0, bot1);
        t.setNormal(1f, 0f, 0f);
        sideV(x0, y0, y1, v0, v1, left0, left1);
        t.setNormal(-1f, 0f, 0f);
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
    private void texRect(IIcon icon, int face, int x0, int y0, int x1, int y1, int tu, int tv) {
        if (x1 <= x0 || y1 <= y0) return;
        rect(face, x0, y0, x1, y1, icon.getInterpolatedU(tu), icon.getInterpolatedV(tv),
            icon.getInterpolatedU(tu + x1 - x0), icon.getInterpolatedV(tv + y1 - y0), 0);
    }

    /** Кусок текстуры с тех же пикселей, что и на иконке. */
    private void atlasRect(IIcon icon, int face, int x0, int y0, int x1, int y1) {
        rect(face, x0, y0, x1, y1, icon.getInterpolatedU(x0), icon.getInterpolatedV(y0),
            icon.getInterpolatedU(x1), icon.getInterpolatedV(y1), 0);
    }

    /**
     * Прямоугольник в пикселях иконки (x вправо, y вниз). layer > 0 — чуть ближе к зрителю, чтобы не мерцать.
     * face 0 — лицевая сторона, 1 — задняя (в инвентаре только лицевая).
     */
    private void rect(int face, double x0, double y0, double x1, double y1,
                      double u0, double v0, double u1, double v1, int layer) {
        float z;
        if (flat) {
            z = layer * 0.01f;
        } else if (face == 0) {
            t.setNormal(0f, 0f, 1f);
            z = layer * 0.001f;
        } else {
            t.setNormal(0f, 0f, -1f);
            z = -THICKNESS - layer * 0.001f;
        }
        vertex(x0, y0, z, u0, v0);
        vertex(x1, y0, z, u1, v0);
        vertex(x1, y1, z, u1, v1);
        vertex(x0, y1, z, u0, v1);
    }

    /** В инвентаре — экранные пиксели; в мире — как у ванильных предметов: 0..1, зеркально по x, y вверх. */
    private void vertex(double x, double y, float z, double u, double v) {
        if (flat) {
            t.addVertexWithUV(x, y, z, u, v);
        } else {
            t.addVertexWithUV(1 - x / ICON, 1 - y / ICON, z, u, v);
        }
    }
}
