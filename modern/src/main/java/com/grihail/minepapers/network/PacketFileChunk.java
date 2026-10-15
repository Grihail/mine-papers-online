package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Кусок файла картинки: сервер -> клиент (скачивание). total = -1 — такой картинки на сервере нет. */
public record PacketFileChunk(byte[] hash, int total, int offset, byte[] data) implements ModPacket {

    public static final ResourceLocation ID = MinePapers.id("file_chunk");
    /** Скачивание: куски до 32 КБ (с запасом). */
    private static final int MAX_DATA = 64 * 1024;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        write(buf, hash, total, offset, data);
    }

    public static PacketFileChunk read(FriendlyByteBuf buf) {
        byte[] hash = readHash(buf);
        return new PacketFileChunk(hash, buf.readInt(), buf.readInt(), buf.readByteArray(MAX_DATA));
    }

    private static void write(FriendlyByteBuf buf, byte[] hash, int total, int offset, byte[] data) {
        buf.writeBytes(hash);
        buf.writeInt(total);
        buf.writeInt(offset);
        buf.writeByteArray(data);
    }

    private static byte[] readHash(FriendlyByteBuf buf) {
        byte[] hash = new byte[SharedImage.HASH_BYTES];
        buf.readBytes(hash);
        return hash;
    }

    /** Кусок файла картинки: клиент -> сервер (выкладывание). */
    public record Upload(byte[] hash, int total, int offset, byte[] data) implements ModPacket {

        public static final ResourceLocation ID = MinePapers.id("upload_chunk");
        /** Пакет клиент -> сервер ограничен 32 КБ. */
        public static final int MAX_DATA = SharedImage.UPLOAD_CHUNK;

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            PacketFileChunk.write(buf, hash, total, offset, data);
        }

        public static Upload read(FriendlyByteBuf buf) {
            byte[] hash = readHash(buf);
            return new Upload(hash, buf.readInt(), buf.readInt(), buf.readByteArray(MAX_DATA));
        }

        /** Применить пакет от игрока player (в серверном потоке). */
        public static void apply(ServerPlayer player, Upload msg) {
            ServerImages.upload(player, msg.hash, msg.total, msg.offset, msg.data);
        }
    }
}
