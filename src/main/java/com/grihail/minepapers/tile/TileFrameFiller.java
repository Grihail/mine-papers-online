package com.grihail.minepapers.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Часть большой картины: помнит, где главный блок рамки (смещением, чтобы переживать копирование построек). */
public class TileFrameFiller extends TileEntity {

    private int dx, dy, dz;
    /** Клетка холста (u, v) относительно рамки — по ней считается хитбокс. */
    private int cu, cv;

    @Override
    public boolean canUpdate() {
        return false;
    }

    public void setMaster(TilePictureFrame master, int cu, int cv) {
        this.dx = master.xCoord - xCoord;
        this.dy = master.yCoord - yCoord;
        this.dz = master.zCoord - zCoord;
        this.cu = cu;
        this.cv = cv;
        markDirty();
    }

    public int getCellU() {
        return cu;
    }

    public int getCellV() {
        return cv;
    }

    public boolean isPartOf(TilePictureFrame master) {
        return xCoord + dx == master.xCoord && yCoord + dy == master.yCoord && zCoord + dz == master.zCoord;
    }

    /** Главный блок или null, если его уже нет. */
    public TilePictureFrame getMaster() {
        if (worldObj == null) return null;
        TileEntity te = worldObj.getTileEntity(xCoord + dx, yCoord + dy, zCoord + dz);
        return te instanceof TilePictureFrame ? (TilePictureFrame) te : null;
    }

    private void write(NBTTagCompound tag) {
        tag.setInteger("MDX", dx);
        tag.setInteger("MDY", dy);
        tag.setInteger("MDZ", dz);
        tag.setInteger("CU", cu);
        tag.setInteger("CV", cv);
    }

    private void read(NBTTagCompound tag) {
        dx = tag.getInteger("MDX");
        dy = tag.getInteger("MDY");
        dz = tag.getInteger("MDZ");
        cu = tag.getInteger("CU");
        cv = tag.getInteger("CV");
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        write(tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        read(tag);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        write(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        read(pkt.func_148857_g());
    }
}
