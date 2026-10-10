package com.grihail.minepapers.tile;

import com.grihail.minepapers.MinePapers;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Расстановка и снятие блоков-частей, которые дают картине хитбокс на всю её площадь. Только на сервере. */
public final class FrameStructure {

    private FrameStructure() {}

    /** Ставит части во все свободные клетки холста (занятые другими блоками пропускает). */
    public static void place(World world, TilePictureFrame master) {
        int[] r = FrameGeometry.cellRange(master);
        for (int cu = r[0]; cu <= r[2]; cu++) {
            for (int cv = r[1]; cv <= r[3]; cv++) {
                if (cu == 0 && cv == 0) continue; // клетка самой рамки
                int[] p = FrameGeometry.cellPos(master, cu, cv);
                if (p[1] < 0 || p[1] >= world.getHeight()) continue;
                Block block = world.getBlock(p[0], p[1], p[2]);
                if (block == MinePapers.filler) {
                    TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
                    if (te instanceof TileFrameFiller && ((TileFrameFiller) te).isPartOf(master)) {
                        ((TileFrameFiller) te).setMaster(master, cu, cv);
                        world.markBlockForUpdate(p[0], p[1], p[2]);
                    }
                    continue; // чужая часть — не трогаем
                }
                if (!block.isReplaceable(world, p[0], p[1], p[2])) continue;
                world.setBlock(p[0], p[1], p[2], MinePapers.filler, master.getFacing(), 3);
                TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
                if (te instanceof TileFrameFiller) {
                    ((TileFrameFiller) te).setMaster(master, cu, cv);
                    world.markBlockForUpdate(p[0], p[1], p[2]);
                }
            }
        }
    }

    /** Убирает все части этой картины (по текущим настройкам холста). */
    public static void clear(World world, TilePictureFrame master) {
        int[] r = FrameGeometry.cellRange(master);
        for (int cu = r[0]; cu <= r[2]; cu++) {
            for (int cv = r[1]; cv <= r[3]; cv++) {
                if (cu == 0 && cv == 0) continue;
                int[] p = FrameGeometry.cellPos(master, cu, cv);
                if (world.getBlock(p[0], p[1], p[2]) != MinePapers.filler) continue;
                TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
                if (te instanceof TileFrameFiller && ((TileFrameFiller) te).isPartOf(master)) {
                    world.setBlockToAir(p[0], p[1], p[2]);
                }
            }
        }
    }
}
