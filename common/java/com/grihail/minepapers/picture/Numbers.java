package com.grihail.minepapers.picture;

import java.util.Locale;
import java.util.regex.Pattern;

/** Числа в окне настроек и в подсказке предмета: разбор ввода и запись без лишних нулей. */
public final class Numbers {

    private Numbers() {}

    /** Размер, округлённый до целого пикселя (не меньше одного): в пикселях или в блоках. */
    public static float snap(float v, boolean px) {
        float pixels = Math.max(1, Math.round(px ? v : v * 16));
        return px ? pixels : pixels / 16f;
    }

    public static float orDefault(Float v, float def) {
        return v != null ? v : def;
    }

    /** Число из поля ввода (запятая тоже годится как разделитель); null — не число. */
    public static Float parse(String s) {
        try {
            float v = Float.parseFloat(s.trim().replace(',', '.'));
            return Float.isNaN(v) || Float.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Для поля ввода: до 4 знаков после точки, без нулей в конце. */
    public static String fmt(float v) {
        if (v == Math.round(v)) return String.valueOf(Math.round(v));
        String s = String.format(Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** Для подсказки предмета: размер в блоках, до 2 знаков после точки. */
    public static String blocks(float blocks) {
        if (blocks == Math.round(blocks)) return String.valueOf(Math.round(blocks));
        return String.format(Locale.ROOT, "%.2f", blocks);
    }

    /** Числовые поля окна настроек. Пределы размера и отступа — в блоках (в пикселях — в 16 раз больше), багета — всегда в пикселях; сдвиг ограничивает сервер, он зависит от размера. */
    public static final Field INTERVAL = new Field(false, 1, 999999),
        SIZE = new Field(true, 0, PictureSettings.MAX_SIZE_BLOCKS),
        OFFSET = new Field(true, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY),
        PADDING = new Field(true, -PictureSettings.MAX_SIZE_BLOCKS / 2, PictureSettings.MAX_SIZE_BLOCKS / 2),
        MOLDING = new Field(false, 0, PictureSettings.MAX_MOLDING);

    /** Числовое поле: дробное ли оно, бывает ли меньше нуля, и пределы, в которых его меняют стрелки и колёсико. */
    public static final class Field {
        private final Pattern typable;
        private final float min, max;

        Field(boolean decimal, float min, float max) {
            this.typable = Pattern.compile((min < 0 ? "-?" : "") + (decimal ? "\\d*([.,]\\d*)?" : "\\d*"));
            this.min = min;
            this.max = max;
        }

        /** Можно ли так набрать: только цифры, у дробного — одна точка или запятая, где бывает меньше нуля — минус в начале. Недописанное ("", "-", "1.") тоже можно. */
        public boolean accepts(String s) {
            return typable.matcher(s).matches();
        }

        /** Значение на шаг dir (1 или -1) больше или меньше, в пределах (unit — 16, если поле в пикселях). Не число в поле — нижний предел или 0. */
        public String step(String s, int dir, float unit) {
            Float v = parse(s);
            float lo = min * unit, hi = max * unit;
            return fmt(Math.max(lo, Math.min(hi, v == null ? 0 : v + dir)));
        }
    }
}
