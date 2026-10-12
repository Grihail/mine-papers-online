package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент -> сервер: новые настройки картины. */
public record PacketSaveFrame(int entityId, CompoundTag settings) implements CustomPacketPayload {

    public static final Type<PacketSaveFrame> TYPE = new Type<>(MinePapers.id("save_frame"));
    public static final StreamCodec<ByteBuf, PacketSaveFrame> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PacketSaveFrame::entityId,
        ByteBufCodecs.COMPOUND_TAG, PacketSaveFrame::settings,
        PacketSaveFrame::new);

    public static PacketSaveFrame of(EntityPicture picture) {
        CompoundTag settings = new CompoundTag();
        picture.frame.writeSettings(settings);
        return new PacketSaveFrame(picture.getId(), settings);
    }

    @Override
    public Type<PacketSaveFrame> type() {
        return TYPE;
    }

    public static void handle(PacketSaveFrame msg, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        EntityPicture picture = ServerImages.pictureById(player.level(), msg.entityId);
        if (picture == null || !ServerImages.canEdit(player, picture)) return;
        PictureFrame frame = picture.frame;
        String origin = frame.getOriginKey();
        byte[] files = frame.files;
        frame.readSettings(msg.settings);
        // Список картинок на сервере меняет только сервер (PacketOfferFiles), клиенту здесь не верим.
        frame.files = origin != null && origin.equals(frame.getOriginKey()) ? files : PictureFrame.NO_FILES;
        picture.settingsChanged();
    }
}
