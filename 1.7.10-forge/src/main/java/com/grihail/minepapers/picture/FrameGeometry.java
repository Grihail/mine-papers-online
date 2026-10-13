package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Геометрия картины (расчёты — в CanvasGeometry) и проверки по миру 1.7.10: блоки и другие картины на месте холста. */
public final class FrameGeometry extends CanvasGeometry {

    private FrameGeometry() {}

    /** Весь холст в мировых координатах — он же хитбокс картины. */
    public static AxisAlignedBB canvasBox(PictureSettings f) {
        double[] b = canvasBounds(f);
        return AxisAlignedBB.getBoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
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
        double[] bounds = bounds(box);
        for (Object o : world.getEntitiesWithinAABB(EntityPicture.class, box.expand(SEARCH_MARGIN, SEARCH_MARGIN, SEARCH_MARGIN))) {
            EntityPicture other = (EntityPicture) o;
            if (other == self || other.isDead) continue;
            AxisAlignedBB otherBox = canvasBox(other.frame);
            if (box.intersectsWith(otherBox) && !cornerJoint(candidate, bounds, other.frame, bounds(otherBox))) return other;
        }
        return null;
    }

    private static double[] bounds(AxisAlignedBB box) {
        return new double[] { box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ };
    }
}
