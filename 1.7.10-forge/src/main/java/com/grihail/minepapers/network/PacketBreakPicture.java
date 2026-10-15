package com.grihail.minepapers.network;

import com.grihail.minepapers.picture.BreakTimer;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Клиент -> сервер: игрок начал "копать" картину (START) или докопал (FINISH). Сервер ломает картину, только если с начала прошло не меньше времени, чем нужно этому игроку с его инструментом. */
public class PacketBreakPicture implements IMessage {

    public static final byte START = 0, FINISH = 1;

    private int entityId;
    private byte action;

    public PacketBreakPicture() {}

    public PacketBreakPicture(int entityId, byte action) {
        this.entityId = entityId;
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        action = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeByte(action);
    }

    private static void apply(EntityPlayerMP player, PacketBreakPicture msg) {
        EntityPicture picture = ServerImages.pictureById(player.worldObj, msg.entityId);
        long now = player.worldObj.getTotalWorldTime();
        if (msg.action == START) {
            BreakTimer.start(player.getUniqueID(), msg.entityId, now);
            return;
        }
        boolean creative = player.capabilities.isCreativeMode;
        if (picture == null || !ServerImages.canEdit(player, picture) || creative) {
            BreakTimer.cancel(player.getUniqueID());
            if (picture != null && creative && ServerImages.canEdit(player, picture)) picture.breakPicture(false);
            return;
        }
        if (BreakTimer.finish(player.getUniqueID(), msg.entityId, now, picture.breakRate(player))) picture.breakPicture(true);
    }

    public static class Handler implements IMessageHandler<PacketBreakPicture, IMessage> {
        @Override
        public IMessage onMessage(final PacketBreakPicture msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerImages.schedule(() -> apply(player, msg));
            return null;
        }
    }
}
