package com.grihail.minepapers;

import net.minecraft.core.HolderLookup;

public class CommonProxy {

    /** Реестры клиентского мира (на сервере — null). */
    public HolderLookup.Provider clientRegistries() {
        return null;
    }

    /** Сервер просит выложить эти картинки. */
    public void onFilesNeeded(byte[] hashes) {}

    /** Кусок скачиваемой картинки. */
    public void onFileChunk(byte[] hash, int total, int offset, byte[] data) {}
}
