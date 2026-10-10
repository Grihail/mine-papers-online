package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Геометрия картины в "координатах стены": u — вправо (как видит смотрящий на картину), v — вверх, d — от стены к зрителю. Начало координат — левый нижний угол клетки рамки у стены, 1 = один блок. Холст занимает u ∈ [offsetX, offsetX + width], v ∈ [offsetY, offsetY + height]. "Стеной" может быть и пол, и потолок: тогда верх картинки смотрит туда, куда смотрел игрок при установке. */
public final class FrameGeometry {

    private FrameGeometry() {}

    /** Направления по горизонтали, как поворот игрока: юг, запад, север, восток. */
    private static final int[][] HORIZONTAL = { { 0, 0, 1 }, { -1, 0, 0 }, { 0, 0, -1 }, { 1, 0, 0 } };
    /** [сторона][поворот] -> {u, v, нормаль} — единичные векторы осей картины в мире. */
    private static final int[][][][] AXES = new int[6][4][][];

    static {
        for (int r = 0; r < 4; r++) {
            int[] right = HORIZONTAL[(r + 1) & 3];
            // Пол: смотришь вниз — верх картинки вдали. Потолок: смотришь вверх — верх картинки за спиной.
            AXES[1][r] = new int[][] { right, HORIZONTAL[r], { 0, 1, 0 } };
            AXES[0][r] = new int[][] { right, HORIZONTAL[(r + 2) & 3], { 0, -1, 0 } };
            AXES[2][r] = new int[][] { { -1, 0, 0 }, { 0, 1, 0 }, { 0, 0, -1 } };
            AXES[3][r] = new int[][] { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };
            AXES[4][r] = new int[][] { { 0, 0, 1 }, { 0, 1, 0 }, { -1, 0, 0 } };
            AXES[5][r] = new int[][] { { 0, 0, -1 }, { 0, 1, 0 }, { 1, 0, 0 } };
        }
    }

    /** Нормаль картины (от стены к зрителю). */
    public static int[] normal(PictureFrame f) {
        return AXES[f.getFacing()][f.getRotation()][2];
    }

    /** Точка в координатах стены -> координаты относительно угла клетки рамки (x, y, z). */
    public static double[] toBlock(int facing, int rotation, double u, double v, double d) {
        int[][] a = AXES[facing][rotation & 3];
        double[] p = new double[3];
        for (int i = 0; i < 3; i++) {
            // По каждой оси мира ровно одна ось картины; отрицательная идёт от дальнего края клетки.
            p[i] = along(a[0][i], u) + along(a[1][i], v) + along(a[2][i], d);
        }
        return p;
    }

    private static double along(int c, double t) {
        return c > 0 ? t : c < 0 ? 1 - t : 0;
    }

    /** Угол клетки рамки в мире, сдвинутый вдоль нормали до поверхности, на которой лежит картина. */
    public static double[] origin(PictureFrame f) {
        int[] n = normal(f);
        return new double[] { f.x + n[0] * f.surface, f.y + n[1] * f.surface, f.z + n[2] * f.surface };
    }

    /** Весь холст в мировых координатах — он же хитбокс картины. */
    public static AxisAlignedBB canvasBox(PictureFrame f) {
        double u0 = f.getOffsetXBlocks(), v0 = f.getOffsetYBlocks();
        double[] b = box(f.getFacing(), f.getRotation(), u0, v0, 0, u0 + f.getWidthBlocks(),
            v0 + f.getHeightBlocks(), f.getDepth());
        double[] o = origin(f);
        return AxisAlignedBB.getBoundingBox(o[0] + b[0], o[1] + b[1], o[2] + b[2], o[0] + b[3], o[1] + b[4], o[2] + b[5]);
    }

    /** Холст нельзя разместить: он прошёл бы сквозь блок или другую картину. */
    public static boolean isBlocked(World world, PictureFrame candidate, Entity self) {
        return hitsBlocks(world, candidate) || overlapping(world, candidate, self) != null;
    }

    /** Холст проходит сквозь коллизию блока. Блоки без коллизии (факелы, трава, цветы) не мешают, касаться граней (стены за картиной) можно. */
    public static boolean hitsBlocks(World world, PictureFrame candidate) {
        double e = 1e-4;
        AxisAlignedBB box = canvasBox(candidate).contract(e, e, e);
        List<AxisAlignedBB> hits = new ArrayList<AxisAlignedBB>();
        int x0 = MathHelper.floor_double(box.minX), x1 = MathHelper.floor_double(box.maxX);
        int y0 = MathHelper.floor_double(box.minY) - 1, y1 = MathHelper.floor_double(box.maxY); // -1: заборы выше блока
        int z0 = MathHelper.floor_double(box.minZ), z1 = MathHelper.floor_double(box.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Block block = world.getBlock(x, y, z);
                    if (block.getMaterial() == Material.air) continue;
                    block.addCollisionBoxesToList(world, x, y, z, box, hits, null);
                    if (!hits.isEmpty()) return true;
                }
            }
        }
        return false;
    }

    /** Другая картина, чей холст пересекается с холстом candidate, или null. Касаться краями можно. @param self сама картина, если меняются её настройки (с собой не сравниваем); null — при установке новой */
    public static EntityPicture overlapping(World world, PictureFrame candidate, Entity self) {
        double e = 1e-4; // погрешность float в размерах и смещениях
        AxisAlignedBB box = canvasBox(candidate).contract(e, e, e);
        for (Object o : world.getEntitiesWithinAABB(EntityPicture.class, box)) {
            EntityPicture other = (EntityPicture) o;
            if (other == self || other.isDead) continue;
            AxisAlignedBB otherBox = canvasBox(other.frame);
            if (box.intersectsWith(otherBox) && !cornerJoint(candidate, box, other.frame, otherBox)) return other;
        }
        return null;
    }

    /** Картины на перпендикулярных поверхностях (две стены, стена и пол...) встречаются во внутреннем углу: их толщины делят угловой столбик. Это разрешено, только если край каждой упирается ровно в поверхность другой. Параллельные картины, крест-накрест и торчащие сквозь другую — по-прежнему пересечение. */
    private static boolean cornerJoint(PictureFrame a, AxisAlignedBB aBox, PictureFrame b, AxisAlignedBB bBox) {
        int aAxis = normalAxis(a), bAxis = normalAxis(b);
        if (aAxis == bAxis) return false;
        return endsAt(aBox, bAxis, wallPlane(b, bBox)) && endsAt(bBox, aAxis, wallPlane(a, aBox));
    }

    /** Ось мира (0 — x, 1 — y, 2 — z), вдоль которой направлена нормаль картины. */
    private static int normalAxis(PictureFrame f) {
        int[] n = normal(f);
        return n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2;
    }

    /** Плоскость поверхности под картиной — задняя грань её хитбокса. */
    private static double wallPlane(PictureFrame f, AxisAlignedBB box) {
        int axis = normalAxis(f);
        return normal(f)[axis] > 0 ? min(box, axis) : max(box, axis);
    }

    /** Край box вдоль оси axis лежит ровно в плоскости plane. */
    private static boolean endsAt(AxisAlignedBB box, int axis, double plane) {
        return Math.abs(min(box, axis) - plane) < 1e-3 || Math.abs(max(box, axis) - plane) < 1e-3;
    }

    private static double min(AxisAlignedBB box, int axis) {
        return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
    }

    private static double max(AxisAlignedBB box, int axis) {
        return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
    }

    /** Параллелепипед из координат стены в координаты клетки {min..., max...}. */
    public static double[] box(int facing, int rotation, double u0, double v0, double d0, double u1, double v1, double d1) {
        double[] a = toBlock(facing, rotation, u0, v0, d0), b = toBlock(facing, rotation, u1, v1, d1);
        return new double[] { Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
            Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]) };
    }
}
