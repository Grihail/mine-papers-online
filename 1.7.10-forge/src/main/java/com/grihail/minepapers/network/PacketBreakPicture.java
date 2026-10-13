package com.grihail.minepapers.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    /** Игрок -> {id картины, тик начала}. Только серверный поток. */
    private static final Map<UUID, long[]> STARTED = new HashMap<UUID, long[]>();

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
            STARTED.put(player.getUniqueID(), new long[] { msg.entityId, now });
            return;
        }
        long[] started = STARTED.remove(player.getUniqueID());
        if (picture == null || !ServerImages.canEdit(player, picture)) return;
        if (player.capabilities.isCreativeMode) {
            picture.breakPicture(false);
            return;
        }
        if (started == null || started[0] != msg.entityId) return;
        // Небольшой запас на задержки сети: клиент считает по своим тикам.
        int required = (int) Math.ceil(1 / Math.max(picture.breakRate(player), 1e-4f));
        if (now - started[1] >= required * 0.7 - 2) picture.breakPicture(true);
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
