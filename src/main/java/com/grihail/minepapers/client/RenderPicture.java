package com.grihail.minepapers.client;

import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.tile.FrameGeometry;
import com.grihail.minepapers.tile.TilePictureFrame;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * Рисует картину целиком: холст, рамку (по выбранному виду) и саму картинку.
 * Всё строится в координатах стены (u — вправо, v — вверх, d — от стены), см. FrameGeometry.
 */
public class RenderPicture extends TileEntitySpecialRenderer {

    private static final double PX = TilePictureFrame.PIXEL;
    /** Плоскость граффити — почти вплотную к стене. */
    private static final double DECAL_DEPTH = 0.003;
    /** Длина одного повтора текстуры рамки вдоль ребра: 12 пикселей текстуры = 12/16 блока. */
    private static final double TILE = 12 * PX;

    // Состояние текущей отрисовки (рендер однопоточный)
    private int facing;
    private World world;
    private int bx, by, bz;
    private Tessellator t;
    private float tintR = 1f, tintG = 1f, tintB = 1f;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TilePictureFrame tile = (TilePictureFrame) te;
        PictureManager.Variant shown = currentVariant(tile);
        tile.clientRenderState = shown;
        boolean hasPicture = shown != null && shown.hasTexture();
        if (hasPicture) shown.touch();

        facing = tile.getFacing();
        world = tile.getWorldObj();
        bx = tile.xCoord;
        by = tile.yCoord;
        bz = tile.zCoord;
        t = Tessellator.instance;

        double u0 = tile.getOffsetXBlocks(), v0 = tile.getOffsetYBlocks();
        double u1 = u0 + tile.getWidthBlocks(), v1 = v0 + tile.getHeightBlocks();
        int mode = tile.mode;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // 1. Холст (атлас блоков) и рамка (обычная или с текстурой предмета — она может быть в атласе предметов)
        if (mode != TilePictureFrame.MODE_DECAL || !hasPicture) {
            bindTexture(TextureMap.locationBlocksTexture);
            t.startDrawingQuads();
            setTint(1f, 1f, 1f);
            canvas(MinePapers.frame.canvasIcon, u0, v0, u1, v1, mode == TilePictureFrame.MODE_DECAL ? DECAL_DEPTH : PX);
            if (mode != TilePictureFrame.MODE_DECAL) {
                FrameSkin skin = FrameSkin.of(tile.borderItem, tile.borderMeta);
                if (skin.atlas != TextureMap.locationBlocksTexture) {
                    t.draw();
                    bindTexture(skin.atlas);
                    t.startDrawingQuads();
                }
                setTint(skin.r, skin.g, skin.b);
                if (mode == TilePictureFrame.MODE_PANEL) {
                    edges(skin.icon, u0, v0, u1, v1, 0, PX);
                } else {
                    realisticFrame(skin.icon, u0, v0, u1, v1);
                }
            }
            t.draw();
        }

        // 2. Картинка
        if (hasPicture) {
            boolean decal = mode == TilePictureFrame.MODE_DECAL;
            double d = decal ? DECAL_DEPTH : PX + 0.002;
            if (decal) {
                // Прижимаем к стене без мерцания
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(-1f, -10f);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, shown.getTextureId());
            t.startDrawingQuads();
            picture(u0 + shown.x0, v0 + shown.y0, u0 + shown.x1, v0 + shown.y1, d);
            t.draw();
            if (decal) GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }

        // Возвращаем состояние OpenGL как было: моды вроде Angelica следят за ним и не любят "хвостов".
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPopMatrix();
    }

    // ---------------------------------------------------------------------
    // Части картины

    /**
     * Картинка с плавным освещением, как у ванильных блоков: свет считается в каждой вершине сетки
     * (среднее по соседним клеткам) и плавно перетекает по поверхности — без "ступенек" по блокам.
     */
    private void picture(double u0, double v0, double u1, double v1, double d) {
        lightCache.clear();
        t.setColorOpaque_F(1f, 1f, 1f);
        for (int cu = (int) Math.floor(u0); cu < u1; cu++) {
            for (int cv = (int) Math.floor(v0); cv < v1; cv++) {
                double a = Math.max(u0, cu), b = Math.min(u1, cu + 1);
                double c = Math.max(v0, cv), e = Math.min(v1, cv + 1);
                if (b - a < 1e-6 || e - c < 1e-6) continue;
                double ta = (a - u0) / (u1 - u0), tb = (b - u0) / (u1 - u0);
                double tc = 1 - (c - v0) / (v1 - v0), te = 1 - (e - v0) / (v1 - v0);
                t.setBrightness(smoothLight(a, c, d));
                vertex(a, c, d, ta, tc);
                t.setBrightness(smoothLight(b, c, d));
                vertex(b, c, d, tb, tc);
                t.setBrightness(smoothLight(b, e, d));
                vertex(b, e, d, tb, te);
                t.setBrightness(smoothLight(a, e, d));
                vertex(a, e, d, ta, te);
            }
        }
    }

    private final java.util.Map<Long, Integer> lightCache = new java.util.HashMap<Long, Integer>();

    /** Свет в точке стены: среднее (отдельно небо и источники) по четырём клеткам вокруг, непрозрачные пропускаем. */
    private int smoothLight(double u, double v, double d) {
        long key = Double.doubleToLongBits(u) * 31 + Double.doubleToLongBits(v);
        Integer cached = lightCache.get(key);
        if (cached != null) return cached;

        int sky = 0, block = 0, n = 0;
        for (int i = 0; i < 4; i++) {
            double[] p = FrameGeometry.toBlock(facing, u + ((i & 1) == 0 ? -0.5 : 0.5), v + ((i & 2) == 0 ? -0.5 : 0.5), d + 0.05);
            int x = bx + (int) Math.floor(p[0]), y = by + (int) Math.floor(p[1]), z = bz + (int) Math.floor(p[2]);
            if (world.getBlock(x, y, z).isOpaqueCube()) continue;
            int br = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
            sky += (br >> 16) & 0xFFFF;
            block += br & 0xFFFF;
            n++;
        }
        int result = n == 0 ? world.getLightBrightnessForSkyBlocks(bx, by, bz, 0)
            : (sky / n) << 16 | (block / n);
        lightCache.put(key, result);
        return result;
    }

    /** Холст, текстура повторяется по сетке блоков. */
    private void canvas(IIcon icon, double u0, double v0, double u1, double v1, double d) {
        for (int cu = (int) Math.floor(u0); cu < u1; cu++) {
            for (int cv = (int) Math.floor(v0); cv < v1; cv++) {
                double a = Math.max(u0, cu), b = Math.min(u1, cu + 1);
                double c = Math.max(v0, cv), e = Math.min(v1, cv + 1);
                if (b - a < 1e-6 || e - c < 1e-6) continue;
                light((a + b) / 2, (c + e) / 2, d + 0.05, 1f);
                double ia = icon.getInterpolatedU((a - cu) * 16), ib = icon.getInterpolatedU((b - cu) * 16);
                double ic = icon.getInterpolatedV((cv + 1 - c) * 16), ie = icon.getInterpolatedV((cv + 1 - e) * 16);
                vertex(a, c, d, ia, ic);
                vertex(b, c, d, ib, ic);
                vertex(b, e, d, ib, ie);
                vertex(a, e, d, ia, ie);
            }
        }
    }

    /**
     * Торцы холста от d0 до d1, обшитые деревом рамки (вид "Панель" и внешние торцы реалистичной рамки).
     * Берём древесину из середины текстуры рамки: полоса шириной в толщину торца.
     */
    private void edges(IIcon icon, double u0, double v0, double u1, double v1, double d0, double d1) {
        double rows = (d1 - d0) * 16;
        // верх и низ
        alongU(icon, u0, u1, v1, d0, d1, 6, rows, 0, 0.05, 1.0f);
        alongU(icon, u0, u1, v0, d0, d1, 6, rows, 0, -0.05, 0.55f);
        // лево и право
        alongV(icon, v0, v1, u0, d0, d1, 6, rows, -0.05, 0.8f);
        alongV(icon, v0, v1, u1, d0, d1, 6, rows, 0.05, 0.8f);
    }

    /**
     * Реалистичная рамка: брусья шириной 2 пикселя заходят на холст и выступают на пиксель над картинкой;
     * лицевая сторона — кольцо из текстуры рамки (дерево + золотая кайма), углы — её же углы.
     */
    private void realisticFrame(IIcon icon, double u0, double v0, double u1, double v1) {
        double b = Math.min(2 * PX, Math.min((u1 - u0) / 2, (v1 - v0) / 2));
        double top = 2 * PX; // лицевая плоскость рамки
        double px = b * 16;   // ширина бруса в пикселях текстуры

        // Внешние торцы — на всю толщину
        edges(icon, u0, v0, u1, v1, 0, top);

        // Углы
        front(icon, u0, v1 - b, u0 + b, v1, top, 0, 0, px, px);
        front(icon, u1 - b, v1 - b, u1, v1, top, 16 - px, 0, 16, px);
        front(icon, u0, v0, u0 + b, v0 + b, top, 0, 16 - px, px, 16);
        front(icon, u1 - b, v0, u1, v0 + b, top, 16 - px, 16 - px, 16, 16);

        // Верхний и нижний брусья (между углами), повтор по 12 пикселей
        for (double s = u0 + b; s < u1 - b - 1e-6; s += TILE) {
            double e = Math.min(s + TILE, u1 - b), len = (e - s) * 16;
            front(icon, s, v1 - b, e, v1, top, 2, 0, 2 + len, px);
            front(icon, s, v0, e, v0 + b, top, 2, 16 - px, 2 + len, 16);
        }
        // Левый и правый брусья, повтор сверху вниз
        for (double s = v1 - b; s > v0 + b + 1e-6; s -= TILE) {
            double e = Math.max(s - TILE, v0 + b), len = (s - e) * 16;
            front(icon, u0, e, u0 + b, s, top, 0, 2, px, 2 + len);
            front(icon, u1 - b, e, u1, s, top, 16 - px, 2, 16, 2 + len);
        }

        // Внутренние торцы брусьев — ступенька от картинки до лицевой стороны рамки
        double ib0 = u0 + b, ib1 = u1 - b, jb0 = v0 + b, jb1 = v1 - b;
        if (ib1 > ib0 && jb1 > jb0) {
            alongU(icon, ib0, ib1, jb1, PX, top, 6, 1, 0, -0.05, 0.55f);
            alongU(icon, ib0, ib1, jb0, PX, top, 6, 1, 0, 0.05, 1.0f);
            alongV(icon, jb0, jb1, ib0, PX, top, 6, 1, 0.05, 0.8f);
            alongV(icon, jb0, jb1, ib1, PX, top, 6, 1, -0.05, 0.8f);
        }
    }

    /** Лицевой прямоугольник (плоскость d), текстура — пиксели (pu0, pv0)–(pu1, pv1) иконки. */
    private void front(IIcon icon, double u0, double v0, double u1, double v1, double d,
                       double pu0, double pv0, double pu1, double pv1) {
        light((u0 + u1) / 2, (v0 + v1) / 2, d + 0.05, 1f);
        double ia = icon.getInterpolatedU(pu0), ib = icon.getInterpolatedU(pu1);
        double ic = icon.getInterpolatedV(pv1), ie = icon.getInterpolatedV(pv0);
        vertex(u0, v0, d, ia, ic);
        vertex(u1, v0, d, ib, ic);
        vertex(u1, v1, d, ib, ie);
        vertex(u0, v1, d, ia, ie);
    }

    /** Горизонтальный торец (плоскость v = const) вдоль u, повтор текстуры по 12 пикселей. */
    private void alongU(IIcon icon, double u0, double u1, double v, double d0, double d1,
                        double row, double rows, double du, double dv, float shade) {
        for (double s = u0; s < u1 - 1e-6; s += TILE) {
            double e = Math.min(s + TILE, u1);
            light((s + e) / 2, v + dv, (d0 + d1) / 2, shade);
            double ia = icon.getInterpolatedU(2), ib = icon.getInterpolatedU(2 + (e - s) * 16);
            double ic = icon.getInterpolatedV(row), ie = icon.getInterpolatedV(row + rows);
            vertex(s, v, d0, ia, ic);
            vertex(e, v, d0, ib, ic);
            vertex(e, v, d1, ib, ie);
            vertex(s, v, d1, ia, ie);
        }
    }

    /** Вертикальный торец (плоскость u = const) вдоль v. */
    private void alongV(IIcon icon, double v0, double v1, double u, double d0, double d1,
                        double row, double rows, double du, float shade) {
        for (double s = v0; s < v1 - 1e-6; s += TILE) {
            double e = Math.min(s + TILE, v1);
            light(u + du, (s + e) / 2, (d0 + d1) / 2, shade);
            double ia = icon.getInterpolatedU(2), ib = icon.getInterpolatedU(2 + (e - s) * 16);
            double ic = icon.getInterpolatedV(row), ie = icon.getInterpolatedV(row + rows);
            vertex(u, s, d0, ia, ic);
            vertex(u, e, d0, ib, ic);
            vertex(u, e, d1, ib, ie);
            vertex(u, s, d1, ia, ie);
        }
    }

    // ---------------------------------------------------------------------
    // Вершины и свет

    private void vertex(double u, double v, double d, double tu, double tv) {
        double[] p = FrameGeometry.toBlock(facing, u, v, d);
        t.addVertexWithUV(p[0], p[1], p[2], tu, tv);
    }

    /** Освещение клетки, в которой лежит точка (u, v, d), и затенение грани как у ванильных блоков. */
    private void light(double u, double v, double d, float shade) {
        double[] p = FrameGeometry.toBlock(facing, u, v, d);
        int x = bx + (int) Math.floor(p[0]), y = by + (int) Math.floor(p[1]), z = bz + (int) Math.floor(p[2]);
        int brightness = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
        if (brightness == 0) brightness = world.getLightBrightnessForSkyBlocks(bx, by, bz, 0);
        t.setBrightness(brightness);
        t.setColorOpaque_F(shade * tintR, shade * tintG, shade * tintB);
    }

    /** Цвет текстуры (листва и трава из атласа серые — их красит цвет блока). */
    private void setTint(float r, float g, float b) {
        tintR = r;
        tintG = g;
        tintB = b;
    }

    // ---------------------------------------------------------------------

    /**
     * Выбирает, что показывать: текущий слайд, если он уже стилизован, иначе — то, что было на экране раньше
     * (чтобы при смене слайда или настроек картинка не пропадала на время обработки).
     */
    private static PictureManager.Variant currentVariant(TilePictureFrame tile) {
        String key = tile.getSourceKey();
        if (key == null || tile.getWorldObj() == null) return null;
        if (!tile.hasSharedFiles() && tile.source != TilePictureFrame.SOURCE_YANDEX) ImageSync.autoOffer(tile);

        PictureManager.Source source = PictureManager.get(key);
        long time = tile.getWorldObj().getTotalWorldTime();
        long seed = tile.getSlideSeed();

        PictureManager.Picture pic = source.slide(time, tile.interval, 0, seed);
        if (pic == null && !source.isLoading()) return null; // ошибка или пустой источник

        PictureManager.Variant current = null;
        if (pic != null && pic.request()) {
            current = pic.variant(ImageStylizer.Layout.of(tile, pic.getOrigWidth(), pic.getOrigHeight()));
        }

        // Заранее готовим следующий слайд
        PictureManager.Picture next = source.slide(time, tile.interval, 1, seed);
        if (next != null && next.request()) {
            next.variant(ImageStylizer.Layout.of(tile, next.getOrigWidth(), next.getOrigHeight()));
        }

        if (current != null && current.isReady()) return current;
        if (tile.clientRenderState instanceof PictureManager.Variant) {
            PictureManager.Variant previous = (PictureManager.Variant) tile.clientRenderState;
            if (previous.isReady()) return previous;
        }
        return null;
    }
}
