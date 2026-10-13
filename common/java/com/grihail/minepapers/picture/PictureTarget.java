package com.grihail.minepapers.picture;

/** Что настраивает окно картины — одним числом для пакетов и окна: 0 и больше — id картины-сущности, меньше нуля — картина-предмет в слоте инвентаря игрока (-1 — слот 0, -2 — слот 1 и т.д.). Предмет игрок открывает ПКМ, держа его в руке. */
public final class PictureTarget {

    private PictureTarget() {}

    public static int ofEntity(int entityId) {
        return entityId;
    }

    public static int ofSlot(int slot) {
        return -1 - slot;
    }

    public static boolean isItem(int target) {
        return target < 0;
    }

    /** Слот инвентаря картины-предмета. */
    public static int slot(int target) {
        return -1 - target;
    }
}
