package com.grihail.minepapers.picture;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Геометрия картины (расчёты — в CanvasGeometry) и проверки по миру 1.21: блоки и другие картины на месте холста. */
public final class FrameGeometry extends CanvasGeometry {

    private FrameGeometry() {}

    /** Весь холст в мировых координатах — он же хитбокс картины. */
    public static AABB canvasBox(PictureSettings f) {
        double[] b = canvasBounds(f);
        return new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /** Холст нельзя разместить: он прошёл бы сквозь блок или другую картину. */
    public static boolean isBlocked(Level level, PictureFrame candidate, Entity self) {
        return hitsBlocks(level, candidate) || overlapping(level, candidate, self) != null;
    }

    /** Холст проходит сквозь коллизию блока. Блоки без коллизии (факелы, трава, цветы) не мешают, касаться граней (стены за картиной) можно. */
    public static boolean hitsBlocks(Level level, PictureFrame candidate) {
        double e = 1e-4;
        AABB box = canvasBox(candidate).deflate(e);
        int x0 = Mth.floor(box.minX), x1 = Mth.floor(box.maxX);
        int y0 = Mth.floor(box.minY) - 1, y1 = Mth.floor(box.maxY); // -1: заборы выше блока
        int z0 = Mth.floor(box.minZ), z1 = Mth.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    for (AABB part : shape.toAabbs()) {
                        if (part.move(x, y, z).intersects(box)) return true;
                    }
                }
            }
        }
        return false;
    }

    /** Другая картина, чей холст пересекается с холстом candidate, или null. Касаться краями можно. @param self сама картина, если меняются её настройки (с собой не сравниваем); null — при установке новой */
    public static EntityPicture overlapping(Level level, PictureFrame candidate, Entity self) {
        double e = 1e-4; // погрешность float в размерах и смещениях
        AABB box = canvasBox(candidate).deflate(e);
        double[] bounds = bounds(box);
        for (EntityPicture other : level.getEntitiesOfClass(EntityPicture.class, box.inflate(SEARCH_MARGIN))) {
            if (other == self || other.isRemoved()) continue;
            AABB otherBox = canvasBox(other.frame);
            if (box.intersects(otherBox) && !cornerJoint(candidate, bounds, other.frame, bounds(otherBox))) return other;
        }
        return null;
    }

    private static double[] bounds(AABB box) {
        return new double[] { box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ };
    }
}
