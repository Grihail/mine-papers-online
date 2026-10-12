package com.grihail.minepapers.client;

import com.grihail.minepapers.CommonProxy;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

public class ClientProxy extends CommonProxy {

    @Override
    public HolderLookup.Provider clientRegistries() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
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
