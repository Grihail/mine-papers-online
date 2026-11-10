package com.grihail.minepapers;

import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.server.ServerImages;

import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/** Окна мода открываются через IGuiHandler: x — картина (PictureTarget: id сущности или слот картины-предмета), y — можно ли менять материалы стопке, z — лимит веса картинки (см. openFrame). */
public class CommonProxy implements IGuiHandler {

    public static final int GUI_FRAME = 0;

    public void preInit() {}

    /** Сервер просит выложить эти картинки (вызывается в сетевом потоке). */
    public void onFilesNeeded(byte[] hashes) {}

    /** Кусок скачиваемой картинки (вызывается в сетевом потоке). */
    public void onFileChunk(byte[] hash, int total, int offset, byte[] data) {}

    /** Открыть окно картины target (на сервере). y — можно ли менять материалы стопке (ModConfig.stackMaterials), z — лимит веса картинки (ModConfig.maxImageMb): так их узнаёт и клиент. */
    public static void openFrame(EntityPlayer player, int target) {
        player.openGui(MinePapers.instance, GUI_FRAME, player.world, target, ModConfig.stackMaterials ? 1 : 0, ModConfig.maxImageMb);
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        FrameHolder holder = id == GUI_FRAME ? FrameHolder.of(player, x) : null;
        if (holder == null || !(player instanceof EntityPlayerMP)
            || !ServerImages.canEdit((EntityPlayerMP) player, holder)) return null;
        return new ContainerPictureFrame(player.inventory, holder, y != 0);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
