package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Список картинок (подряд идущие SHA-1). */
public final class PacketHashes {

    /** Больше одного пакета клиент -> сервер (32 КБ) не влезет. */
    public static final int MAX_HASHES = 1024;

    /** Список хешей; неполный или слишком длинный читается как пустой. */
    static final StreamCodec<ByteBuf, byte[]> HASHES = StreamCodec.of(
        (buf, hashes) -> ByteBufCodecs.byteArray(MAX_HASHES * SharedImage.HASH_BYTES).encode(buf, hashes),
        buf -> {
            byte[] hashes = ByteBufCodecs.byteArray(MAX_HASHES * SharedImage.HASH_BYTES).decode(buf);
            return hashes.length % SharedImage.HASH_BYTES == 0 ? hashes : PictureFrame.NO_FILES;
        });

    private PacketHashes() {}

    /** Сервер -> клиент: этих картинок на сервере нет, пришли их. */
    public record Need(byte[] hashes) implements CustomPacketPayload {
        public static final Type<Need> TYPE = new Type<>(MinePapers.id("need_files"));
        public static final StreamCodec<ByteBuf, Need> CODEC = HASHES.map(Need::new, Need::hashes);

        @Override
        public Type<Need> type() {
            return TYPE;
        }
    }

    /** Клиент -> сервер: пришли мне эти картинки. */
    public record Request(byte[] hashes) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(MinePapers.id("request_files"));
        public static final StreamCodec<ByteBuf, Request> CODEC = HASHES.map(Request::new, Request::hashes);

        @Override
        public Type<Request> type() {
            return TYPE;
        }

        public static void handle(Request msg, IPayloadContext ctx) {
            ServerImages.request((ServerPlayer) ctx.player(), msg.hashes);
        }
    }
}
