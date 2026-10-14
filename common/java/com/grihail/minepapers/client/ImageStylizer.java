package com.grihail.minepapers.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.grihail.minepapers.picture.PictureSettings;

/** Готовит текстуру под конкретный холст: вписывание по правилам CSS object-fit (от центра), пикселизация до N пикселей на блок, перевод в палитру карт Minecraft или постеризация — заданное число цветов — с дизерингом и, для резких пикселей, прозрачность без полутонов. Работает в фоновом потоке, OpenGL не трогает. */
public final class ImageStylizer {

    /** Предельный размер итоговой текстуры по большей стороне. */
    private static final int MAX_TEXTURE = 1024;

    /** Оттенки цветов карт, доступные в выживании (180, 220, 255 из 255). */
    private static final int[] MAP_SHADES = { 180, 220, 255 };

    /** Цвета карт во всех оттенках (см. palette). */
    private static volatile int[] palette;
    /** Кэш "15-битный цвет -> ближайший цвет палитры" (0 = ещё не посчитано). */
    private static final int[] NEAREST_CACHE = new int[1 << 15];

    private ImageStylizer() {}

    /** Параметры, от которых зависит итоговая текстура. */
    public static final class Layout {
        final double canvasW, canvasH;
        /** Внутренний отступ картинки от краёв холста и масштаб (больше нуля — крупнее), в блоках. */
        final double padding, zoom;
        /** ppb — пикселей текстуры на блок; naturalPpb — для режимов none / scale-down (у картины в мире они равны). */
        final double ppb, naturalPpb;
        /** Число цветов (ступеней у каждого канала; 0 — палитра карт), сила дизеринга в процентах и его вид (PictureSettings.DITHER_*). */
        final int fit, colors, dither, ditherMode;
        /** Резкие пиксели: пиксель либо непрозрачен, либо прозрачен целиком. */
        final boolean sharp;
        final String key;

        Layout(double canvasW, double canvasH, double padding, int fit, double ppb, double naturalPpb, PictureSettings tile) {
            this.canvasW = canvasW;
            this.canvasH = canvasH;
            this.padding = padding;
            this.zoom = tile.getZoomBlocks();
            this.fit = fit;
            this.ppb = ppb;
            this.naturalPpb = naturalPpb;
            this.colors = tile.mapPalette ? 0 : tile.colors;
            this.dither = tile.dither;
            this.ditherMode = tile.ditherMode;
            this.sharp = tile.sharpPixels;
            // Ключ собирается каждый кадр для каждой картины и иконки — без String.format, дроби с точностью до 1/10000.
            this.key = new StringBuilder(64).append(tenThousandths(canvasW)).append('|').append(tenThousandths(canvasH))
                .append('|').append(tenThousandths(padding)).append('|').append(tenThousandths(zoom))
                .append('|').append(fit).append('|').append(tenThousandths(ppb))
                .append('|').append(tenThousandths(naturalPpb)).append('|').append(colors).append('|').append(dither)
                .append('|').append(ditherMode).append('|').append(sharp ? 1 : 0).toString();
        }

        private static long tenThousandths(double v) {
            return Math.round(v * 10000);
        }

        /** Та же картина, уменьшенная так, что большая сторона холста — iconPixels пикселей (для иконки предмета). */
        public static Layout icon(PictureSettings tile, int iconPixels) {
            double w = tile.getWidthBlocks(), h = tile.getHeightBlocks();
            return new Layout(w, h, tile.getPaddingBlocks(), tile.fitMode, iconPixels / Math.max(w, h), tile.pixelsPerBlock, tile);
        }

        /** Размер холста в блоках: 0 по одной из сторон = подобрать по пропорциям картинки. */
        public static Layout of(PictureSettings tile, int imageW, int imageH) {
            double w = tile.getWidthBlocks();
            double h = tile.getHeightBlocks();
            double aspect = (double) imageH / imageW;
            if (w <= 0 && h <= 0) w = 1;
            if (h <= 0) h = w * aspect;
            else if (w <= 0) w = h / aspect;
            return new Layout(w, h, tile.getPaddingBlocks(), tile.fitMode, tile.pixelsPerBlock, tile.pixelsPerBlock, tile);
        }
    }

    /** Готовая текстура и прямоугольник, который она занимает на холсте (в блоках от левого нижнего угла). */
    public static final class Result {
        int[] pixels;
        int width, height;
        float x0, y0, x1, y1;
        boolean empty;
    }

    /** @param src    пиксели исходника (ARGB), возможно уже уменьшенного @param origW  исходная ширина картинки до уменьшения — нужна для режимов none / scale-down */
    public static Result process(int[] src, int srcW, int srcH, int origW, int origH, Layout l) {
        Result res = new Result();
        // Картинка вписывается в холст за вычетом отступа, раздвинутый масштабом с каждой стороны (zoom > 0 — область больше, картинка крупнее; < 0 — мельче). Видна только часть внутри отступа. В конце сдвигаем всё обратно на край области вписывания.
        double pad = l.padding, zoom = l.zoom;
        double origin = pad - zoom;
        double canvasW = l.canvasW - 2 * origin, canvasH = l.canvasH - 2 * origin;
        if (l.canvasW - 2 * pad < 1e-6 || l.canvasH - 2 * pad < 1e-6 || canvasW < 1e-6 || canvasH < 1e-6) {
            res.empty = true;
            return res;
        }

        // 1. Размер картинки на холсте (в блоках) по правилам object-fit. Для none "исходный размер" = 1 пиксель картинки на 1 пиксель стилизации.
        double natural = 1.0 / l.naturalPpb;
        double dw, dh;
        switch (l.fit) {
            case PictureSettings.FIT_FILL:
                dw = canvasW;
                dh = canvasH;
                break;
            case PictureSettings.FIT_CONTAIN: {
                double s = Math.min(canvasW / origW, canvasH / origH);
                dw = origW * s;
                dh = origH * s;
                break;
            }
            case PictureSettings.FIT_NONE:
                dw = origW * natural;
                dh = origH * natural;
                break;
            case PictureSettings.FIT_SCALE_DOWN: {
                double s = Math.min(natural, Math.min(canvasW / origW, canvasH / origH));
                dw = origW * s;
                dh = origH * s;
                break;
            }
            case PictureSettings.FIT_COVER:
            default: {
                double s = Math.max(canvasW / origW, canvasH / origH);
                dw = origW * s;
                dh = origH * s;
                break;
            }
        }

        // 2. Картинка центрирована на холсте, всё, что вылезает за область вписывания (и за отступ, если масштаб раздвинул её дальше), обрезается.
        double cut = Math.max(0, zoom);
        double rx0 = (canvasW - dw) / 2, ry0 = (canvasH - dh) / 2;
        double vx0 = Math.max(cut, rx0), vx1 = Math.min(canvasW - cut, rx0 + dw);
        double vy0 = Math.max(cut, ry0), vy1 = Math.min(canvasH - cut, ry0 + dh);
        if (vx1 - vx0 < 1e-6 || vy1 - vy0 < 1e-6) {
            res.empty = true;
            return res;
        }

        // Видимая часть в координатах исходника (строки сверху вниз).
        double sx0 = (vx0 - rx0) / dw * srcW, sx1 = (vx1 - rx0) / dw * srcW;
        double sy0 = (ry0 + dh - vy1) / dh * srcH, sy1 = (ry0 + dh - vy0) / dh * srcH;

        // 3. Размер текстуры: ppb пикселей на блок, но не крупнее, чем есть деталей в исходнике, и не больше лимита.
        double tw = (vx1 - vx0) * l.ppb, th = (vy1 - vy0) * l.ppb;
        double detail = Math.max((sx1 - sx0) / tw, (sy1 - sy0) / th);
        if (detail < 1) {
            tw *= detail;
            th *= detail;
        }
        double limit = MAX_TEXTURE / Math.max(tw, th);
        if (limit < 1) {
            tw *= limit;
            th *= limit;
        }
        int outW = Math.max(1, (int) Math.round(tw));
        int outH = Math.max(1, (int) Math.round(th));

        int[] out = resample(src, srcW, srcH, sx0, sy0, sx1, sy1, outW, outH);
        if (l.sharp) sharpen(out);
        posterize(out, outW, outH, l.colors, l.dither / (float) PictureSettings.MAX_DITHER, l.colors == 0,
            l.ditherMode == PictureSettings.DITHER_ORDERED);

        res.pixels = out;
        res.width = outW;
        res.height = outH;
        res.x0 = (float) (origin + vx0);
        res.y0 = (float) (origin + vy0);
        res.x1 = (float) (origin + vx1);
        res.y1 = (float) (origin + vy1);
        return res;
    }

    // --------------------------------------------------------------------- Ресемплинг: усреднение по площади (с учётом прозрачности) при уменьшении, ближайший пиксель при увеличении.

    private static int[] resample(int[] src, int sw, int sh, double sx0, double sy0, double sx1, double sy1, int tw, int th) {
        int[] out = new int[tw * th];
        double stepX = (sx1 - sx0) / tw, stepY = (sy1 - sy0) / th;
        int[] xa = new int[tw], xb = new int[tw];
        for (int i = 0; i < tw; i++) {
            int[] span = span(sx0 + i * stepX, stepX, sw);
            xa[i] = span[0];
            xb[i] = span[1];
        }
        for (int j = 0; j < th; j++) {
            int[] spanY = span(sy0 + j * stepY, stepY, sh);
            for (int i = 0; i < tw; i++) {
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int y = spanY[0]; y < spanY[1]; y++) {
                    int row = y * sw;
                    for (int x = xa[i]; x < xb[i]; x++) {
                        int c = src[row + x];
                        int ca = c >>> 24;
                        a += ca;
                        r += ((c >> 16) & 0xFF) * ca;
                        g += ((c >> 8) & 0xFF) * ca;
                        b += (c & 0xFF) * ca;
                        n++;
                    }
                }
                if (a == 0 || n == 0) {
                    out[j * tw + i] = 0;
                } else {
                    out[j * tw + i] = (int) (a / n) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
                }
            }
        }
        return out;
    }

    /** Резкие пиксели: полупрозрачные края (сглаженные края букв и фигур на прозрачном фоне, смешанные при уменьшении пиксели) не размываются по стене — пиксель видимый наполовину и больше становится непрозрачным, остальные исчезают. */
    private static void sharpen(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            px[i] = (c >>> 24) < 128 ? 0 : c | 0xFF000000;
        }
    }

    /** Диапазон исходных пикселей [from, to) для одного выходного. */
    private static int[] span(double start, double step, int size) {
        int from, to;
        if (step < 1) {
            from = (int) Math.floor(start + step / 2);
            to = from + 1;
        } else {
            from = (int) Math.floor(start);
            to = (int) Math.ceil(start + step);
        }
        from = Math.max(0, Math.min(size - 1, from));
        to = Math.max(from + 1, Math.min(size, to));
        return new int[] { from, to };
    }

    // --------------------------------------------------------------------- Постеризация

    /** Матрица Байера 4x4 для упорядоченного дизеринга: порог каждого пикселя по его месту в квадрате 4x4. */
    private static final int[][] BAYER4 = { { 0, 8, 2, 10 }, { 12, 4, 14, 6 }, { 3, 11, 1, 9 }, { 15, 7, 13, 5 } };
    /** Упорядоченный дизеринг в палитру карт: размах сдвига — примерно расстояние между соседними цветами палитры (оттенки одного цвета карты отстоят на 35-40). */
    private static final float PALETTE_SPREAD = 40;

    /** Перевод в меньшее число цветов с дизерингом: в палитру карт (mapPalette) или постеризация — у каждого канала levels ровных ступеней от 0 до 255. Дизеринг — диффузия ошибки (Флойд - Стейнберг змейкой: плавно, точки вразброс) или упорядоченный (ordered: к пикселю добавляется сдвиг из матрицы Байера по его месту — точки ровной сеткой). Прозрачность не меняется (на картах её нет — там пиксель либо виден, либо прозрачен), полностью прозрачные пиксели пропускаются. @param strength сила дизеринга: 0 — без него (ровные пятна), 1 — полный (зернистый, зато плавные переходы). */
    private static void posterize(int[] px, int w, int h, int levels, float strength, boolean mapPalette, boolean ordered) {
        float step = mapPalette ? 0 : 255f / (levels - 1);
        float amplitude = (mapPalette ? PALETTE_SPREAD : step) * strength;
        float[] errCur = new float[(w + 2) * 3];
        float[] errNext = new float[(w + 2) * 3];
        boolean diffuse = strength > 0 && !ordered;
        for (int y = 0; y < h; y++) {
            // Змейкой (чётные строки слева направо, нечётные справа налево) — без "полос" в одну сторону.
            boolean ltr = (y & 1) == 0;
            int dir = ltr ? 3 : -3;
            for (int k = 0; k < w; k++) {
                int x = ltr ? k : w - 1 - k;
                int i = y * w + x;
                int c = px[i];
                if (mapPalette && (c >>> 24) < 128) c = px[i] = 0;
                if ((c >>> 24) == 0) continue;
                int e = (x + 1) * 3;
                float shift = ordered ? ((BAYER4[y & 3][x & 3] + 0.5f) / 16 - 0.5f) * amplitude : 0;
                int r = clamp255(((c >> 16) & 0xFF) + (diffuse ? errCur[e] : shift));
                int g = clamp255(((c >> 8) & 0xFF) + (diffuse ? errCur[e + 1] : shift));
                int b = clamp255((c & 0xFF) + (diffuse ? errCur[e + 2] : shift));
                int qr, qg, qb;
                if (mapPalette) {
                    int q = nearest(r, g, b);
                    qr = (q >> 16) & 0xFF;
                    qg = (q >> 8) & 0xFF;
                    qb = q & 0xFF;
                    c |= 0xFF000000;
                } else {
                    qr = level(r, step);
                    qg = level(g, step);
                    qb = level(b, step);
                }
                px[i] = (c & 0xFF000000) | qr << 16 | qg << 8 | qb;
                if (diffuse) {
                    float dr = (r - qr) * strength;
                    float dg = (g - qg) * strength;
                    float db = (b - qb) * strength;
                    spread(errCur, e + dir, dr, dg, db, 7f / 16);
                    spread(errNext, e - dir, dr, dg, db, 3f / 16);
                    spread(errNext, e, dr, dg, db, 5f / 16);
                    spread(errNext, e + dir, dr, dg, db, 1f / 16);
                }
            }
            float[] t = errCur;
            errCur = errNext;
            errNext = t;
            Arrays.fill(errNext, 0f);
        }
    }

    private static void spread(float[] err, int at, float dr, float dg, float db, float k) {
        err[at] += dr * k;
        err[at + 1] += dg * k;
        err[at + 2] += db * k;
    }

    private static int clamp255(float v) {
        return v < 0 ? 0 : v > 255 ? 255 : (int) v;
    }

    /** Ближайшая ступень канала. */
    private static int level(int v, float step) {
        return Math.min(255, Math.round(Math.round(v / step) * step));
    }

    // --------------------------------------------------------------------- Палитра карт

    /** Ближайший цвет палитры карт. */
    private static int nearest(int r, int g, int b) {
        int key = (r >> 3) << 10 | (g >> 3) << 5 | (b >> 3);
        int cached = NEAREST_CACHE[key];
        if (cached != 0) return cached & 0xFFFFFF;

        // Ищем от центра 15-битной ячейки, чтобы кэш не зависел от порядка запросов.
        int cr = (r & ~7) | 4, cg = (g & ~7) | 4, cb = (b & ~7) | 4;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int p : palette()) {
            int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
            // "redmean" — простая перцептивная метрика
            long rm = (cr + pr) / 2;
            long dr = cr - pr, dg = cg - pg, db = cb - pb;
            long d = (((512 + rm) * dr * dr) >> 8) + 4 * dg * dg + (((767 - rm) * db * db) >> 8);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        NEAREST_CACHE[key] = 0xFF000000 | best;
        return best;
    }

    /** Цвета карт во всех оттенках, доступных в выживании; базовые цвета даёт версия (ClientHost.mapColors). */
    private static int[] palette() {
        int[] p = palette;
        if (p == null) {
            List<Integer> colors = new ArrayList<Integer>();
            for (int base : ClientHost.get().mapColors()) {
                for (int shade : MAP_SHADES) {
                    int r = ((base >> 16) & 0xFF) * shade / 255;
                    int g = ((base >> 8) & 0xFF) * shade / 255;
                    int b = (base & 0xFF) * shade / 255;
                    colors.add(r << 16 | g << 8 | b);
                }
            }
            p = new int[colors.size()];
            for (int i = 0; i < p.length; i++) p[i] = colors.get(i);
            palette = p;
        }
        return p;
    }
}
