package com.grihail.minepapers.picture;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Пазл: картины одной группы (PictureSettings.puzzle — номер; у заприваченных — среди картин того же владельца), которые висят в одной плоскости с одним поворотом и не дальше MAX_SIZE_BLOCKS друг от друга, складываются в одну картину. Картинка вписывается в их общий прямоугольник — как в один большой холст, — и каждая картина показывает свой кусок; что попало в промежутки между ними, не видно, как у настоящего триптиха. Слайдшоу у всей группы общее. Настройки картинки у группы общие: сервер переносит их на все куски при сохранении любого (PictureSettings.copyPuzzleSettings). Группу каждая картина собирает сама на клиенте: соседей ищет версия (Finder). */
public final class PuzzleGroup {

    /** Больше кусков в группу не берём. */
    public static final int MAX_PIECES = 64;

    /** Картины рядом с областью мира {minX, minY, minZ, maxX, maxY, maxZ} — их ищет версия. */
    public interface Finder {
        List<? extends PictureSettings> near(double[] box);
    }

    /** Общий прямоугольник группы в координатах стены картины, для которой группа собрана. */
    public final double u0, v0, u1, v1;
    /** Зерно слайдшоу всей группы (самое маленькое из зёрен кусков) и число кусков. */
    public final long seed;
    public final int pieces;

    private PuzzleGroup(double u0, double v0, double u1, double v1, long seed, int pieces) {
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.seed = seed;
        this.pieces = pieces;
    }

    public double width() {
        return u1 - u0;
    }

    public double height() {
        return v1 - v0;
    }

    /** Группа картины self или null — пазл выключен, других кусков нет или общий прямоугольник больше MAX_SIZE_BLOCKS. */
    public static PuzzleGroup of(PictureSettings self, Finder finder) {
        if (self.puzzle == 0) return null;
        double m = PictureSettings.MAX_SIZE_BLOCKS;
        double u0 = Double.MAX_VALUE, v0 = Double.MAX_VALUE, u1 = -Double.MAX_VALUE, v1 = -Double.MAX_VALUE;
        long seed = Long.MAX_VALUE;
        int pieces = 0;
        for (PictureSettings other : finder.near(searchBox(self))) {
            if (!sameGroup(self, other)) continue;
            double[] r = rect(self, other);
            u0 = Math.min(u0, r[0]);
            v0 = Math.min(v0, r[1]);
            u1 = Math.max(u1, r[2]);
            v1 = Math.max(v1, r[3]);
            seed = Math.min(seed, other.getSlideSeed());
            if (++pieces >= MAX_PIECES) break;
        }
        if (pieces < 2) return null; // одна картина (сама self) — не пазл
        if (u1 - u0 > m + 1e-6 || v1 - v0 > m + 1e-6) return null;
        // Доли блока — до 1/10000, как в ключе раскладки: у всех кусков прямоугольник (и с ним общая текстура) один и тот же.
        return new PuzzleGroup(round(u0), round(v0), round(u1), round(v1), seed, pieces);
    }

    /** Где искать куски группы картины self: её холст, раздвинутый на MAX_SIZE_BLOCKS во все стороны. */
    public static double[] searchBox(PictureSettings self) {
        double[] b = CanvasGeometry.canvasBounds(self);
        double m = PictureSettings.MAX_SIZE_BLOCKS;
        return new double[] { b[0] - m, b[1] - m, b[2] - m, b[3] + m, b[4] + m, b[5] + m };
    }

    /** Картина b — кусок той же группы, что и a (и сама a тоже): тот же номер и тот же владелец (у обеих без привата — общий номер), та же плоскость и поворот. */
    public static boolean sameGroup(PictureSettings a, PictureSettings b) {
        return a.puzzle != 0 && b.puzzle == a.puzzle && sameOwner(a.owner, b.owner)
            && b.getFacing() == a.getFacing() && b.getRotation() == a.getRotation() && Math.abs(plane(b) - plane(a)) < 1e-3;
    }

    private static boolean sameOwner(UUID a, UUID b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Самый маленький номер группы (от 1), которого нет у картин из pictures с тем же владельцем owner (null — у картин без привата). */
    public static int freeNumber(Iterable<? extends PictureSettings> pictures, UUID owner) {
        Set<Integer> used = new HashSet<Integer>();
        for (PictureSettings p : pictures) {
            if (p.puzzle != 0 && sameOwner(owner, p.owner)) used.add(p.puzzle);
        }
        int n = 1;
        while (used.contains(n)) n++;
        return n;
    }

    /** Холст картины f в координатах стены картины self (у обеих одна сторона и поворот): {u0, v0, u1, v1}. */
    private static double[] rect(PictureSettings self, PictureSettings f) {
        double[] so = CanvasGeometry.origin(self), fo = CanvasGeometry.origin(f);
        double hw = f.getWidthBlocks() / 2, hh = f.getHeightBlocks() / 2;
        // Середина холста f -> мир -> координаты стены self; оси у них одни, так что холст откладывается от неё.
        double[] mid = CanvasGeometry.toBlock(f.getFacing(), f.getRotation(), f.getOffsetXBlocks() + hw, f.getOffsetYBlocks() + hh, 0);
        double[] c = CanvasGeometry.toWall(self.getFacing(), self.getRotation(),
            new double[] { fo[0] + mid[0] - so[0], fo[1] + mid[1] - so[1], fo[2] + mid[2] - so[2] });
        return new double[] { c[0] - hw, c[1] - hh, c[0] + hw, c[1] + hh };
    }

    /** Плоскость картины: координата её поверхности вдоль нормали в мире. */
    private static double plane(PictureSettings f) {
        int[] n = CanvasGeometry.normal(f);
        int axis = n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2;
        return CanvasGeometry.origin(f)[axis] + CanvasGeometry.toBlock(f.getFacing(), f.getRotation(), 0, 0, 0)[axis];
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
