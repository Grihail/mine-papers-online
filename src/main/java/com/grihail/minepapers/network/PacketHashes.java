package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.ServerImages;
import com.grihail.minepapers.tile.TilePictureFrame;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Список картинок (подряд идущие SHA-1). */
public abstract class PacketHashes implements IMessage {

    /** Больше одного пакета клиент -> сервер (32 КБ) не влезет. */
    public static final int MAX_HASHES = 1024;

    public byte[] hashes;

    protected PacketHashes() {}

    protected PacketHashes(byte[] hashes) {
        this.hashes = hashes;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hashes = readHashes(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeHashes(buf, hashes);
    }

    static byte[] readHashes(ByteBuf buf) {
        int n = ByteBufUtils.readVarInt(buf, 3);
        if (n < 0 || n % SharedImage.HASH_BYTES != 0 || n > MAX_HASHES * SharedImage.HASH_BYTES
            || n > buf.readableBytes()) {
            buf.skipBytes(buf.readableBytes());
            return TilePictureFrame.NO_FILES;
        }
        byte[] hashes = new byte[n];
        buf.readBytes(hashes);
        return hashes;
    }

    static void writeHashes(ByteBuf buf, byte[] hashes) {
        ByteBufUtils.writeVarInt(buf, hashes.length, 3);
        buf.writeBytes(hashes);
    }

    /** Сервер -> клиент: этих картинок на сервере нет, пришли их. */
    public static class Need extends PacketHashes {
        public Need() {}

        public Need(byte[] hashes) {
            super(hashes);
        }

        public static class Handler implements IMessageHandler<Need, IMessage> {
            @Override
            public IMessage onMessage(Need msg, MessageContext ctx) {
                MinePapers.proxy.onFilesNeeded(msg.hashes);
                return null;
            }
        }
    }

    /** Клиент -> сервер: пришли мне эти картинки. */
    public static class Request extends PacketHashes {
        public Request() {}

        public Request(byte[] hashes) {
            super(hashes);
        }

        public static class Handler implements IMessageHandler<Request, IMessage> {
            @Override
            public IMessage onMessage(final Request msg, MessageContext ctx) {
                final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                ServerImages.schedule(() -> ServerImages.request(player, msg.hashes));
                return null;
            }
        }
    }
}
