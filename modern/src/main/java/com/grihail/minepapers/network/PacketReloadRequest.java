package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.PictureReload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Клиент -> сервер: Shift+клик по кнопке "Обновить" — перезагрузить картину entityId у всех игроков (PictureReload.fromWindow). */
public record PacketReloadRequest(int entityId) implements ModPacket {

    public static final ResourceLocation ID = MinePapers.id("reload_request");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static PacketReloadRequest read(FriendlyByteBuf buf) {
        return new PacketReloadRequest(buf.readVarInt());
    }

    /** Применить пакет от игрока player (в серверном потоке). */
    public static void apply(ServerPlayer player, PacketReloadRequest msg) {
        PictureReload.fromWindow(player, msg.entityId);
    }
}
