package com.grihail.minepapers.block;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

/**
 * Картина-блок из старого сохранения. При первом тике на сервере превращается в картину-сущность
 * с теми же настройками и убирает свои невидимые части.
 */
public class TileLegacyPicture extends TileEntity {

    private NBTTagCompound saved = new NBTTagCompound();

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        saved = (NBTTagCompound) tag.copy();
    }

    /** Если превратить не успели (чанк выгрузился), сохраняем всё как было. */
    @Override
    public void writeToNBT(NBTTagCompound tag) {
        for (Object key : saved.func_150296_c()) {
            tag.setTag((String) key, saved.getTag((String) key).copy());
        }
        super.writeToNBT(tag);
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        PictureFrame frame = new PictureFrame();
        frame.x = xCoord;
        frame.y = yCoord;
        frame.z = zCoord;
        frame.facing = getBlockMetadata();
        frame.facing = frame.getFacing();
        NBTTagCompound tag = (NBTTagCompound) saved.copy();
        // Последняя блочная версия могла поставить блок не в клетку клика (ShiftU/V): пересчитываем смещение от блока.
        float scale = tag.getBoolean("PosPx") ? 16f : 1f;
        tag.setFloat("OX", tag.getFloat("OX") - tag.getInteger("ShiftU") * scale);
        tag.setFloat("OY", tag.getFloat("OY") - tag.getInteger("ShiftV") * scale);
        frame.readSettings(tag);

        worldObj.setBlockToAir(xCoord, yCoord, zCoord);
        AxisAlignedBB box = FrameGeometry.canvasBox(frame);
        double e = 1e-4;
        for (int x = MathHelper.floor_double(box.minX + e); x <= MathHelper.floor_double(box.maxX - e); x++) {
            for (int y = MathHelper.floor_double(box.minY + e); y <= MathHelper.floor_double(box.maxY - e); y++) {
                for (int z = MathHelper.floor_double(box.minZ + e); z <= MathHelper.floor_double(box.maxZ - e); z++) {
                    if (worldObj.getBlock(x, y, z) == MinePapers.filler) worldObj.setBlockToAir(x, y, z);
                }
            }
        }
        worldObj.spawnEntityInWorld(new EntityPicture(worldObj, frame));
    }
}
