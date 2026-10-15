package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.BreakTimer;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент -> сервер: игрок начал "копать" картину (START) или докопал (FINISH). Сервер ломает картину, только если с начала прошло не меньше времени, чем нужно этому игроку с его инструментом. */
public record PacketBreakPicture(int entityId, byte action) implements CustomPacketPayload {

    public static final byte START = 0, FINISH = 1;

    public static final Type<PacketBreakPicture> TYPE = new Type<>(MinePapers.id("break_picture"));
    public static final StreamCodec<ByteBuf, PacketBreakPicture> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PacketBreakPicture::entityId,
        ByteBufCodecs.BYTE, PacketBreakPicture::action,
        PacketBreakPicture::new);

    @Override
    public Type<PacketBreakPicture> type() {
        return TYPE;
    }

    public static void handle(PacketBreakPicture msg, IPayloadContext ctx) {
        apply((ServerPlayer) ctx.player(), msg);
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
