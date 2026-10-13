package com.grihail.minepapers.client;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.network.PacketOfferFiles;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

/** Клиент 1.21 для общего кода. Текстуры — ResourceLocation динамических текстур. */
final class MinePapersClientHost extends ClientHost {

    private static final AtomicInteger TEXTURES = new AtomicInteger();

    @Override
    public File gameDir() {
        return Minecraft.getInstance().gameDirectory;
    }

    @Override
    public File picturesDir() {
        return MinePapers.picturesDir.toFile();
    }

    @Override
    public String userAgent() {
        return MinePapers.NAME + "/" + MinePapers.VERSION;
    }

    @Override
    public int[] mapColors() {
        int[] colors = new int[64];
        int n = 0;
        for (int id = 1; id < 64; id++) { // 0 — "прозрачный" цвет воздуха
            MapColor mc = MapColor.byId(id);
            if (mc != MapColor.NONE) colors[n++] = mc.col;
        }
        return Arrays.copyOf(colors, n);
    }

    @Override
    public boolean isConnected() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.getConnection() != null;
    }

    @Override
    public boolean isSingleplayerHost() {
        return Minecraft.getInstance().hasSingleplayerServer();
    }

    @Override
    public Object uploadTexture(int[] argb, int width, int height) {
        NativeImage img = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int c = argb[y * width + x];
                // NativeImage хранит ABGR: меняем местами красный и синий.
                img.setPixelRGBA(x, y, (c & 0xFF00FF00) | (c >> 16 & 0xFF) | (c & 0xFF) << 16);
            }
        }
        // Фильтрация по умолчанию — ближайший пиксель: чёткие пиксели, как у карт и картин.
        ResourceLocation id = MinePapers.id("picture/" + TEXTURES.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
        return id;
    }

    @Override
    public void releaseTexture(Object texture) {
        Minecraft.getInstance().getTextureManager().release((ResourceLocation) texture);
    }

    @Override
    public void sendOffer(int entityId, String origin, byte[] hashes) {
        PacketDistributor.sendToServer(new PacketOfferFiles(entityId, origin, hashes));
    }

    @Override
    public void sendRequest(byte[] hashes) {
        PacketDistributor.sendToServer(new PacketHashes.Request(hashes));
    }

    @Override
    public void sendUpload(byte[] hash, int total, int offset, byte[] data) {
        PacketDistributor.sendToServer(new PacketFileChunk.Upload(hash, total, offset, data));
    }
}
