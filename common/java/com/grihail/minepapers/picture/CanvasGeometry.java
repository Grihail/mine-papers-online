package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;

/** Геометрия картины в "координатах стены": u — вправо (как видит смотрящий на картину), v — вверх, d — от стены к зрителю. Начало координат — левый нижний угол клетки рамки у стены, 1 = один блок. Холст занимает u ∈ [offsetX, offsetX + width], v ∈ [offsetY, offsetY + height]. "Стеной" может быть и пол, и потолок: тогда верх картинки смотрит туда, куда смотрел игрок при установке. Здесь только расчёты; проверки по миру — в FrameGeometry версии (она наследует этот класс). Параллелепипеды — массивы {minX, minY, minZ, maxX, maxY, maxZ}. */
public class CanvasGeometry {

    protected CanvasGeometry() {}

    /** Насколько расширять поиск картин вокруг области. Сущность хранится в секции мира по своему центру, а холст бывает до MAX_SIZE_BLOCKS — без запаса край большой картины не найти. */
    public static final double SEARCH_MARGIN = PictureSettings.MAX_SIZE_BLOCKS / 2 + 2;

    /** Шаг сетки источников света по холсту: свет гаснет на 1 за блок, так между ними он падает не больше чем на 2. */
    public static final double LIGHT_STEP = 4;

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
    public static int[] normal(PictureSettings f) {
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

    /** Параллелепипед из координат стены в координаты клетки {min..., max...}. */
    public static double[] box(int facing, int rotation, double u0, double v0, double d0, double u1, double v1, double d1) {
        double[] a = toBlock(facing, rotation, u0, v0, d0), b = toBlock(facing, rotation, u1, v1, d1);
        return new double[] { Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
            Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]) };
    }

    /** Угол клетки рамки в мире, сдвинутый вдоль нормали до поверхности, на которой лежит картина. Считаем в double: int + float дал бы float, а у него вдали от центра мира (например, где другие моды хранят блоки движущихся конструкций — десятки миллионов блоков) шаг в несколько блоков. */
    public static double[] origin(PictureSettings f) {
        int[] n = normal(f);
        double s = f.surface;
        return new double[] { f.x + n[0] * s, f.y + n[1] * s, f.z + n[2] * s };
    }

    /** Весь холст в мировых координатах — он же хитбокс картины. */
    public static double[] canvasBounds(PictureSettings f) {
        double u0 = f.getOffsetXBlocks(), v0 = f.getOffsetYBlocks();
        double[] b = box(f.getFacing(), f.getRotation(), u0, v0, 0, u0 + f.getWidthBlocks(),
            v0 + f.getHeightBlocks(), f.getDepth());
        double[] o = origin(f);
        return new double[] { o[0] + b[0], o[1] + b[1], o[2] + b[2], o[0] + b[3], o[1] + b[4], o[2] + b[5] };
    }

    /** Где висит подпись с заголовком (верх её строки), в мировых координатах: под картиной по центру, чуть впереди стены; у картины на полу — над ней. */
    public static double[] labelPos(PictureSettings f) {
        double[] box = canvasBounds(f);
        int[] n = normal(f);
        double x = (box[0] + box[3]) / 2, z = (box[2] + box[5]) / 2;
        if (f.getFacing() == 1) return new double[] { x, box[4] + 0.45, z };
        return new double[] { x + n[0] * 0.1, box[1] - 0.1, z + n[2] * 0.1 };
    }

    /** Клетки перед холстом, где светящаяся картина ставит свет: сетка с шагом LIGHT_STEP (у маленькой картины — одна клетка в центре), на четверть блока от поверхности. Смещения {dx, dy, dz} от клетки рамки — так список остаётся верным, даже если картину вместе с её светом перенесут (движущиеся конструкции). */
    public static List<int[]> lightCells(PictureSettings f) {
        List<int[]> cells = new ArrayList<int[]>();
        double[] o = origin(f);
        double u0 = f.getOffsetXBlocks(), v0 = f.getOffsetYBlocks(), w = f.getWidthBlocks(), h = f.getHeightBlocks();
        for (double u = u0 + Math.min(w, LIGHT_STEP) / 2; u < u0 + w; u += LIGHT_STEP) {
            for (double v = v0 + Math.min(h, LIGHT_STEP) / 2; v < v0 + h; v += LIGHT_STEP) {
                double[] p = toBlock(f.getFacing(), f.getRotation(), u, v, 0.25);
                int[] cell = { floor(o[0] + p[0]) - f.x, floor(o[1] + p[1]) - f.y, floor(o[2] + p[2]) - f.z };
                boolean known = false;
                for (int[] c : cells) known |= c[0] == cell[0] && c[1] == cell[1] && c[2] == cell[2];
                if (!known) cells.add(cell);
            }
        }
        return cells;
    }

    /** Картины на перпендикулярных поверхностях (две стены, стена и пол...) встречаются во внутреннем углу: их толщины делят угловой столбик. Это разрешено, только если край каждой упирается ровно в поверхность другой. Параллельные картины, крест-накрест и торчащие сквозь другую — по-прежнему пересечение. */
    public static boolean cornerJoint(PictureSettings a, double[] aBox, PictureSettings b, double[] bBox) {
        int aAxis = normalAxis(a), bAxis = normalAxis(b);
        if (aAxis == bAxis) return false;
        return endsAt(aBox, bAxis, wallPlane(b, bBox)) && endsAt(bBox, aAxis, wallPlane(a, aBox));
    }

    /** Ось мира (0 — x, 1 — y, 2 — z), вдоль которой направлена нормаль картины. */
    private static int normalAxis(PictureSettings f) {
        int[] n = normal(f);
        return n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2;
    }

    /** Плоскость поверхности под картиной — задняя грань её хитбокса. */
    private static double wallPlane(PictureSettings f, double[] box) {
        int axis = normalAxis(f);
        return normal(f)[axis] > 0 ? box[axis] : box[axis + 3];
    }

    /** Край box вдоль оси axis лежит ровно в плоскости plane. */
    private static boolean endsAt(double[] box, int axis, double plane) {
        return Math.abs(box[axis] - plane) < 1e-3 || Math.abs(box[axis + 3] - plane) < 1e-3;
    }

    protected static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
