package com.grihail.minepapers.network;

import com.grihail.minepapers.server.PictureReload;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Клиент -> сервер: Shift+клик по кнопке "Обновить" — перезагрузить картину entity у всех игроков (PictureReload.fromWindow). */
public class PacketReloadRequest implements IMessage {

    private int entity;

    public PacketReloadRequest() {}

    public PacketReloadRequest(int entity) {
        this.entity = entity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entity);
    }

    public static class Handler implements IMessageHandler<PacketReloadRequest, IMessage> {
        @Override
        public IMessage onMessage(final PacketReloadRequest msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerImages.schedule(() -> PictureReload.fromWindow(player, msg.entity));
            return null;
        }
    }
}
