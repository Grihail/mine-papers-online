package com.grihail.minepapers.client;

import java.util.HashMap;
import java.util.Map;

import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.CanvasGeometry;
import com.grihail.minepapers.picture.PictureSettings;

/** Картина в мире целиком: холст, рамка (по выбранному виду), сама картинка и значок NSFW. Всё строится в координатах стены (u — вправо, v — вверх, d — от стены), см. CanvasGeometry; вершины — относительно угла клетки рамки у поверхности (CanvasGeometry.origin). Версия наследует класс и даёт только вывод вершин (emit), свет мира (lightAt, opaqueAt) и смену текстуры (texture). Рендер однопоточный: состояние отрисовки — в полях. */
public abstract class PictureMesh {

    /** Что рисуется текстурой (см. texture): кусок атласа (холст, рамка), картинка, картинка-граффити (прижата к стене смещением полигонов), значок NSFW. */
    public static final int ATLAS = 0, PICTURE = 1, DECAL = 2, MARK = 3;

    private static final double PX = PictureSettings.PIXEL;
    /** Плоскость граффити — почти вплотную к стене. */
    private static final double DECAL_DEPTH = 0.003;
    /** Длина одного повтора текстуры рамки вдоль ребра: 12 пикселей текстуры = 12/16 блока. */
    private static final double TILE = 12 * PX;
    /** Кайма багета — два крайних пикселя текстуры рамки (тёмный край и светлая фаска). */
    private static final double RIM = 2 * PX;

    /** Текстура рамки или холста: спрайт в атласе и цвет блока (листва и трава в атласе серые — их красит цвет). */
    public abstract static class Skin {
        public final float r, g, b;

        protected Skin(int color) {
            this.r = (color >> 16 & 0xFF) / 255f;
            this.g = (color >> 8 & 0xFF) / 255f;
            this.b = (color & 0xFF) / 255f;
        }

        /** Атлас, в котором лежит спрайт; его понимает только версия. */
        public abstract Object atlas();

        /** Пиксель спрайта (0..16) -> координата в атласе. */
        public abstract double u(double px);

        public abstract double v(double px);
    }

    private int facing, rotation;
    /** Угол клетки рамки у поверхности и сама клетка рамки (запасной свет: угол может оказаться внутри блока — полублок, ковёр). */
    private double ox, oy, oz;
    private int fx, fy, fz;
    private float tintR = 1f, tintG = 1f, tintB = 1f;
    /** Свечение рисуемой картины (0..15). */
    private int glow;
    private final Map<Long, Integer> lightCache = new HashMap<Long, Integer>();

    /** Свет (упакован как в ванили: небо << 20 | блоки << 4, светящаяся картина уже учтена) и цвет следующих вершин. */
    protected int light;
    protected float r = 1f, g = 1f, b = 1f;

    /** Вершина в координатах относительно угла клетки рамки у поверхности; свет и цвет — в light, r, g, b. */
    protected abstract void emit(double x, double y, double z, double tu, double tv);

    /** Свет клетки мира, упакованный как в ванили (0 — темно или внутри блока). */
    protected abstract int lightAt(int x, int y, int z);

    /** Непрозрачный блок: сквозь него свет к картине не идёт. */
    protected abstract boolean opaqueAt(int x, int y, int z);

    /** Дальше рисовать этой текстурой (kind — ATLAS, PICTURE, DECAL, MARK; у MARK текстура null — значок версии). Версия может пропустить смену, если текстура та же. */
    protected abstract void texture(int kind, Object texture);

    /** Отрисовка кончилась: дорисовать начатое. */
    protected abstract void finish();

    /** Нарисовать картину. shown — что показывать (см. shown), glow — как ярко светится сама картина (PictureFrame.selfGlow), canvasSkin и borderSkin — текстуры холста и рамки по их материалам. */
    public void draw(PictureSettings tile, PictureManager.Variant shown, int glow, Skin canvasSkin, Skin borderSkin) {
        boolean hasPicture = shown != null && shown.hasTexture();
        facing = tile.getFacing();
        rotation = tile.getRotation();
        double[] origin = CanvasGeometry.origin(tile);
        ox = origin[0];
        oy = origin[1];
        oz = origin[2];
        fx = tile.x;
        fy = tile.y;
        fz = tile.z;
        this.glow = glow;

        double u0 = tile.getOffsetXBlocks(), v0 = tile.getOffsetYBlocks();
        double u1 = u0 + tile.getWidthBlocks(), v1 = v0 + tile.getHeightBlocks();
        int frame = tile.getFrameShape(), canvasKind = tile.getCanvasKind();
        boolean decal = canvasKind == PictureSettings.CANVAS_NONE, plate = canvasKind == PictureSettings.CANVAS_PICTURE;

        // 1. Холст и рамка. Без холста и у пластины холст только вместо картинки, пока её нет (не загружена, нет источника), — обычный: блок холста им не нужен. Загруженной картинке, от которой на холсте ничего не видно (отступ и масштаб не оставили места), заглушка не нужна.
        boolean drawCanvas = canvasKind == PictureSettings.CANVAS_BLOCK || shown == null;
        double base = decal ? DECAL_DEPTH : PX; // лицевая плоскость холста
        if (drawCanvas) {
            texture(ATLAS, canvasSkin.atlas());
            setTint(canvasSkin.r, canvasSkin.g, canvasSkin.b);
            canvas(canvasSkin, u0, v0, u1, v1, base);
            // Торцы без рамки — из самого холста.
            if (!decal && frame == PictureSettings.FRAME_NONE) edges(canvasSkin, u0, v0, u1, v1, 0, PX);
        }
        if (frame != PictureSettings.FRAME_NONE) {
            texture(ATLAS, borderSkin.atlas());
            setTint(borderSkin.r, borderSkin.g, borderSkin.b);
            if (frame == PictureSettings.FRAME_EDGES) {
                edges(borderSkin, u0, v0, u1, v1, 0, PX);
            } else {
                molding(borderSkin, u0, v0, u1, v1, base, tile.getMoldingBlocks());
            }
        }
        // Цвет материала (трава, листва) красит только холст и рамку — не картинку и не следующую картину.
        setTint(1f, 1f, 1f);

        // 2. Картинка (без холста — прижата к стене, пластина — объёмная, сама по себе; торцы в рамке закрывает рамка)
        if (hasPicture) {
            double d = decal ? DECAL_DEPTH : plate ? PX : PX + 0.002;
            // Кадр анимации — по реальным часам (см. getCurrentTexture): при просадках TPS анимация не тормозит и не дёргается.
            texture(decal ? DECAL : PICTURE, shown.getCurrentTexture());
            double pu0 = u0 + shown.x0, pv0 = v0 + shown.y0, pu1 = u0 + shown.x1, pv1 = v0 + shown.y1;
            picture(pu0, pv0, pu1, pv1, d);
            if (plate) {
                picture(pu0, pv0, pu1, pv1, 0);
                if (frame == PictureSettings.FRAME_NONE) plateEdges(shown, pu0, pv0, pu1, pv1);
            }
        }

        // 3. Картина NSFW, скрытая настройкой hideNsfw: по центру холста красный значок "18+" (1x1 блок, не больше холста) -
        // чтобы её не путали с картиной, которая ещё не загрузилась.
        if (ModConfig.hides(tile.nsfw)) nsfwMark(u0, v0, u1, v1, base);
        finish();
    }

    // --------------------------------------------------------------------- Что показывать

    /** Что показывает картина: текущий слайд, если он уже стилизован, иначе — то, что было на экране раньше (чтобы при смене слайда или настроек картинка не пропадала на время обработки). Заодно готовит следующий слайд, выкладывает на сервер картинки с этого компьютера (world — измерение для ImageSync.autoOffer) и запоминает показанное в tile.clientRenderState. time — время мира в тиках. */
    public static PictureManager.Variant shown(PictureSettings tile, Object world, int entityId, long time) {
        PictureManager.Variant shown = choose(tile, world, entityId, time);
        tile.clientRenderState = shown;
        if (shown != null && shown.hasTexture()) shown.touch();
        return shown;
    }

    private static PictureManager.Variant choose(PictureSettings tile, Object world, int entityId, long time) {
        String key = tile.getSourceKey();
        if (key == null) return null;
        if (!tile.hasSharedFiles() && !tile.isNetworkSource()) ImageSync.autoOffer(world, entityId, tile);
        // Картина с пометкой NSFW, а у игрока в конфиге hideNsfw: вместо картинки пустой холст, картинки не скачиваются.
        if (ModConfig.hides(tile.nsfw)) return null;

        PictureManager.Source source = PictureManager.get(key);
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

    // --------------------------------------------------------------------- Части картины

    /** Картинка с плавным освещением, как у ванильных блоков: свет считается в каждой вершине сетки (среднее по соседним клеткам) и плавно перетекает по поверхности — без "ступенек" по блокам. */
    private void picture(double u0, double v0, double u1, double v1, double d) {
        lightCache.clear();
        r = g = b = 1f;
        for (int cu = (int) Math.floor(u0); cu < u1; cu++) {
            for (int cv = (int) Math.floor(v0); cv < v1; cv++) {
                double a = Math.max(u0, cu), bu = Math.min(u1, cu + 1);
                double c = Math.max(v0, cv), e = Math.min(v1, cv + 1);
                if (bu - a < 1e-6 || e - c < 1e-6) continue;
                double ta = (a - u0) / (u1 - u0), tb = (bu - u0) / (u1 - u0);
                double tc = 1 - (c - v0) / (v1 - v0), te = 1 - (e - v0) / (v1 - v0);
                light = glowing(smoothLight(a, c, d));
                vertex(a, c, d, ta, tc);
                light = glowing(smoothLight(bu, c, d));
                vertex(bu, c, d, tb, tc);
                light = glowing(smoothLight(bu, e, d));
                vertex(bu, e, d, tb, te);
                light = glowing(smoothLight(a, e, d));
                vertex(a, e, d, ta, te);
            }
        }
    }

    /** Свет в точке стены: среднее (отдельно небо и источники) по четырём клеткам вокруг, непрозрачные пропускаем. */
    private int smoothLight(double u, double v, double d) {
        long key = Double.doubleToLongBits(u) * 31 + Double.doubleToLongBits(v);
        Integer cached = lightCache.get(key);
        if (cached != null) return cached;

        int sky = 0, block = 0, n = 0;
        for (int i = 0; i < 4; i++) {
            double[] p = CanvasGeometry.toBlock(facing, rotation, u + ((i & 1) == 0 ? -0.5 : 0.5),
                v + ((i & 2) == 0 ? -0.5 : 0.5), d + 0.05);
            int x = floor(ox + p[0]), y = floor(oy + p[1]), z = floor(oz + p[2]);
            if (opaqueAt(x, y, z)) continue;
            int br = lightAt(x, y, z);
            sky += (br >> 16) & 0xFFFF;
            block += br & 0xFFFF;
            n++;
        }
        int result = n == 0 ? lightAt(fx, fy, fz) : (sky / n) << 16 | (block / n);
        lightCache.put(key, result);
        return result;
    }

    /** Значок "18+" по центру холста: квадрат в блок (у холста меньше блока — по его меньшей стороне), чуть над лицевой плоскостью base. */
    private void nsfwMark(double u0, double v0, double u1, double v1, double base) {
        double half = Math.min(1, Math.min(u1 - u0, v1 - v0)) / 2;
        double cu = (u0 + u1) / 2, cv = (v0 + v1) / 2, d = base + 0.002;
        texture(MARK, null);
        light(cu, cv, d + 0.05, 1f);
        vertex(cu - half, cv - half, d, 0, 1);
        vertex(cu + half, cv - half, d, 1, 1);
        vertex(cu + half, cv + half, d, 1, 0);
        vertex(cu - half, cv + half, d, 0, 0);
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
    private void canvas(Skin icon, double u0, double v0, double u1, double v1, double d) {
        for (int cu = (int) Math.floor(u0); cu < u1; cu++) {
            for (int cv = (int) Math.floor(v0); cv < v1; cv++) {
                double a = Math.max(u0, cu), bu = Math.min(u1, cu + 1);
                double c = Math.max(v0, cv), e = Math.min(v1, cv + 1);
                if (bu - a < 1e-6 || e - c < 1e-6) continue;
                light((a + bu) / 2, (c + e) / 2, d + 0.05, 1f);
                double ia = icon.u((a - cu) * 16), ib = icon.u((bu - cu) * 16);
                double ic = icon.v((cv + 1 - c) * 16), ie = icon.v((cv + 1 - e) * 16);
                vertex(a, c, d, ia, ic);
                vertex(bu, c, d, ib, ic);
                vertex(bu, e, d, ib, ie);
                vertex(a, e, d, ia, ie);
            }
        }
    }

    /** Торцы холста от d0 до d1, обшитые деревом рамки (вид "Панель" и внешние торцы багета). Берём древесину из середины текстуры рамки: полоса шириной в толщину торца. */
    private void edges(Skin icon, double u0, double v0, double u1, double v1, double d0, double d1) {
        double rows = (d1 - d0) * 16;
        // верх и низ
        alongU(icon, u0, u1, v1, d0, d1, 6, rows, 0.05, 1.0f);
        alongU(icon, u0, u1, v0, d0, d1, 6, rows, -0.05, 0.55f);
        // лево и право
        alongV(icon, v0, v1, u0, d0, d1, rows, true, true, -0.05, 0.8f);
        alongV(icon, v0, v1, u1, d0, d1, rows, false, true, 0.05, 0.8f);
    }

    /** Багет: брусья шириной width заходят на холст и выступают над картинкой (base — лицевая плоскость холста). Лицевая сторона: по внешнему краю — кайма из краёв и углов текстуры рамки (не шире RIM), внутри неё — середина текстуры без шва (у боковых брусьев повёрнута, чтобы волокна шли вдоль бруса). */
    private void molding(Skin icon, double u0, double v0, double u1, double v1, double base, double width) {
        double bw = Math.min(width, Math.min((u1 - u0) / 2, (v1 - v0) / 2));
        double top = 2 * PX; // лицевая плоскость рамки
        double rim = Math.min(bw, RIM);
        double px = rim * 16;  // ширина каймы в пикселях текстуры
        double far = 16 - px;  // справа и снизу кайма кончается у края текстуры

        // Внешние торцы — на всю толщину
        edges(icon, u0, v0, u1, v1, 0, top);

        // Кайма: углы — углы текстуры, стороны — её края, вдоль повторяется середина (12 пикселей)
        front(icon, u0, v1 - rim, u0 + rim, v1, top, 0, 0, px, px);
        front(icon, u1 - rim, v1 - rim, u1, v1, top, far, 0, 16, px);
        front(icon, u0, v0, u0 + rim, v0 + rim, top, 0, far, px, 16);
        front(icon, u1 - rim, v0, u1, v0 + rim, top, far, far, 16, 16);
        tiledFront(icon, u0 + rim, v1 - rim, u1 - rim, v1, top, 2, 2, 12, 0, 0, 16);
        tiledFront(icon, u0 + rim, v0, u1 - rim, v0 + rim, top, 2, 2, 12, far, 0, 16);
        tiledFront(icon, u0, v0 + rim, u0 + rim, v1 - rim, top, 0, 0, 16, 2, 2, 12);
        tiledFront(icon, u1 - rim, v0 + rim, u1, v1 - rim, top, far, 0, 16, 2, 2, 12);

        // Внутри каймы: верхний и нижний брусья — во всю ширину, боковые — между ними
        tiledFront(icon, u0 + rim, v1 - bw, u1 - rim, v1 - rim, top, 2, 2, 12, 2, 2, 12);
        tiledFront(icon, u0 + rim, v0 + rim, u1 - rim, v0 + bw, top, 2, 2, 12, 2, 2, 12);
        sideBar(icon, u0 + rim, v0 + bw, u0 + bw, v1 - bw, top);
        sideBar(icon, u1 - bw, v0 + bw, u1 - rim, v1 - bw, top);

        // Внутренние торцы брусьев — ступенька от картинки до лицевой стороны рамки
        double ib0 = u0 + bw, ib1 = u1 - bw, jb0 = v0 + bw, jb1 = v1 - bw;
        if (ib1 > ib0 && jb1 > jb0) {
            double rows = (top - base) * 16;
            alongU(icon, ib0, ib1, jb1, base, top, 6, rows, -0.05, 0.55f);
            alongU(icon, ib0, ib1, jb0, base, top, 6, rows, 0.05, 1.0f);
            alongV(icon, jb0, jb1, ib0, base, top, rows, false, false, 0.05, 0.8f);
            alongV(icon, jb0, jb1, ib1, base, top, rows, true, false, -0.05, 0.8f);
        }
    }

    /** Лицевой прямоугольник (плоскость d), текстура — пиксели (pu0, pv0)-(pu1, pv1) спрайта. */
    private void front(Skin icon, double u0, double v0, double u1, double v1, double d,
                       double pu0, double pv0, double pu1, double pv1) {
        light((u0 + u1) / 2, (v0 + v1) / 2, d + 0.05, 1f);
        double ia = icon.u(pu0), ib = icon.u(pu1);
        double ic = icon.v(pv1), ie = icon.v(pv0);
        vertex(u0, v0, d, ia, ic);
        vertex(u1, v0, d, ib, ic);
        vertex(u1, v1, d, ib, ie);
        vertex(u0, v1, d, ia, ie);
    }

    /** Лицевой прямоугольник с повторяющейся текстурой: вправо от u0 — пиксели спрайта с tu, повтор [su, su + nu); вниз от v1 — с tv, повтор [sv, sv + nv). На границах повторов прямоугольник режется, чтобы не выйти за спрайт. */
    private void tiledFront(Skin icon, double u0, double v0, double u1, double v1, double d,
                            double tu, double su, double nu, double tv, double sv, double nv) {
        double a = u0;
        for (double ta = wrap(tu, su, nu); a < u1 - 1e-6; ta = su) {
            double bu = Math.min(u1, a + (su + nu - ta) / 16);
            double e = v1;
            for (double te = wrap(tv, sv, nv); e > v0 + 1e-6; te = sv) {
                double c = Math.max(v0, e - (sv + nv - te) / 16);
                front(icon, a, c, bu, e, d, ta, te, ta + (bu - a) * 16, te + (e - c) * 16);
                e = c;
            }
            a = bu;
        }
    }

    /** Боковой брус багета внутри каймы: середина текстуры (пиксели 2..14) с повтором, повёрнутая на четверть оборота — её строки (волокна) идут вдоль бруса, сверху вниз. */
    private void sideBar(Skin icon, double u0, double v0, double u1, double v1, double d) {
        for (double e = v1; e > v0 + 1e-6; ) {
            double c = Math.max(v0, e - TILE);
            for (double a = u0; a < u1 - 1e-6; ) {
                double bu = Math.min(u1, a + TILE);
                light((a + bu) / 2, (c + e) / 2, d + 0.05, 1f);
                double x0 = icon.u(2), x1 = icon.u(2 + (e - c) * 16);
                double y0 = icon.v(2), y1 = icon.v(2 + (bu - a) * 16);
                vertex(a, c, d, x1, y0);
                vertex(bu, c, d, x1, y1);
                vertex(bu, e, d, x0, y1);
                vertex(a, e, d, x0, y0);
                a = bu;
            }
            e = c;
        }
    }

    /** Пиксель t, приведённый в повтор [start, start + size). */
    private static double wrap(double t, double start, double size) {
        double rest = ((t - start) % size + size) % size;
        return start + (size - rest < 1e-6 ? 0 : rest);
    }

    /** Горизонтальный торец (плоскость v = const) вдоль u, повтор текстуры по 12 пикселей. */
    private void alongU(Skin icon, double u0, double u1, double v, double d0, double d1,
                        double row, double rows, double dv, float shade) {
        for (double s = u0; s < u1 - 1e-6; s += TILE) {
            double e = Math.min(s + TILE, u1);
            light((s + e) / 2, v + dv, (d0 + d1) / 2, shade);
            double ia = icon.u(2), ib = icon.u(2 + (e - s) * 16);
            double ic = icon.v(row), ie = icon.v(row + rows);
            vertex(s, v, d0, ia, ic);
            vertex(e, v, d0, ib, ic);
            vertex(e, v, d1, ib, ie);
            vertex(s, v, d1, ia, ie);
        }
    }

    /** Вертикальный торец (плоскость u = const) вдоль v. Текстура стоит как на лицевой стороне — верхом вверх: у наружного торца (fromTop) верхний блок берёт её верх (у травы — зелёная кайма), ниже — и у внутренних целиком — повторяется середина, как у лицевых брусьев. Поперёк — cols столбцов у края текстуры (left — левого), чтобы угол продолжал лицевую сторону без шва. */
    private void alongV(Skin icon, double v0, double v1, double u, double d0, double d1,
                        double cols, boolean left, boolean fromTop, double du, float shade) {
        double outer = left ? 0 : 16, inner = left ? cols : 16 - cols; // у лица (d1) — крайний столбец
        boolean first = fromTop;
        for (double s = v1; s > v0 + 1e-6; first = false) {
            double e = Math.max(s - (first ? 1 : TILE), v0);
            double t0 = first ? 0 : 2, t1 = t0 + (s - e) * 16;
            light(u + du, (s + e) / 2, (d0 + d1) / 2, shade);
            double ia = icon.u(inner), ib = icon.u(outer);
            double ic = icon.v(t0), ie = icon.v(t1);
            vertex(u, s, d0, ia, ic);
            vertex(u, e, d0, ia, ie);
            vertex(u, e, d1, ib, ie);
            vertex(u, s, d1, ib, ic);
            s = e;
        }
    }

    // --------------------------------------------------------------------- Вершины и свет

    private void vertex(double u, double v, double d, double tu, double tv) {
        double[] p = CanvasGeometry.toBlock(facing, rotation, u, v, d);
        emit(p[0], p[1], p[2], tu, tv);
    }

    /** Освещение клетки, в которой лежит точка (u, v, d), и затенение грани как у ванильных блоков. */
    private void light(double u, double v, double d, float shade) {
        double[] p = CanvasGeometry.toBlock(facing, rotation, u, v, d);
        int brightness = lightAt(floor(ox + p[0]), floor(oy + p[1]), floor(oz + p[2]));
        if (brightness == 0) brightness = lightAt(fx, fy, fz);
        light = glowing(brightness);
        r = shade * tintR;
        g = shade * tintG;
        b = shade * tintB;
    }

    /** Светящаяся картина не темнее своего свечения: блочный свет не ниже него, как у светящихся блоков (небесный — как есть). */
    private int glowing(int packed) {
        return (packed & 0xFFFF) < glow << 4 ? (packed & 0xFFFF0000) | glow << 4 : packed;
    }

    private void setTint(float r, float g, float b) {
        tintR = r;
        tintG = g;
        tintB = b;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
