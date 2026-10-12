package com.grihail.minepapers;

import com.grihail.minepapers.picture.EntityPicture;

public class CommonProxy {

    public void openFrameGui(EntityPicture picture) {}

    /** Сервер просит выложить эти картинки. */
    public void onFilesNeeded(byte[] hashes) {}

    /** Кусок скачиваемой картинки. */
    public void onFileChunk(byte[] hash, int total, int offset, byte[] data) {}
}
