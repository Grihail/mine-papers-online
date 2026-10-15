package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Клиент -> сервер: картинки локального источника картины target (PictureTarget; origin — путь, как он записан в рамке). Сервер в ответ просит недостающие (PacketHashes.Need), а когда они все у него, записывает список в рамку. */
public record PacketOfferFiles(int target, String origin, byte[] hashes) implements ModPacket {

    public static final ResourceLocation ID = MinePapers.id("offer_files");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(target);
        buf.writeUtf(origin, 4096);
        buf.writeByteArray(hashes);
    }

    public static PacketOfferFiles read(FriendlyByteBuf buf) {
        return new PacketOfferFiles(buf.readVarInt(), buf.readUtf(4096), PacketHashes.readHashes(buf));
    }

    /** Применить пакет от игрока player (в серверном потоке). */
    public static void apply(ServerPlayer player, PacketOfferFiles msg) {
        ServerImages.offer(player, msg.target, msg.origin, msg.hashes);
    }
}
