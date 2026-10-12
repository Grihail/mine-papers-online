package com.grihail.minepapers;

import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/** Окна мода открываются через IGuiHandler: x — id сущности картины. */
public class CommonProxy implements IGuiHandler {

    public static final int GUI_FRAME = 0;

    public void preInit() {}

    /** Сервер просит выложить эти картинки (вызывается в сетевом потоке). */
    public void onFilesNeeded(byte[] hashes) {}

    /** Кусок скачиваемой картинки (вызывается в сетевом потоке). */
    public void onFileChunk(byte[] hash, int total, int offset, byte[] data) {}

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        EntityPicture picture = ServerImages.pictureById(world, x);
        if (id != GUI_FRAME || picture == null || !(player instanceof EntityPlayerMP)
            || !ServerImages.canEdit((EntityPlayerMP) player, picture.frame)) return null;
        return new ContainerPictureFrame(player.inventory, picture);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
