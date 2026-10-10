package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.ServerImages;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент -> сервер: картинки локального источника рамки (origin — путь, как он записан в рамке).
 * Сервер в ответ просит недостающие (PacketHashes.Need), а когда они все у него, записывает список в рамку.
 */
public record PacketOfferFiles(int entityId, String origin, byte[] hashes) implements CustomPacketPayload {

    public static final Type<PacketOfferFiles> TYPE = new Type<>(MinePapers.id("offer_files"));
    public static final StreamCodec<ByteBuf, PacketOfferFiles> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PacketOfferFiles::entityId,
        ByteBufCodecs.stringUtf8(4096), PacketOfferFiles::origin,
        PacketHashes.HASHES, PacketOfferFiles::hashes,
        PacketOfferFiles::new);

    @Override
    public Type<PacketOfferFiles> type() {
        return TYPE;
    }

    public static void handle(PacketOfferFiles msg, IPayloadContext ctx) {
        ServerImages.offer((ServerPlayer) ctx.player(), msg.entityId, msg.origin, msg.hashes);
    }
}
