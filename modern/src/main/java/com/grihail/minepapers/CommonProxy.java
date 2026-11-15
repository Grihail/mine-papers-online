package com.grihail.minepapers;

import net.minecraft.core.HolderLookup;

public class CommonProxy {

    /** Реестры клиентского мира (на сервере — null). */
    public HolderLookup.Provider clientRegistries() {
        return null;
    }

    /** Сервер просит выложить эти картинки. */
    public void onFilesNeeded(byte[] hashes) {}

    /** Сервер просит загрузить источник key заново, а entity >= 0 — ещё и выложить его картинки для этой картины (PacketReload). */
    public void onReload(String key, int entity) {}

    /** Кусок скачиваемой картинки. */
    public void onFileChunk(byte[] hash, int total, int offset, byte[] data) {}
}
