package com.grihail.minepapers.picture;

import java.util.Locale;

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
}
