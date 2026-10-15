package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Список картинок (подряд идущие SHA-1). */
public final class PacketHashes {

    /** Больше одного пакета клиент -> сервер (32 КБ) не влезет. */
    public static final int MAX_HASHES = SharedImage.MAX_HASHES;

    private PacketHashes() {}

    /** Список хешей; неполный или слишком длинный читается как пустой. */
    static byte[] readHashes(FriendlyByteBuf buf) {
        byte[] hashes = buf.readByteArray(MAX_HASHES * SharedImage.HASH_BYTES);
        return hashes.length % SharedImage.HASH_BYTES == 0 ? hashes : PictureFrame.NO_FILES;
    }

    /** Сервер -> клиент: этих картинок на сервере нет, пришли их. */
    public record Need(byte[] hashes) implements ModPacket {
        public static final ResourceLocation ID = MinePapers.id("need_files");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByteArray(hashes);
        }

        public static Need read(FriendlyByteBuf buf) {
            return new Need(readHashes(buf));
        }
    }

    /** Клиент -> сервер: пришли мне эти картинки. */
    public record Request(byte[] hashes) implements ModPacket {
        public static final ResourceLocation ID = MinePapers.id("request_files");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByteArray(hashes);
        }

        public static Request read(FriendlyByteBuf buf) {
            return new Request(readHashes(buf));
        }

        /** Применить пакет от игрока player (в серверном потоке). */
        public static void apply(ServerPlayer player, Request msg) {
            ServerImages.request(player, msg.hashes);
        }
    }
}
