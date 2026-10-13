package com.grihail.minepapers.picture;

/** Слот материала в окне настройки: в нём лежит по блоку на каждую настраиваемую картину — у стопки картин-предметов столько блоков, сколько картин, у висящей картины один. Клик меняет материал у нескольких картин сразу; если изменились не все картины стопки, изменённые отделяются от неё и уходят в инвентарь. */
public final class MaterialStack {

    private MaterialStack() {}

    /** Можно ли менять материалы: у одной картины — всегда, у стопки — если сервер это разрешает (ModConfig.stackMaterials). */
    public static boolean editable(boolean stackMaterials, int pictures) {
        return stackMaterials || pictures <= 1;
    }

    /** Сколько блоков забрать из слота (столько картин останутся без материала): левой кнопкой — все, правой — половину с округлением вверх, как в ванильных слотах. Не больше, чем поместится в руку. */
    public static int takeCount(int pictures, boolean half, int maxStack) {
        int n = half ? (pictures + 1) / 2 : pictures;
        return Math.max(1, Math.min(n, maxStack));
    }

    /** Сколько картин получат блок из руки: левой кнопкой — сколько блоков в руке, правой — одна. Не больше, чем картин. */
    public static int putCount(int pictures, int carried, boolean one) {
        return Math.max(0, Math.min(pictures, one ? 1 : carried));
    }
}
