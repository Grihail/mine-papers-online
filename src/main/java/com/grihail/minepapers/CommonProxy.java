package com.grihail.minepapers;

import com.grihail.minepapers.tile.TilePictureFrame;

public class CommonProxy {

    public void preInit() {}

    public void openFrameGui(TilePictureFrame tile) {}

    /** Сервер просит выложить эти картинки (вызывается в сетевом потоке). */
    public void onFilesNeeded(byte[] hashes) {}

    /** Кусок скачиваемой картинки (вызывается в сетевом потоке). */
    public void onFileChunk(byte[] hash, int total, int offset, byte[] data) {}
}
