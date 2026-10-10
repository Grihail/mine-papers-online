package com.grihail.minepapers.network;

import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Клиент -> сервер: картинки локального источника рамки (origin — путь, как он записан в рамке).
 * Сервер в ответ просит недостающие (PacketHashes.Need), а когда они все у него, записывает список в рамку.
 */
public class PacketOfferFiles implements IMessage {

    private int x, y, z;
    private String origin;
    private byte[] hashes;

    public PacketOfferFiles() {}

    public PacketOfferFiles(int x, int y, int z, String origin, byte[] hashes) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.origin = origin;
        this.hashes = hashes;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        origin = ByteBufUtils.readUTF8String(buf);
        hashes = PacketHashes.readHashes(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        ByteBufUtils.writeUTF8String(buf, origin);
        PacketHashes.writeHashes(buf, hashes);
    }

    public static class Handler implements IMessageHandler<PacketOfferFiles, IMessage> {
        @Override
        public IMessage onMessage(final PacketOfferFiles msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerImages.schedule(() -> ServerImages.offer(player, msg.x, msg.y, msg.z, msg.origin, msg.hashes));
            return null;
        }
    }
}
