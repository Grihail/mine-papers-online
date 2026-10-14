package com.grihail.minepapers.client;

import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/** Рисует картину целиком: холст, рамку (по выбранному виду), саму картинку. Всё строится в координатах стены (u — вправо, v — вверх, d — от стены), см. FrameGeometry. */
public class RenderPicture extends Render {

    private static final double PX = PictureFrame.PIXEL;
    /** Плоскость граффити — почти вплотную к стене. */
    private static final double DECAL_DEPTH = 0.003;
    /** Длина одного повтора текстуры рамки вдоль ребра: 12 пикселей текстуры = 12/16 блока. */
    private static final double TILE = 12 * PX;
    /** Кайма багета — два крайних пикселя текстуры рамки (тёмный край и светлая фаска). */
    private static final double RIM = 2 * PX;
    /** Значок "18+" на скрытой картине NSFW. */
    private static final ResourceLocation NSFW_MARK = new ResourceLocation(MinePapers.MODID, "textures/misc/nsfw.png");

    // Состояние текущей отрисовки (рендер однопоточный)
    private int facing, rotation;
    private World world;
    /** Угол клетки рамки у поверхности (см. FrameGeometry.origin) и сама клетка рамки. */
    private double ox, oy, oz;
    private int bx, by, bz;
    private Tessellator t;
    private float tintR = 1f, tintG = 1f, tintB = 1f;
    /** Свечение рисуемой картины (0..15). */
    private int glow;

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        EntityPicture picture = (EntityPicture) entity;
        PictureFrame tile = picture.frame;
        world = picture.worldObj;
        PictureManager.Variant shown = currentVariant(picture);
        tile.clientRenderState = shown;
        boolean hasPicture = shown != null && shown.hasTexture();
        if (hasPicture) shown.touch();

        facing = tile.getFacing();
        rotation = tile.getRotation();
        double[] origin = FrameGeometry.origin(tile);
        ox = origin[0];
        oy = origin[1];
        oz = origin[2];
        // Запасной свет — из самой клетки рамки: угол у поверхности может оказаться внутри блока (полублок, ковёр).
        bx = tile.x;
        by = tile.y;
        bz = tile.z;
        t = Tessellator.instance;

        double u0 = tile.getOffsetXBlocks(), v0 = tile.getOffsetYBlocks();
        double u1 = u0 + tile.getWidthBlocks(), v1 = v0 + tile.getHeightBlocks();
        int frame = tile.getFrameShape(), canvasKind = tile.getCanvasKind();
        boolean decal = canvasKind == PictureFrame.CANVAS_NONE, plate = canvasKind == PictureFrame.CANVAS_PICTURE;
        glow = tile.selfGlow();

        GL11.glPushMatrix();
        // Отсчёт от угла клетки рамки у поверхности, а не от позиции сущности (центра холста).
        GL11.glTranslated(ox - RenderManager.renderPosX, oy - RenderManager.renderPosY, oz - RenderManager.renderPosZ);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        // 1. Холст и рамка (обычные или с текстурой предмета — она может быть в атласе предметов). Без холста и у пластины холст только вместо картинки, пока её нет, — обычный: блок холста им не нужен.
        boolean drawCanvas = canvasKind == PictureFrame.CANVAS_BLOCK || !hasPicture;
        double base = decal ? DECAL_DEPTH : PX; // лицевая плоскость холста
        if (drawCanvas || frame != PictureFrame.FRAME_NONE) {
            FrameSkin canvasSkin = tile.usesCanvas() ? FrameSkin.canvas(tile.canvas) : FrameSkin.plainCanvas();
            FrameSkin skin = FrameSkin.border(tile.border);
            ResourceLocation atlas = drawCanvas ? canvasSkin.atlas : skin.atlas;
            bindTexture(atlas);
            t.startDrawingQuads();
            if (drawCanvas) {
                setTint(canvasSkin.r, canvasSkin.g, canvasSkin.b);
                canvas(canvasSkin.icon, u0, v0, u1, v1, base);
                // Торцы без рамки — из самого холста.
                if (!decal && frame == PictureFrame.FRAME_NONE) edges(canvasSkin.icon, u0, v0, u1, v1, 0, PX);
            }
            if (frame != PictureFrame.FRAME_NONE) {
                if (skin.atlas != atlas) {
                    t.draw();
                    bindTexture(skin.atlas);
                    t.startDrawingQuads();
                }
                setTint(skin.r, skin.g, skin.b);
                if (frame == PictureFrame.FRAME_EDGES) {
                    edges(skin.icon, u0, v0, u1, v1, 0, PX);
                } else {
                    realisticFrame(skin.icon, u0, v0, u1, v1, base, tile.getMoldingBlocks());
                }
            }
            t.draw();
        }

        // Цвет материала (трава, листва) красит только холст и рамку — не картинку и не следующую картину.
        setTint(1f, 1f, 1f);

        // 2. Картинка (пластина — объёмная, сама по себе; торцы в рамке закрывает рамка)
        if (hasPicture) {
            double d = decal ? DECAL_DEPTH : plate ? PX : PX + 0.002;
            if (decal) {
                // Прижимаем к стене без мерцания
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(-1f, -10f);
            }
            // Кадр анимации — по игровому времени: у всех игроков одинаковый, как и слайд.
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, (Integer) shown.getTexture((long) ((world.getTotalWorldTime() + partialTicks) * 50)));
            t.startDrawingQuads();
            double pu0 = u0 + shown.x0, pv0 = v0 + shown.y0, pu1 = u0 + shown.x1, pv1 = v0 + shown.y1;
            picture(pu0, pv0, pu1, pv1, d);
            if (plate) {
                picture(pu0, pv0, pu1, pv1, 0);
                setTint(1f, 1f, 1f);
                if (frame == PictureFrame.FRAME_NONE) plateEdges(shown, pu0, pv0, pu1, pv1);
            }
            t.draw();
            if (decal) GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }

        // 2б. Картина NSFW, скрытая настройкой hideNsfw: по центру холста красный значок "18+" (1x1 блок, не больше холста) -
        // чтобы её не путали с картиной, которая ещё не загрузилась.
        if (ModConfig.hides(tile.nsfw)) nsfwMark(u0, v0, u1, v1, decal ? DECAL_DEPTH : PX);

        // Возвращаем состояние OpenGL как было: моды вроде Angelica следят за ним и не любят "хвостов".
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPopMatrix();

        // 3. Под прицелом с зажатым Shift — заголовок картины и курсивом ник подписавшего, как имя переименованного предмета в рамке.
        MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
        String label = label(tile);
        if (!label.isEmpty() && hit != null && hit.entityHit == picture && GuiScreen.isShiftKeyDown()
            && Minecraft.isGuiEnabled()) {
            double[] p = FrameGeometry.labelPos(tile);
            titleLabel(label, p[0] - RenderManager.renderPosX, p[1] - RenderManager.renderPosY,
                p[2] - RenderManager.renderPosZ);
        }
    }

    /** Заголовок и ник подписавшего курсивом: "Заголовок - ник". */
    private static String label(PictureFrame tile) {
        if (!tile.isSigned()) return tile.title;
        return (tile.title.isEmpty() ? "" : tile.title + " - ") + "\u00a7o" + tile.signerName;
    }

    /** Подпись над миром, как ванильная табличка с именем (Render.func_147906_a): тёмная подложка, текст виден и сквозь стены (тускло). */
    private void titleLabel(String text, double x, double y, double z) {
        FontRenderer font = getFontRendererFromRenderManager();
        float scale = 0.025f;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0f, 1f, 0f);
        GL11.glRotatef(-renderManager.playerViewY, 0f, 1f, 0f);
        GL11.glRotatef(renderManager.playerViewX, 1f, 0f, 0f);
        GL11.glScalef(-scale, -scale, scale);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        int half = font.getStringWidth(text) / 2;
        t.startDrawingQuads();
        t.setColorRGBA_F(0f, 0f, 0f, 0.25f);
        t.addVertex(-half - 1, -1, 0);
        t.addVertex(-half - 1, 8, 0);
        t.addVertex(half + 1, 8, 0);
        t.addVertex(half + 1, -1, 0);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        font.drawString(text, -half, 0, 0x20FFFFFF);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        font.drawString(text, -half, 0, 0xFFFFFFFF);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glPopMatrix();
    }

    // --------------------------------------------------------------------- Части картины

    /** Картинка с плавным освещением, как у ванильных блоков: свет считается в каждой вершине сетки (среднее по соседним клеткам) и плавно перетекает по поверхности — без "ступенек" по блокам. */
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
            double[] p = FrameGeometry.toBlock(facing, rotation, u + ((i & 1) == 0 ? -0.5 : 0.5), v + ((i & 2) == 0 ? -0.5 : 0.5), d + 0.05);
            int x = MathHelper.floor_double(ox + p[0]), y = MathHelper.floor_double(oy + p[1]), z = MathHelper.floor_double(oz + p[2]);
            if (world.getBlock(x, y, z).isOpaqueCube()) continue;
            int br = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
            sky += (br >> 16) & 0xFFFF;
            block += br & 0xFFFF;
            n++;
        }
        int result = n == 0 ? world.getLightBrightnessForSkyBlocks(bx, by, bz, 0)
            : (sky / n) << 16 | (block / n);
        // Светящаяся картина не темнее своего свечения (блочный свет — младшие биты, в шестнадцатых).
        if ((result & 0xFFFF) < glow << 4) result = (result & 0xFFFF0000) | glow << 4;
        lightCache.put(key, result);
        return result;
    }

    /** Значок "18+" по центру холста: квадрат в блок (у холста меньше блока — по его меньшей стороне), чуть над лицевой плоскостью base. */
    private void nsfwMark(double u0, double v0, double u1, double v1, double base) {
        double half = Math.min(1, Math.min(u1 - u0, v1 - v0)) / 2;
        double cu = (u0 + u1) / 2, cv = (v0 + v1) / 2, d = base + 0.002;
        bindTexture(NSFW_MARK);
        t.startDrawingQuads();
        setTint(1f, 1f, 1f);
        light(cu, cv, d + 0.05, 1f);
        vertex(cu - half, cv - half, d, 0, 1);
        vertex(cu + half, cv - half, d, 1, 1);
        vertex(cu + half, cv + half, d, 1, 0);
        vertex(cu - half, cv + half, d, 0, 0);
        t.draw();
    }

    /** Торцы пластины: крайние пиксели картинки, растянутые на всю толщину. */
    private void plateEdges(PictureManager.Variant shown, double u0, double v0, double u1, double v1) {
        // Середина крайнего пикселя текстуры, чтобы не захватить соседний.
        double hu = 0.5 / shown.width, hv = 0.5 / shown.height;
        plateU(u0, u1, v1, hv, 0.05, 1.0f);
        plateU(u0, u1, v0, 1 - hv, -0.05, 0.55f);
        plateV(v0, v1, u0, hu, -0.05, 0.8f);
        plateV(v0, v1, u1, 1 - hu, 0.05, 0.8f);
    }

    /** Горизонтальный торец пластины (плоскость v = const), по блокам — свет у каждого свой. */
    private void plateU(double u0, double u1, double v, double tv, double dv, float shade) {
        for (double s = u0; s < u1 - 1e-6; s = Math.floor(s) + 1) {
            double e = Math.min(Math.floor(s) + 1, u1);
            light((s + e) / 2, v + dv, PX / 2, shade);
            double ta = (s - u0) / (u1 - u0), tb = (e - u0) / (u1 - u0);
            vertex(s, v, 0, ta, tv);
            vertex(e, v, 0, tb, tv);
            vertex(e, v, PX, tb, tv);
            vertex(s, v, PX, ta, tv);
        }
    }

    /** Вертикальный торец пластины (плоскость u = const). */
    private void plateV(double v0, double v1, double u, double tu, double du, float shade) {
        for (double s = v0; s < v1 - 1e-6; s = Math.floor(s) + 1) {
            double e = Math.min(Math.floor(s) + 1, v1);
            light(u + du, (s + e) / 2, PX / 2, shade);
            double ta = 1 - (s - v0) / (v1 - v0), tb = 1 - (e - v0) / (v1 - v0);
            vertex(u, s, 0, tu, ta);
            vertex(u, e, 0, tu, tb);
            vertex(u, e, PX, tu, tb);
            vertex(u, s, PX, tu, ta);
        }
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

    /** Торцы холста от d0 до d1, обшитые деревом рамки (вид "Панель" и внешние торцы реалистичной рамки). Берём древесину из середины текстуры рамки: полоса шириной в толщину торца. */
    private void edges(IIcon icon, double u0, double v0, double u1, double v1, double d0, double d1) {
        double rows = (d1 - d0) * 16;
        // верх и низ
        alongU(icon, u0, u1, v1, d0, d1, 6, rows, 0, 0.05, 1.0f);
        alongU(icon, u0, u1, v0, d0, d1, 6, rows, 0, -0.05, 0.55f);
        // лево и право
        alongV(icon, v0, v1, u0, d0, d1, rows, true, true, -0.05, 0.8f);
        alongV(icon, v0, v1, u1, d0, d1, rows, false, true, 0.05, 0.8f);
    }

    /** Багет: брусья шириной width заходят на холст и выступают над картинкой (base — лицевая плоскость холста). Лицевая сторона: по внешнему краю — кайма из краёв и углов текстуры рамки (не шире RIM), внутри неё — середина текстуры без шва (у боковых брусьев повёрнута, чтобы волокна шли вдоль бруса). */
    private void realisticFrame(IIcon icon, double u0, double v0, double u1, double v1, double base, double width) {
        double b = Math.min(width, Math.min((u1 - u0) / 2, (v1 - v0) / 2));
        double top = 2 * PX; // лицевая плоскость рамки
        double r = Math.min(b, RIM);
        double px = r * 16;   // ширина каймы в пикселях текстуры
        double far = 16 - px; // справа и снизу кайма кончается у края текстуры

        // Внешние торцы — на всю толщину
        edges(icon, u0, v0, u1, v1, 0, top);

        // Кайма: углы — углы текстуры, стороны — её края, вдоль повторяется середина (12 пикселей)
        front(icon, u0, v1 - r, u0 + r, v1, top, 0, 0, px, px);
        front(icon, u1 - r, v1 - r, u1, v1, top, far, 0, 16, px);
        front(icon, u0, v0, u0 + r, v0 + r, top, 0, far, px, 16);
        front(icon, u1 - r, v0, u1, v0 + r, top, far, far, 16, 16);
        tiledFront(icon, u0 + r, v1 - r, u1 - r, v1, top, 2, 2, 12, 0, 0, 16);
        tiledFront(icon, u0 + r, v0, u1 - r, v0 + r, top, 2, 2, 12, far, 0, 16);
        tiledFront(icon, u0, v0 + r, u0 + r, v1 - r, top, 0, 0, 16, 2, 2, 12);
        tiledFront(icon, u1 - r, v0 + r, u1, v1 - r, top, far, 0, 16, 2, 2, 12);

        // Внутри каймы: верхний и нижний брусья — во всю ширину, боковые — между ними
        tiledFront(icon, u0 + r, v1 - b, u1 - r, v1 - r, top, 2, 2, 12, 2, 2, 12);
        tiledFront(icon, u0 + r, v0 + r, u1 - r, v0 + b, top, 2, 2, 12, 2, 2, 12);
        sideBar(icon, u0 + r, v0 + b, u0 + b, v1 - b, top);
        sideBar(icon, u1 - b, v0 + b, u1 - r, v1 - b, top);

        // Внутренние торцы брусьев — ступенька от картинки до лицевой стороны рамки
        double ib0 = u0 + b, ib1 = u1 - b, jb0 = v0 + b, jb1 = v1 - b;
        if (ib1 > ib0 && jb1 > jb0) {
            double rows = (top - base) * 16;
            alongU(icon, ib0, ib1, jb1, base, top, 6, rows, 0, -0.05, 0.55f);
            alongU(icon, ib0, ib1, jb0, base, top, 6, rows, 0, 0.05, 1.0f);
            alongV(icon, jb0, jb1, ib0, base, top, rows, false, false, 0.05, 0.8f);
            alongV(icon, jb0, jb1, ib1, base, top, rows, true, false, -0.05, 0.8f);
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

    /** Лицевой прямоугольник с повторяющейся текстурой: вправо от u0 — пиксели иконки с tu, повтор [su, su + nu); вниз от v1 — с tv, повтор [sv, sv + nv). На границах повторов прямоугольник режется, чтобы не выйти за иконку. */
    private void tiledFront(IIcon icon, double u0, double v0, double u1, double v1, double d,
                            double tu, double su, double nu, double tv, double sv, double nv) {
        double a = u0;
        for (double ta = wrap(tu, su, nu); a < u1 - 1e-6; ta = su) {
            double b = Math.min(u1, a + (su + nu - ta) / 16);
            double e = v1;
            for (double te = wrap(tv, sv, nv); e > v0 + 1e-6; te = sv) {
                double c = Math.max(v0, e - (sv + nv - te) / 16);
                front(icon, a, c, b, e, d, ta, te, ta + (b - a) * 16, te + (e - c) * 16);
                e = c;
            }
            a = b;
        }
    }

    /** Боковой брус багета внутри каймы: середина текстуры (пиксели 2..14) с повтором, повёрнутая на четверть оборота — её строки (волокна) идут вдоль бруса, сверху вниз. */
    private void sideBar(IIcon icon, double u0, double v0, double u1, double v1, double d) {
        for (double e = v1; e > v0 + 1e-6; ) {
            double c = Math.max(v0, e - 12 * PX);
            for (double a = u0; a < u1 - 1e-6; ) {
                double b = Math.min(u1, a + 12 * PX);
                light((a + b) / 2, (c + e) / 2, d + 0.05, 1f);
                double x0 = icon.getInterpolatedU(2), x1 = icon.getInterpolatedU(2 + (e - c) * 16);
                double y0 = icon.getInterpolatedV(2), y1 = icon.getInterpolatedV(2 + (b - a) * 16);
                vertex(a, c, d, x1, y0);
                vertex(b, c, d, x1, y1);
                vertex(b, e, d, x0, y1);
                vertex(a, e, d, x0, y0);
                a = b;
            }
            e = c;
        }
    }

    /** Пиксель t, приведённый в повтор [start, start + size). */
    private static double wrap(double t, double start, double size) {
        double r = ((t - start) % size + size) % size;
        return start + (size - r < 1e-6 ? 0 : r);
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

    /** Вертикальный торец (плоскость u = const) вдоль v. Текстура стоит как на лицевой стороне — верхом вверх: у наружного торца (fromTop) верхний блок берёт её верх (у травы — зелёная кайма), ниже — и у внутренних целиком — повторяется середина, как у лицевых брусьев. Поперёк — cols столбцов у края текстуры (left — левого), чтобы угол продолжал лицевую сторону без шва. */
    private void alongV(IIcon icon, double v0, double v1, double u, double d0, double d1,
                        double cols, boolean left, boolean fromTop, double du, float shade) {
        double outer = left ? 0 : 16, inner = left ? cols : 16 - cols; // у лица (d1) — крайний столбец
        boolean first = fromTop;
        for (double s = v1; s > v0 + 1e-6; first = false) {
            double e = Math.max(s - (first ? 1 : TILE), v0);
            double t0 = first ? 0 : 2, t1 = t0 + (s - e) * 16;
            light(u + du, (s + e) / 2, (d0 + d1) / 2, shade);
            double ia = icon.getInterpolatedU(inner), ib = icon.getInterpolatedU(outer);
            double ic = icon.getInterpolatedV(t0), ie = icon.getInterpolatedV(t1);
            vertex(u, s, d0, ia, ic);
            vertex(u, e, d0, ia, ie);
            vertex(u, e, d1, ib, ie);
            vertex(u, s, d1, ib, ic);
            s = e;
        }
    }

    // --------------------------------------------------------------------- Вершины и свет

    private void vertex(double u, double v, double d, double tu, double tv) {
        double[] p = FrameGeometry.toBlock(facing, rotation, u, v, d);
        t.addVertexWithUV(p[0], p[1], p[2], tu, tv);
    }

    /** Освещение клетки, в которой лежит точка (u, v, d), и затенение грани как у ванильных блоков. */
    private void light(double u, double v, double d, float shade) {
        double[] p = FrameGeometry.toBlock(facing, rotation, u, v, d);
        int x = MathHelper.floor_double(ox + p[0]), y = MathHelper.floor_double(oy + p[1]), z = MathHelper.floor_double(oz + p[2]);
        // Светящаяся картина не темнее своего свечения — ванильный минимум блочного света, как у светящихся блоков.
        int brightness = world.getLightBrightnessForSkyBlocks(x, y, z, glow);
        if (brightness == 0) brightness = world.getLightBrightnessForSkyBlocks(bx, by, bz, glow);
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

    /** Выбирает, что показывать: текущий слайд, если он уже стилизован, иначе — то, что было на экране раньше (чтобы при смене слайда или настроек картинка не пропадала на время обработки). */
    private static PictureManager.Variant currentVariant(EntityPicture picture) {
        PictureFrame tile = picture.frame;
        String key = tile.getSourceKey();
        if (key == null) return null;
        if (!tile.hasSharedFiles() && !tile.isNetworkSource()) ImageSync.autoOffer(picture.worldObj.provider.dimensionId, picture.getEntityId(), tile);
        // Картина с пометкой NSFW, а у игрока в конфиге hideNsfw: вместо картинки пустой холст, картинки не скачиваются.
        if (ModConfig.hides(tile.nsfw)) return null;

        PictureManager.Source source = PictureManager.get(key);
        long time = picture.worldObj.getTotalWorldTime();
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
