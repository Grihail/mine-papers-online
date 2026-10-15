package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.BreakTimer;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Клиент -> сервер: игрок начал "копать" картину (START) или докопал (FINISH). Сервер ломает картину, только если с начала прошло не меньше времени, чем нужно этому игроку с его инструментом. */
public record PacketBreakPicture(int entityId, byte action) implements ModPacket {

    public static final byte START = 0, FINISH = 1;

    public static final ResourceLocation ID = MinePapers.id("break_picture");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(action);
    }

    public static PacketBreakPicture read(FriendlyByteBuf buf) {
        return new PacketBreakPicture(buf.readVarInt(), buf.readByte());
    }

    /** Применить пакет от игрока player (в серверном потоке). */
    public static void apply(ServerPlayer player, PacketBreakPicture msg) {
        EntityPicture picture = ServerImages.pictureById(player.level(), msg.entityId);
        long now = player.level().getGameTime();
        if (msg.action == START) {
            BreakTimer.start(player.getUUID(), msg.entityId, now);
            return;
        }
        if (picture == null || !ServerImages.canEdit(player, picture) || player.isCreative()) {
            BreakTimer.cancel(player.getUUID());
            if (picture != null && player.isCreative() && ServerImages.canEdit(player, picture)) picture.breakPicture(false);
            return;
        }
        if (BreakTimer.finish(player.getUUID(), msg.entityId, now, picture.breakRate(player))) picture.breakPicture(true);
    }
}
