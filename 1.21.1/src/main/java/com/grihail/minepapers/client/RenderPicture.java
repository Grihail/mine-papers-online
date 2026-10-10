package com.grihail.minepapers.client;

import java.util.HashMap;
import java.util.Map;

import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Рисует картину целиком: холст, рамку (по выбранному виду), саму картинку и трещины, пока её ломают. Всё строится в координатах стены (u — вправо, v — вверх, d — от стены), см. FrameGeometry. */
public class RenderPicture extends EntityRenderer<EntityPicture> {

    private static final double PX = PictureFrame.PIXEL;
    /** Плоскость граффити — почти вплотную к стене. */
    private static final double DECAL_DEPTH = 0.003;
    /** Длина одного повтора текстуры рамки вдоль ребра: 12 пикселей текстуры = 12/16 блока. */
    private static final double TILE = 12 * PX;
    /** Полная яркость (небо и источники 15) — для трещин. */
    private static final int FULL_BRIGHT = 0xF000F0;

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
        int mode = tile.mode;

        poseStack.pushPose();
        // Отсчёт от угла клетки рамки у поверхности, а не от позиции сущности (центра холста).
        Vec3 at = picture.getPosition(partialTick);
        poseStack.translate(ox - at.x, oy - at.y, oz - at.z);
        pose = poseStack.last();

        // 1. Холст и рамка (обычная или с текстурой предмета) — всё из атласа блоков
        if (mode != PictureFrame.MODE_DECAL || !hasPicture) {
            out = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            setTint(1f, 1f, 1f);
            canvas(FrameSkin.sprite(FrameSkin.CANVAS), u0, v0, u1, v1, mode == PictureFrame.MODE_DECAL ? DECAL_DEPTH : PX);
            if (mode != PictureFrame.MODE_DECAL) {
                FrameSkin skin = FrameSkin.of(tile.borderItem);
                setTint(skin.r, skin.g, skin.b);
                if (mode == PictureFrame.MODE_PANEL) {
                    edges(skin.sprite, u0, v0, u1, v1, 0, PX);
                } else {
                    realisticFrame(skin.sprite, u0, v0, u1, v1);
                }
            }
        }

        // 2. Картинка (граффити — прижата к стене смещением полигонов, без мерцания)
        if (hasPicture) {
            boolean decal = mode == PictureFrame.MODE_DECAL;
            double d = decal ? DECAL_DEPTH : PX + 0.002;
            out = buffers.getBuffer(decal ? PictureRenderTypes.decal(shown.getTexture())
                : RenderType.entityTranslucent(shown.getTexture()));
            setTint(1f, 1f, 1f);
            picture(u0 + shown.x0, v0 + shown.y0, u0 + shown.x1, v0 + shown.y1, d);
        }

        // 3. Трещины, пока картину ломают — ванильные текстуры разрушения, как у блоков
        if (picture.breakProgress > 0) {
            double front = mode == PictureFrame.MODE_DECAL ? DECAL_DEPTH + 0.001 : tile.getDepth() + 0.002;
            int stage = Math.min(9, (int) (picture.breakProgress * 10));
            out = buffers.getBuffer(ModelBakery.DESTROY_TYPES.get(stage));
            cracks(u0, v0, u1, v1, front);
        }

        poseStack.popPose();
        out = null;
        pose = null;
    }

    /** Трещины поверх всей лицевой стороны: текстура разрушения повторяется по сетке блоков. */
    private void cracks(double u0, double v0, double u1, double v1, double d) {
        light = FULL_BRIGHT;
        colorR = colorG = colorB = colorA = 1f;
        for (int cu = (int) Math.floor(u0); cu < u1; cu++) {
            for (int cv = (int) Math.floor(v0); cv < v1; cv++) {
                double a = Math.max(u0, cu), b = Math.min(u1, cu + 1);
                double c = Math.max(v0, cv), e = Math.min(v1, cv + 1);
                if (b - a < 1e-6 || e - c < 1e-6) continue;
                double ia = a - cu, ib = b - cu;
                double ic = cv + 1 - c, ie = cv + 1 - e;
                vertex(a, c, d, ia, ic);
                vertex(b, c, d, ib, ic);
                vertex(b, e, d, ib, ie);
                vertex(a, e, d, ia, ie);
            }
        }
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
        alongV(icon, v0, v1, u0, d0, d1, 6, rows, -0.05, 0.8f);
        alongV(icon, v0, v1, u1, d0, d1, 6, rows, 0.05, 0.8f);
    }

    /** Реалистичная рамка: брусья шириной 2 пикселя заходят на холст и выступают на пиксель над картинкой; лицевая сторона — кольцо из текстуры рамки (дерево + золотая кайма), углы — её же углы. */
    private void realisticFrame(TextureAtlasSprite icon, double u0, double v0, double u1, double v1) {
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
            alongU(icon, ib0, ib1, jb1, PX, top, 6, 1, -0.05, 0.55f);
            alongU(icon, ib0, ib1, jb0, PX, top, 6, 1, 0.05, 1.0f);
            alongV(icon, jb0, jb1, ib0, PX, top, 6, 1, 0.05, 0.8f);
            alongV(icon, jb0, jb1, ib1, PX, top, 6, 1, -0.05, 0.8f);
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

    /** Вертикальный торец (плоскость u = const) вдоль v. */
    private void alongV(TextureAtlasSprite icon, double v0, double v1, double u, double d0, double d1,
                        double row, double rows, double du, float shade) {
        for (double s = v0; s < v1 - 1e-6; s += TILE) {
            double e = Math.min(s + TILE, v1);
            light(u + du, (s + e) / 2, (d0 + d1) / 2, shade);
            double ia = texU(icon, 2), ib = texU(icon, 2 + (e - s) * 16);
            double ic = texV(icon, row), ie = texV(icon, row + rows);
            vertex(u, s, d0, ia, ic);
            vertex(u, e, d0, ib, ic);
            vertex(u, e, d1, ib, ie);
            vertex(u, s, d1, ia, ie);
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
            .setLight(light)
            .setNormal(pose, 0f, 1f, 0f);
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
        if (!tile.hasSharedFiles() && !tile.isNetworkSource()) ImageSync.autoOffer(picture);

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
