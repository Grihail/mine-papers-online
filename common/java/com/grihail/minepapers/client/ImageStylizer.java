package com.grihail.minepapers.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.grihail.minepapers.picture.PictureSettings;

/** Готовит текстуру под конкретный холст: вписывание по правилам CSS object-fit (от центра), пикселизация до N пикселей на блок и, при желании, перевод в палитру цветов карт Minecraft. Работает в фоновом потоке, OpenGL не трогает. */
public final class ImageStylizer {

    /** Предельный размер итоговой текстуры по большей стороне. */
    private static final int MAX_TEXTURE = 1024;

    /** Оттенки цветов карт, доступные в выживании (180, 220, 255 из 255). */
    private static final int[] MAP_SHADES = { 180, 220, 255 };

    /** Сила мягкого дизеринга (доля переносимой ошибки). */
    private static final float SOFT_DITHER = 0.45f;

    /** Цвета карт во всех оттенках; базовые цвета даёт версия (ClientHost.mapColors). */
    private static volatile int[] palette;
    /** Кэш "15-битный цвет -> ближайший цвет палитры" (0 = ещё не посчитано). */
    private static final int[] NEAREST_CACHE = new int[1 << 15];

    private ImageStylizer() {}

    /** Параметры, от которых зависит итоговая текстура. */
    public static final class Layout {
        final double canvasW, canvasH;
        /** Внутренний отступ картинки от краёв холста, в блоках. */
        final double padding;
        /** ppb — пикселей текстуры на блок; naturalPpb — для режимов none / scale-down (у картины в мире они равны). */
        final double ppb, naturalPpb;
        final int fit, style;
        final String key;

        Layout(double canvasW, double canvasH, double padding, int fit, double ppb, double naturalPpb, int style) {
            this.canvasW = canvasW;
            this.canvasH = canvasH;
            this.padding = padding;
            this.fit = fit;
            this.ppb = ppb;
            this.naturalPpb = naturalPpb;
            this.style = style;
            this.key = String.format(Locale.ROOT, "%.4f|%.4f|%.4f|%d|%.4f|%.4f|%d", canvasW, canvasH, padding, fit, ppb,
                naturalPpb, style);
        }

        /** Та же картина, уменьшенная так, что большая сторона холста — iconPixels пикселей (для иконки предмета). */
        public static Layout icon(PictureSettings tile, int iconPixels) {
            double w = tile.getWidthBlocks(), h = tile.getHeightBlocks();
            return new Layout(w, h, tile.getPaddingBlocks(), tile.fitMode, iconPixels / Math.max(w, h), tile.pixelsPerBlock,
                tile.style);
        }

        /** Размер холста в блоках: 0 по одной из сторон = подобрать по пропорциям картинки. */
        public static Layout of(PictureSettings tile, int imageW, int imageH) {
            double w = tile.getWidthBlocks();
            double h = tile.getHeightBlocks();
            double aspect = (double) imageH / imageW;
            if (w <= 0 && h <= 0) w = 1;
            if (h <= 0) h = w * aspect;
            else if (w <= 0) w = h / aspect;
            return new Layout(w, h, tile.getPaddingBlocks(), tile.fitMode, tile.pixelsPerBlock, tile.pixelsPerBlock,
                tile.style);
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
        // Картинка вписывается в холст за вычетом отступа; в конце сдвигаем её обратно на отступ.
        double pad = l.padding;
        double canvasW = l.canvasW - 2 * pad, canvasH = l.canvasH - 2 * pad;
        if (canvasW < 1e-6 || canvasH < 1e-6) {
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

        // 2. Картинка центрирована на холсте, всё, что вылезает за холст, обрезается.
        double rx0 = (canvasW - dw) / 2, ry0 = (canvasH - dh) / 2;
        double vx0 = Math.max(0, rx0), vx1 = Math.min(canvasW, rx0 + dw);
        double vy0 = Math.max(0, ry0), vy1 = Math.min(canvasH, ry0 + dh);
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
        if (l.style == PictureSettings.STYLE_MAP) {
            quantize(out, outW, outH, 0f);
        } else if (l.style == PictureSettings.STYLE_MAP_DITHER) {
            quantize(out, outW, outH, 1f);
        } else if (l.style == PictureSettings.STYLE_MAP_SOFT_DITHER) {
            quantize(out, outW, outH, SOFT_DITHER);
        }

        res.pixels = out;
        res.width = outW;
        res.height = outH;
        res.x0 = (float) (pad + vx0);
        res.y0 = (float) (pad + vy0);
        res.x1 = (float) (pad + vx1);
        res.y1 = (float) (pad + vy1);
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

    // --------------------------------------------------------------------- Палитра карт

    /** Перевод в палитру карт с дизерингом Флойда — Стейнберга. @param strength доля ошибки, которая переносится на соседей: 0 — без дизеринга, 1 — полный (зернистый), меньше 1 — мягкий: меньше шума, но градиенты чуть ступенчатее. */
    private static void quantize(int[] px, int w, int h, float strength) {
        float[] errCur = new float[(w + 2) * 3];
        float[] errNext = new float[(w + 2) * 3];
        boolean dither = strength > 0;
        for (int y = 0; y < h; y++) {
            // Змейкой (чётные строки слева направо, нечётные справа налево) — без "полос" в одну сторону.
            boolean ltr = (y & 1) == 0;
            int dir = ltr ? 3 : -3;
            for (int k = 0; k < w; k++) {
                int x = ltr ? k : w - 1 - k;
                int i = y * w + x;
                int c = px[i];
                if ((c >>> 24) < 128) {
                    px[i] = 0; // на картах нет полупрозрачности
                    continue;
                }
                int e = (x + 1) * 3;
                int r = clamp255(((c >> 16) & 0xFF) + (dither ? errCur[e] : 0));
                int g = clamp255(((c >> 8) & 0xFF) + (dither ? errCur[e + 1] : 0));
                int b = clamp255((c & 0xFF) + (dither ? errCur[e + 2] : 0));
                int q = nearest(r, g, b);
                px[i] = 0xFF000000 | q;
                if (dither) {
                    float dr = (r - ((q >> 16) & 0xFF)) * strength;
                    float dg = (g - ((q >> 8) & 0xFF)) * strength;
                    float db = (b - (q & 0xFF)) * strength;
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
