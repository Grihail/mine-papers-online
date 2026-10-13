package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Кусок файла картинки: сервер -> клиент (скачивание). total = -1 — такой картинки на сервере нет. */
public class PacketFileChunk implements IMessage {

    public byte[] hash;
    public int total, offset;
    public byte[] data;
    private int dataOffset, dataLength;

    public PacketFileChunk() {}

    public PacketFileChunk(byte[] hash, int total, int offset, byte[] data, int dataOffset, int dataLength) {
        this.hash = hash;
        this.total = total;
        this.offset = offset;
        this.data = data;
        this.dataOffset = dataOffset;
        this.dataLength = dataLength;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hash = new byte[SharedImage.HASH_BYTES];
        buf.readBytes(hash);
        total = buf.readInt();
        offset = buf.readInt();
        int n = ByteBufUtils.readVarInt(buf, 3);
        if (n < 0 || n > buf.readableBytes()) {
            total = -1;
            data = new byte[0];
            buf.skipBytes(buf.readableBytes());
            return;
        }
        data = new byte[n];
        buf.readBytes(data);
        dataLength = n;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBytes(hash);
        buf.writeInt(total);
        buf.writeInt(offset);
        ByteBufUtils.writeVarInt(buf, dataLength, 3);
        buf.writeBytes(data, dataOffset, dataLength);
    }

    public static class Handler implements IMessageHandler<PacketFileChunk, IMessage> {
        @Override
        public IMessage onMessage(PacketFileChunk msg, MessageContext ctx) {
            MinePapers.proxy.onFileChunk(msg.hash, msg.total, msg.offset, msg.data);
            return null;
        }
    }

    /** Кусок файла картинки: клиент -> сервер (выкладывание). */
    public static class Upload extends PacketFileChunk {
        /** Пакет клиент -> сервер ограничен 32 КБ. */
        public static final int MAX_DATA = SharedImage.UPLOAD_CHUNK;

        public Upload() {}

        public Upload(byte[] hash, int total, int offset, byte[] data, int dataOffset, int dataLength) {
            super(hash, total, offset, data, dataOffset, dataLength);
        }

        public static class Handler implements IMessageHandler<Upload, IMessage> {
            @Override
            public IMessage onMessage(final Upload msg, MessageContext ctx) {
                final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                ServerImages.schedule(() -> ServerImages.upload(player, msg.hash, msg.total, msg.offset, msg.data));
                return null;
            }
        }
    }
}
