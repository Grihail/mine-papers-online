package com.grihail.minepapers.client;

import java.util.HashMap;
import java.util.Map;

import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Рисует картину целиком: холст, рамку (по выбранному виду), саму картинку. Всё строится в координатах стены (u — вправо, v — вверх, d — от стены), см. FrameGeometry. */
public class RenderPicture extends EntityRenderer<EntityPicture> {

    private static final double PX = PictureFrame.PIXEL;
    /** Плоскость граффити — почти вплотную к стене. */
    private static final double DECAL_DEPTH = 0.003;
    /** Длина одного повтора текстуры рамки вдоль ребра: 12 пикселей текстуры = 12/16 блока. */
    private static final double TILE = 12 * PX;

    // Состояние текущей отрисовки (рендер однопоточный)
    private int facing, rotation;
    private Level level;
    /** Угол клетки рамки у поверхности (см. FrameGeometry.origin) и сама клетка рамки. */
    private double ox, oy, oz;
    private BlockPos framePos;
    private PoseStack.Pose pose;
    private VertexConsumer out;
    private float tintR = 1f, tintG = 1f, tintB = 1f;
    private float colorR = 1f, colorG = 1f, colorB = 1f, colorA = 1f;
    private int light;
    /** Свечение рисуемой картины (0..15). */
    private int glow;
    private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

    public RenderPicture(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityPicture entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(EntityPicture picture, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        PictureFrame tile = picture.frame;
        level = picture.level();
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
        framePos = new BlockPos(tile.x, tile.y, tile.z);

        double u0 = tile.getOffsetXBlocks(), v0 = tile.getOffsetYBlocks();
        double u1 = u0 + tile.getWidthBlocks(), v1 = v0 + tile.getHeightBlocks();
        int frame = tile.getFrameShape(), canvasKind = tile.getCanvasKind();
        boolean decal = canvasKind == PictureFrame.CANVAS_NONE, plate = canvasKind == PictureFrame.CANVAS_PICTURE;
        glow = tile.selfGlow();

        poseStack.pushPose();
        // Отсчёт от угла клетки рамки у поверхности, а не от позиции сущности (центра холста).
        Vec3 at = picture.getPosition(partialTick);
        poseStack.translate(ox - at.x, oy - at.y, oz - at.z);
        pose = poseStack.last();

        // 1. Холст и рамка (обычная или с текстурой предмета) — всё из атласа блоков. Без холста и у пластины холст только вместо картинки, пока её нет, — обычный: блок холста им не нужен.
        boolean drawCanvas = canvasKind == PictureFrame.CANVAS_BLOCK || !hasPicture;
        double base = decal ? DECAL_DEPTH : PX; // лицевая плоскость холста
        if (drawCanvas || frame != PictureFrame.FRAME_NONE) {
            out = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            if (drawCanvas) {
                FrameSkin canvasSkin = tile.usesCanvas() ? FrameSkin.canvas(tile.canvas) : FrameSkin.plainCanvas();
                setTint(canvasSkin.r, canvasSkin.g, canvasSkin.b);
                canvas(canvasSkin.sprite, u0, v0, u1, v1, base);
                // Торцы без рамки — из самого холста.
                if (!decal && frame == PictureFrame.FRAME_NONE) edges(canvasSkin.sprite, u0, v0, u1, v1, 0, PX);
            }
            if (frame != PictureFrame.FRAME_NONE) {
                FrameSkin skin = FrameSkin.border(tile.border);
                setTint(skin.r, skin.g, skin.b);
                if (frame == PictureFrame.FRAME_EDGES) {
                    edges(skin.sprite, u0, v0, u1, v1, 0, PX);
                } else {
                    realisticFrame(skin.sprite, u0, v0, u1, v1, base);
                }
            }
        }

        // 2. Картинка (без холста — прижата к стене смещением полигонов, без мерцания; пластина — объёмная, сама по себе, торцы в рамке закрывает рамка)
        if (hasPicture) {
            double d = decal ? DECAL_DEPTH : plate ? PX : PX + 0.002;
            // Кадр анимации — по игровому времени: у всех игроков одинаковый, как и слайд.
            ResourceLocation texture = (ResourceLocation) shown.getTexture((long) ((level.getGameTime() + partialTick) * 50));
            out = buffers.getBuffer(decal ? PictureRenderTypes.decal(texture) : RenderType.entityTranslucent(texture));
            setTint(1f, 1f, 1f);
            double pu0 = u0 + shown.x0, pv0 = v0 + shown.y0, pu1 = u0 + shown.x1, pv1 = v0 + shown.y1;
            picture(pu0, pv0, pu1, pv1, d);
            if (plate) {
                picture(pu0, pv0, pu1, pv1, 0);
                if (frame == PictureFrame.FRAME_NONE) plateEdges(shown, pu0, pv0, pu1, pv1);
            }
        }

        // 3. Обводка хитбокса под прицелом — как у блоков. Здесь, а не в мировых координатах: так она повторяет картину,
        // даже если её позу меняет кто-то ещё (движущиеся конструкции других модов).
        if (PictureControls.isAimed(picture)) {
            AABB box = FrameGeometry.canvasBox(tile).inflate(0.002).move(-ox, -oy, -oz);
            LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box, 0f, 0f, 0f, 0.4f);
        }

        poseStack.popPose();
        out = null;
        pose = null;

        // 4. Под прицелом с зажатым Shift — заголовок картины, как имя переименованного предмета в рамке.
        if (!tile.title.isEmpty() && PictureControls.isAimed(picture) && Screen.hasShiftDown()
            && Minecraft.renderNames()) {
            double[] p = FrameGeometry.labelPos(tile);
            titleLabel(Component.literal(tile.title), p[0] - at.x, p[1] - at.y, p[2] - at.z, poseStack, buffers, packedLight);
        }
    }

    /** Подпись над миром, как ванильная табличка с именем (EntityRenderer.renderNameTag): тёмная подложка, текст виден и сквозь стены (тускло). */
    private void titleLabel(Component text, double x, double y, double z, PoseStack poseStack, MultiBufferSource buffers,
                            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.025f, -0.025f, 0.025f);
        Matrix4f matrix = poseStack.last().pose();
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255f) << 24;
        Font font = getFont();
        float left = -font.width(text) / 2f;
        font.drawInBatch(text, left, 0, 0x20FFFFFF, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, background,
            packedLight);
        font.drawInBatch(text, left, 0, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    // --------------------------------------------------------------------- Части картины

    /** Картинка с плавным освещением, как у ванильных блоков: свет считается в каждой вершине сетки (среднее по соседним клеткам) и плавно перетекает по поверхности — без "ступенек" по блокам. */
    private void picture(double u0, double v0, double u1, double v1, double d) {
        lightCache.clear();
        colorR = colorG = colorB = colorA = 1f;
        for (int cu = (int) Math.floor(u0); cu < u1; cu++) {
            for (int cv = (int) Math.floor(v0); cv < v1; cv++) {
                double a = Math.max(u0, cu), b = Math.min(u1, cu + 1);
                double c = Math.max(v0, cv), e = Math.min(v1, cv + 1);
                if (b - a < 1e-6 || e - c < 1e-6) continue;
                double ta = (a - u0) / (u1 - u0), tb = (b - u0) / (u1 - u0);
                double tc = 1 - (c - v0) / (v1 - v0), te = 1 - (e - v0) / (v1 - v0);
                light = smoothLight(a, c, d);
                vertex(a, c, d, ta, tc);
                light = smoothLight(b, c, d);
                vertex(b, c, d, tb, tc);
                light = smoothLight(b, e, d);
                vertex(b, e, d, tb, te);
                light = smoothLight(a, e, d);
                vertex(a, e, d, ta, te);
            }
        }
    }

    private final Map<Long, Integer> lightCache = new HashMap<Long, Integer>();

    /** Свет в точке стены: среднее (отдельно небо и источники) по четырём клеткам вокруг, непрозрачные пропускаем. */
    private int smoothLight(double u, double v, double d) {
        long key = Double.doubleToLongBits(u) * 31 + Double.doubleToLongBits(v);
        Integer cached = lightCache.get(key);
        if (cached != null) return cached;

        int sky = 0, block = 0, n = 0;
        for (int i = 0; i < 4; i++) {
            double[] p = FrameGeometry.toBlock(facing, rotation, u + ((i & 1) == 0 ? -0.5 : 0.5),
                v + ((i & 2) == 0 ? -0.5 : 0.5), d + 0.05);
            cell.set(Mth.floor(ox + p[0]), Mth.floor(oy + p[1]), Mth.floor(oz + p[2]));
            if (level.getBlockState(cell).isSolidRender(level, cell)) continue;
            int br = LevelRenderer.getLightColor(level, cell);
            sky += (br >> 16) & 0xFFFF;
            block += br & 0xFFFF;
            n++;
        }
        int result = n == 0 ? LevelRenderer.getLightColor(level, framePos) : (sky / n) << 16 | (block / n);
        lightCache.put(key, result);
        return result;
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
    private void canvas(TextureAtlasSprite icon, double u0, double v0, double u1, double v1, double d) {
        for (int cu = (int) Math.floor(u0); cu < u1; cu++) {
            for (int cv = (int) Math.floor(v0); cv < v1; cv++) {
                double a = Math.max(u0, cu), b = Math.min(u1, cu + 1);
                double c = Math.max(v0, cv), e = Math.min(v1, cv + 1);
                if (b - a < 1e-6 || e - c < 1e-6) continue;
                light((a + b) / 2, (c + e) / 2, d + 0.05, 1f);
                double ia = texU(icon, (a - cu) * 16), ib = texU(icon, (b - cu) * 16);
                double ic = texV(icon, (cv + 1 - c) * 16), ie = texV(icon, (cv + 1 - e) * 16);
                vertex(a, c, d, ia, ic);
                vertex(b, c, d, ib, ic);
                vertex(b, e, d, ib, ie);
                vertex(a, e, d, ia, ie);
            }
        }
    }

    /** Торцы холста от d0 до d1, обшитые деревом рамки (вид "Панель" и внешние торцы реалистичной рамки). Берём древесину из середины текстуры рамки: полоса шириной в толщину торца. */
    private void edges(TextureAtlasSprite icon, double u0, double v0, double u1, double v1, double d0, double d1) {
        double rows = (d1 - d0) * 16;
        // верх и низ
        alongU(icon, u0, u1, v1, d0, d1, 6, rows, 0.05, 1.0f);
        alongU(icon, u0, u1, v0, d0, d1, 6, rows, -0.05, 0.55f);
        // лево и право
        alongV(icon, v0, v1, u0, d0, d1, rows, true, true, -0.05, 0.8f);
        alongV(icon, v0, v1, u1, d0, d1, rows, false, true, 0.05, 0.8f);
    }

    /** Багет: брусья шириной 2 пикселя заходят на холст и выступают над картинкой (base — лицевая плоскость холста); лицевая сторона — кольцо из текстуры рамки (дерево + золотая кайма), углы — её же углы. */
    private void realisticFrame(TextureAtlasSprite icon, double u0, double v0, double u1, double v1, double base) {
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
            double rows = (top - base) * 16;
            alongU(icon, ib0, ib1, jb1, base, top, 6, rows, -0.05, 0.55f);
            alongU(icon, ib0, ib1, jb0, base, top, 6, rows, 0.05, 1.0f);
            alongV(icon, jb0, jb1, ib0, base, top, rows, false, false, 0.05, 0.8f);
            alongV(icon, jb0, jb1, ib1, base, top, rows, true, false, -0.05, 0.8f);
        }
    }

    /** Лицевой прямоугольник (плоскость d), текстура — пиксели (pu0, pv0)–(pu1, pv1) спрайта. */
    private void front(TextureAtlasSprite icon, double u0, double v0, double u1, double v1, double d,
                       double pu0, double pv0, double pu1, double pv1) {
        light((u0 + u1) / 2, (v0 + v1) / 2, d + 0.05, 1f);
        double ia = texU(icon, pu0), ib = texU(icon, pu1);
        double ic = texV(icon, pv1), ie = texV(icon, pv0);
        vertex(u0, v0, d, ia, ic);
        vertex(u1, v0, d, ib, ic);
        vertex(u1, v1, d, ib, ie);
        vertex(u0, v1, d, ia, ie);
    }

    /** Горизонтальный торец (плоскость v = const) вдоль u, повтор текстуры по 12 пикселей. */
    private void alongU(TextureAtlasSprite icon, double u0, double u1, double v, double d0, double d1,
                        double row, double rows, double dv, float shade) {
        for (double s = u0; s < u1 - 1e-6; s += TILE) {
            double e = Math.min(s + TILE, u1);
            light((s + e) / 2, v + dv, (d0 + d1) / 2, shade);
            double ia = texU(icon, 2), ib = texU(icon, 2 + (e - s) * 16);
            double ic = texV(icon, row), ie = texV(icon, row + rows);
            vertex(s, v, d0, ia, ic);
            vertex(e, v, d0, ib, ic);
            vertex(e, v, d1, ib, ie);
            vertex(s, v, d1, ia, ie);
        }
    }

    /** Вертикальный торец (плоскость u = const) вдоль v. Текстура стоит как на лицевой стороне — верхом вверх: у наружного торца (fromTop) верхний блок берёт её верх (у травы — зелёная кайма), ниже — и у внутренних целиком — повторяется середина, как у лицевых брусьев. Поперёк — cols столбцов у края текстуры (left — левого), чтобы угол продолжал лицевую сторону без шва. */
    private void alongV(TextureAtlasSprite icon, double v0, double v1, double u, double d0, double d1,
                        double cols, boolean left, boolean fromTop, double du, float shade) {
        double outer = left ? 0 : 16, inner = left ? cols : 16 - cols; // у лица (d1) — крайний столбец
        boolean first = fromTop;
        for (double s = v1; s > v0 + 1e-6; first = false) {
            double e = Math.max(s - (first ? 1 : TILE), v0);
            double t0 = first ? 0 : 2, t1 = t0 + (s - e) * 16;
            light(u + du, (s + e) / 2, (d0 + d1) / 2, shade);
            double ia = texU(icon, inner), ib = texU(icon, outer);
            double ic = texV(icon, t0), ie = texV(icon, t1);
            vertex(u, s, d0, ia, ic);
            vertex(u, e, d0, ia, ie);
            vertex(u, e, d1, ib, ie);
            vertex(u, s, d1, ib, ic);
            s = e;
        }
    }

    // --------------------------------------------------------------------- Вершины и свет

    /** Пиксель спрайта (0..16) -> координата в атласе. */
    private static double texU(TextureAtlasSprite sprite, double px) {
        return sprite.getU((float) (px / 16));
    }

    private static double texV(TextureAtlasSprite sprite, double px) {
        return sprite.getV((float) (px / 16));
    }

    /** Нормаль у всех вершин — вверх: так ванильный шейдер сущностей не затемняет картину в зависимости от стороны света, а затенение граней делаем сами (light), как у блоков. */
    private void vertex(double u, double v, double d, double tu, double tv) {
        double[] p = FrameGeometry.toBlock(facing, rotation, u, v, d);
        out.addVertex(pose, (float) p[0], (float) p[1], (float) p[2])
            .setColor(colorR, colorG, colorB, colorA)
            .setUv((float) tu, (float) tv)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(glowing(light))
            .setNormal(pose, 0f, 1f, 0f);
    }

    /** Светящаяся картина не темнее своего свечения: блочный свет не ниже него, как у светящихся блоков (небесный — как есть). */
    private int glowing(int packed) {
        return glow > LightTexture.block(packed) ? LightTexture.pack(glow, LightTexture.sky(packed)) : packed;
    }

    /** Освещение клетки, в которой лежит точка (u, v, d), и затенение грани как у ванильных блоков. */
    private void light(double u, double v, double d, float shade) {
        double[] p = FrameGeometry.toBlock(facing, rotation, u, v, d);
        cell.set(Mth.floor(ox + p[0]), Mth.floor(oy + p[1]), Mth.floor(oz + p[2]));
        int brightness = LevelRenderer.getLightColor(level, cell);
        if (brightness == 0) brightness = LevelRenderer.getLightColor(level, framePos);
        light = brightness;
        colorR = shade * tintR;
        colorG = shade * tintG;
        colorB = shade * tintB;
        colorA = 1f;
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
        if (!tile.hasSharedFiles() && !tile.isNetworkSource()) ImageSync.autoOffer(picture.level().dimension().location(), picture.getId(), tile);

        PictureManager.Source source = PictureManager.get(key);
        long time = picture.level().getGameTime();
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
        if (tile.clientRenderState instanceof PictureManager.Variant previous && previous.isReady()) return previous;
        return null;
    }
}
