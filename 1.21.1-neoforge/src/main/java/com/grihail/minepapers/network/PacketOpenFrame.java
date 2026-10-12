package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент -> сервер: открыть окно настройки картины. Окно с инвентарём игрока открывает сервер; дальность проверяем сами (до холста, а не до центра сущности, как у ванильного клика — иначе большие картины не открывались бы с края). */
public record PacketOpenFrame(int entityId) implements CustomPacketPayload {

    public static final Type<PacketOpenFrame> TYPE = new Type<>(MinePapers.id("open_frame"));
    public static final StreamCodec<ByteBuf, PacketOpenFrame> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PacketOpenFrame::entityId,
        PacketOpenFrame::new);

    @Override
    public Type<PacketOpenFrame> type() {
        return TYPE;
    }

    public static void handle(PacketOpenFrame msg, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        EntityPicture picture = ServerImages.pictureById(player.level(), msg.entityId);
        if (picture == null || !ServerImages.canEdit(player, picture)) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ContainerPictureFrame(id, inventory, picture),
            Component.translatable("minepapers.gui.title")), buf -> buf.writeVarInt(msg.entityId));
    }
}
