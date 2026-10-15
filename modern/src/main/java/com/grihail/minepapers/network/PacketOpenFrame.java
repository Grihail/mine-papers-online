package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Клиент -> сервер: открыть окно настройки картины. Окно с инвентарём игрока открывает сервер; дальность проверяем сами (до холста, а не до центра сущности, как у ванильного клика — иначе большие картины не открывались бы с края). */
public record PacketOpenFrame(int entityId) implements ModPacket {

    public static final ResourceLocation ID = MinePapers.id("open_frame");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static PacketOpenFrame read(FriendlyByteBuf buf) {
        return new PacketOpenFrame(buf.readVarInt());
    }

    /** Применить пакет от игрока player (в серверном потоке). */
    public static void apply(ServerPlayer player, PacketOpenFrame msg) {
        EntityPicture picture = ServerImages.pictureById(player.level(), msg.entityId);
        if (picture == null || !ServerImages.canEdit(player, picture)) return;
        ContainerPictureFrame.open(player, FrameHolder.of(picture, player));
    }
}
