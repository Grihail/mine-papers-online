package com.grihail.minepapers.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.grihail.minepapers.MinePapers;
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

    /** Игрок -> {id картины, тик начала}. Только серверный поток. */
    private static final Map<UUID, long[]> STARTED = new HashMap<UUID, long[]>();

    @Override
    public Type<PacketBreakPicture> type() {
        return TYPE;
    }

    public static void handle(PacketBreakPicture msg, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        EntityPicture picture = ServerImages.pictureById(player.level(), msg.entityId);
        long now = player.level().getGameTime();
        if (msg.action == START) {
            STARTED.put(player.getUUID(), new long[] { msg.entityId, now });
            return;
        }
        long[] started = STARTED.remove(player.getUUID());
        if (picture == null || !ServerImages.canEdit(player, picture)) return;
        if (player.isCreative()) {
            picture.breakPicture(false);
            return;
        }
        if (started == null || started[0] != msg.entityId) return;
        // Небольшой запас на задержки сети: клиент считает по своим тикам.
        int required = (int) Math.ceil(1 / Math.max(picture.breakRate(player), 1e-4f));
        if (now - started[1] >= required * 0.7 - 2) picture.breakPicture(true);
    }
}
