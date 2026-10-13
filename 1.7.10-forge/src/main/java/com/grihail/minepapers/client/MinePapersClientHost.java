package com.grihail.minepapers.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.network.PacketOfferFiles;

import net.minecraft.block.material.MapColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;

/** Клиент 1.7.10 для общего кода. Текстуры — номера текстур OpenGL (Integer). */
final class MinePapersClientHost extends ClientHost {

    @Override
    public File gameDir() {
        return Minecraft.getMinecraft().mcDataDir;
    }

    @Override
    public File picturesDir() {
        return MinePapers.picturesDir;
    }

    @Override
    public String userAgent() {
        return MinePapers.NAME + "/" + MinePapers.VERSION;
    }

    @Override
    public int[] mapColors() {
        int[] colors = new int[MapColor.mapColorArray.length];
        int n = 0;
        for (MapColor mc : MapColor.mapColorArray) {
            if (mc != null && mc.colorIndex != 0) colors[n++] = mc.colorValue; // 0 — "прозрачный" цвет воздуха
        }
        return Arrays.copyOf(colors, n);
    }

    @Override
    public boolean isConnected() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && mc.getNetHandler() != null;
    }

    @Override
    public boolean isSingleplayerHost() {
        return Minecraft.getMinecraft().isIntegratedServerRunning();
    }

    @Override
    public Object uploadTexture(int[] argb, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, argb, 0, width);
        int id = GL11.glGenTextures();
        // blur = false: чёткие пиксели, как у карт и картин
        TextureUtil.uploadTextureImageAllocate(id, img, false, true);
        return id;
    }

    @Override
    public void releaseTexture(Object texture) {
        GL11.glDeleteTextures((Integer) texture);
    }

    @Override
    public void sendOffer(int entityId, String origin, byte[] hashes) {
        MinePapers.network.sendToServer(new PacketOfferFiles(entityId, origin, hashes));
    }

    @Override
    public void sendRequest(byte[] hashes) {
        MinePapers.network.sendToServer(new PacketHashes.Request(hashes));
    }

    @Override
    public void sendUpload(byte[] hash, int total, int offset, byte[] data) {
        MinePapers.network.sendToServer(new PacketFileChunk.Upload(hash, total, offset, data, 0, data.length));
    }
}
