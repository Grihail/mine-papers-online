package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.ServerImages;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Кусок файла картинки: сервер -> клиент (скачивание). total = -1 — такой картинки на сервере нет. */
public record PacketFileChunk(byte[] hash, int total, int offset, byte[] data)
    implements CustomPacketPayload, FileChunkFields {

    /** Скачивание: куски до 32 КБ (с запасом). */
    private static final int MAX_DATA = 64 * 1024;

    public static final Type<PacketFileChunk> TYPE = new Type<>(MinePapers.id("file_chunk"));
    public static final StreamCodec<ByteBuf, PacketFileChunk> CODEC = codec(MAX_DATA, PacketFileChunk::new);

    @Override
    public Type<PacketFileChunk> type() {
        return TYPE;
    }

    private static <T extends FileChunkFields> StreamCodec<ByteBuf, T> codec(int maxData, FileChunkFields.Factory<T> factory) {
        StreamCodec<ByteBuf, byte[]> data = ByteBufCodecs.byteArray(maxData);
        return StreamCodec.of((buf, msg) -> {
            buf.writeBytes(msg.hash());
            buf.writeInt(msg.total());
            buf.writeInt(msg.offset());
            data.encode(buf, msg.data());
        }, buf -> {
            byte[] hash = new byte[SharedImage.HASH_BYTES];
            buf.readBytes(hash);
            int total = buf.readInt();
            int offset = buf.readInt();
            return factory.create(hash, total, offset, data.decode(buf));
        });
    }

    /** Кусок файла картинки: клиент -> сервер (выкладывание). */
    public record Upload(byte[] hash, int total, int offset, byte[] data) implements CustomPacketPayload, FileChunkFields {
        /** Пакет клиент -> сервер ограничен 32 КБ. */
        public static final int MAX_DATA = SharedImage.UPLOAD_CHUNK;

        public static final Type<Upload> TYPE = new Type<>(MinePapers.id("upload_chunk"));
        public static final StreamCodec<ByteBuf, Upload> CODEC = codec(MAX_DATA, Upload::new);

        @Override
        public Type<Upload> type() {
            return TYPE;
        }

        public static void handle(Upload msg, IPayloadContext ctx) {
            ServerImages.upload((ServerPlayer) ctx.player(), msg.hash, msg.total, msg.offset, msg.data);
        }
    }
}

/** Общие поля кусков файла в обе стороны — для одного кодека на оба пакета. */
interface FileChunkFields {
    byte[] hash();

    int total();

    int offset();

    byte[] data();

    interface Factory<T> {
        T create(byte[] hash, int total, int offset, byte[] data);
    }
}
