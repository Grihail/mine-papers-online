package com.grihail.minepapers.tile;

import net.minecraft.util.AxisAlignedBB;

/**
 * Геометрия картины в "координатах стены":
 * u — вправо (как видит смотрящий на картину), v — вверх, d — от стены к зрителю.
 * Начало координат — левый нижний угол блока рамки у стены, 1 = один блок.
 * Холст занимает u ∈ [offsetX, offsetX + width], v ∈ [offsetY, offsetY + height].
 */
public final class FrameGeometry {

    private FrameGeometry() {}

    /** Точка в координатах стены -> координаты относительно угла блока рамки (x, y, z). */
    public static double[] toBlock(int facing, double u, double v, double d) {
        switch (facing) {
            case 2: return new double[] { 1 - u, v, 1 - d };
            case 5: return new double[] { d, v, 1 - u };
            case 4: return new double[] { 1 - d, v, u };
            case 3:
            default: return new double[] { u, v, d };
        }
    }

    /** Мировые координаты блока, который находится в клетке (cu, cv) стены относительно рамки. */
    public static int[] cellPos(TilePictureFrame te, int cu, int cv) {
        double[] p = toBlock(te.getFacing(), cu + 0.5, cv + 0.5, 0.5);
        return new int[] { te.xCoord + (int) Math.floor(p[0]), te.yCoord + (int) Math.floor(p[1]),
            te.zCoord + (int) Math.floor(p[2]) };
    }

    /** Диапазон клеток, которые задевает холст: {cu0, cv0, cu1, cv1} включительно. */
    public static int[] cellRange(TilePictureFrame te) {
        double e = 1e-4;
        double u0 = te.getOffsetXBlocks(), v0 = te.getOffsetYBlocks();
        double u1 = u0 + te.getWidthBlocks(), v1 = v0 + te.getHeightBlocks();
        return new int[] { (int) Math.floor(u0 + e), (int) Math.floor(v0 + e),
            (int) Math.ceil(u1 - e) - 1, (int) Math.ceil(v1 - e) - 1 };
    }

    /**
     * Хитбокс части картины в клетке (cu, cv) в локальных координатах этого блока
     * {minX, minY, minZ, maxX, maxY, maxZ}, или null, если холст клетку не задевает.
     */
    public static double[] cellBounds(TilePictureFrame te, int cu, int cv) {
        double u0 = Math.max(te.getOffsetXBlocks(), cu) - cu;
        double u1 = Math.min(te.getOffsetXBlocks() + te.getWidthBlocks(), cu + 1) - cu;
        double v0 = Math.max(te.getOffsetYBlocks(), cv) - cv;
        double v1 = Math.min(te.getOffsetYBlocks() + te.getHeightBlocks(), cv + 1) - cv;
        if (u1 - u0 < 1e-4 || v1 - v0 < 1e-4) return null;
        return box(te.getFacing(), u0, v0, 0, u1, v1, te.getDepth());
    }

    /** Весь холст в мировых координатах — для обводки при наведении и границ отрисовки. */
    public static AxisAlignedBB canvasBox(TilePictureFrame te) {
        double u0 = te.getOffsetXBlocks(), v0 = te.getOffsetYBlocks();
        double[] b = box(te.getFacing(), u0, v0, 0, u0 + te.getWidthBlocks(), v0 + te.getHeightBlocks(), te.getDepth());
        return AxisAlignedBB.getBoundingBox(te.xCoord + b[0], te.yCoord + b[1], te.zCoord + b[2],
            te.xCoord + b[3], te.yCoord + b[4], te.zCoord + b[5]);
    }

    /** Параллелепипед из координат стены в координаты блока {min..., max...}. */
    public static double[] box(int facing, double u0, double v0, double d0, double u1, double v1, double d1) {
        double[] a = toBlock(facing, u0, v0, d0), b = toBlock(facing, u1, v1, d1);
        return new double[] { Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
            Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]) };
    }
}
