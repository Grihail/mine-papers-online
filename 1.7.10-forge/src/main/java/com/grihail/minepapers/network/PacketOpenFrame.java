package com.grihail.minepapers.network;

import com.grihail.minepapers.CommonProxy;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Клиент -> сервер: открыть окно настройки картины. Окно с инвентарём игрока открывает сервер; дальность проверяем сами (до холста, а не до центра сущности, как у ванильного клика — иначе большие картины не открывались бы с края). */
public class PacketOpenFrame implements IMessage {

    private int entityId;

    public PacketOpenFrame() {}

    public PacketOpenFrame(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
    }

    private static void apply(EntityPlayerMP player, PacketOpenFrame msg) {
        EntityPicture picture = ServerImages.pictureById(player.worldObj, msg.entityId);
        if (picture == null || !ServerImages.canEdit(player, picture)) return;
        CommonProxy.openFrame(player, msg.entityId);
    }

    public static class Handler implements IMessageHandler<PacketOpenFrame, IMessage> {
        @Override
        public IMessage onMessage(final PacketOpenFrame msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerImages.schedule(() -> apply(player, msg));
            return null;
        }
    }
}
