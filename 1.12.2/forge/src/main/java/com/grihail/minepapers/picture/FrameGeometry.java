package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** Геометрия картины (расчёты — в CanvasGeometry) и проверки по миру 1.12.2: блоки и другие картины на месте холста. */
public final class FrameGeometry extends CanvasGeometry {

    private FrameGeometry() {}

    /** Весь холст в мировых координатах — он же хитбокс картины. */
    public static AxisAlignedBB canvasBox(PictureSettings f) {
        double[] b = canvasBounds(f);
        return new AxisAlignedBB(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    /** Холст нельзя разместить: он прошёл бы сквозь блок или другую картину. */
    public static boolean isBlocked(World world, PictureFrame candidate, Entity self) {
        return hitsBlocks(world, candidate) || overlapping(world, candidate, self) != null;
    }

    /** Холст проходит сквозь коллизию блока. Блоки без коллизии (факелы, трава, цветы) не мешают, касаться граней (стены за картиной) можно. */
    public static boolean hitsBlocks(World world, PictureFrame candidate) {
        double e = 1e-4;
        AxisAlignedBB box = canvasBox(candidate).shrink(e);
        List<AxisAlignedBB> hits = new ArrayList<AxisAlignedBB>();
        int x0 = MathHelper.floor(box.minX), x1 = MathHelper.floor(box.maxX);
        int y0 = MathHelper.floor(box.minY) - 1, y1 = MathHelper.floor(box.maxY); // -1: заборы выше блока
        int z0 = MathHelper.floor(box.minZ), z1 = MathHelper.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    pos.setPos(x, y, z);
                    IBlockState state = world.getBlockState(pos);
                    if (state.getMaterial() == Material.AIR) continue;
                    state.addCollisionBoxToList(world, pos, box, hits, null, false);
                    if (!hits.isEmpty()) return true;
                }
            }
        }
        return false;
    }

    /** Другая картина, чей холст пересекается с холстом candidate, или null. Касаться краями можно. @param self сама картина, если меняются её настройки (с собой не сравниваем); null — при установке новой */
    public static EntityPicture overlapping(World world, PictureFrame candidate, Entity self) {
        double e = 1e-4; // погрешность float в размерах и смещениях
        AxisAlignedBB box = canvasBox(candidate).shrink(e);
        double[] bounds = bounds(box);
        for (EntityPicture other : world.getEntitiesWithinAABB(EntityPicture.class, box.grow(SEARCH_MARGIN))) {
            if (other == self || other.isDead) continue;
            AxisAlignedBB otherBox = canvasBox(other.frame);
            if (box.intersects(otherBox) && !cornerJoint(candidate, bounds, other.frame, bounds(otherBox))) return other;
        }
        return null;
    }

    private static double[] bounds(AxisAlignedBB box) {
        return new double[] { box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ };
    }
}
