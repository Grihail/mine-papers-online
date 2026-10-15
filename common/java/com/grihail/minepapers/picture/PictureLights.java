package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;

/** Свет светящейся картины: перед холстом стоят обычные блоки света силы её материала свечения (их освещение считает сам мир — и движущиеся конструкции других модов переносят и освещают их как любые блоки). Здесь — какие клетки заняты (смещения от клетки рамки, см. CanvasGeometry.lightCells) и их обновление; ставит и убирает блоки версия. Только на сервере. */
public abstract class PictureLights {

    private final List<int[]> cells = new ArrayList<int[]>();
    private boolean dirty;

    /** Поставить в клетку блок света силы level или поменять силу у уже стоящего блока света. Занятую другим блоком клетку не трогать. @return свет в клетке есть */
    protected abstract boolean place(int x, int y, int z, int level);

    /** Убрать блок света, если он ещё стоит в клетке. */
    protected abstract void remove(int x, int y, int z);

    /** Настройки изменились — свет обновится в следующем тике (при загрузке из сохранения мир ещё не готов). */
    public void markDirty() {
        dirty = true;
    }

    /** Обновить свет, если его пометили (вызывать каждый тик). glow — сила света картины (0 — не светится). */
    public void tick(PictureSettings frame, int glow) {
        if (!dirty) return;
        dirty = false;
        List<int[]> wanted = glow > 0 ? CanvasGeometry.lightCells(frame) : new ArrayList<int[]>();
        for (int[] c : cells) {
            if (!contains(wanted, c)) remove(frame.x + c[0], frame.y + c[1], frame.z + c[2]);
        }
        cells.clear();
        for (int[] c : wanted) {
            if (place(frame.x + c[0], frame.y + c[1], frame.z + c[2], glow)) cells.add(c);
        }
    }

    /** Картину сломали или убрали — убираем и её свет. */
    public void clear(PictureSettings frame) {
        for (int[] c : cells) remove(frame.x + c[0], frame.y + c[1], frame.z + c[2]);
        cells.clear();
    }

    /** Этот блок света поставила эта картина. */
    public boolean owns(PictureSettings frame, int x, int y, int z) {
        return contains(cells, new int[] { x - frame.x, y - frame.y, z - frame.z });
    }

    /** Клетки подряд по три числа — для сохранения. */
    public int[] save() {
        int[] flat = new int[cells.size() * 3];
        for (int i = 0; i < cells.size(); i++) System.arraycopy(cells.get(i), 0, flat, i * 3, 3);
        return flat;
    }

    public void load(int[] flat) {
        cells.clear();
        for (int i = 0; i + 2 < flat.length; i += 3) cells.add(new int[] { flat[i], flat[i + 1], flat[i + 2] });
    }

    private static boolean contains(List<int[]> list, int[] cell) {
        for (int[] c : list) {
            if (c[0] == cell[0] && c[1] == cell[1] && c[2] == cell[2]) return true;
        }
        return false;
    }
}
