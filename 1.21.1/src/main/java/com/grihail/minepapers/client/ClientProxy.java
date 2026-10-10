package com.grihail.minepapers.client;

import com.grihail.minepapers.CommonProxy;
import com.grihail.minepapers.picture.EntityPicture;

import net.minecraft.client.Minecraft;

public class ClientProxy extends CommonProxy {

    @Override
    public void openFrameGui(EntityPicture picture) {
        Minecraft.getInstance().setScreen(new GuiPictureFrame(picture));
    }

    @Override
    public void onFilesNeeded(byte[] hashes) {
        ImageSync.onFilesNeeded(hashes);
    }

    @Override
    public void onFileChunk(byte[] hash, int total, int offset, byte[] data) {
        ImageSync.onFileChunk(hash, total, offset, data);
    }
}
