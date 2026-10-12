package com.grihail.minepapers.network;

import java.util.UUID;

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
        if (picture == null || !ServerImages.canChange(player, picture)) return;
        PictureFrame frame = picture.frame;
        String origin = frame.getOriginKey();
        byte[] files = frame.files;
        UUID owner = frame.owner;
        String ownerName = frame.ownerName;
        // Материалы рамки и холста — настоящие предметы, их меняют только слоты окна (ContainerPictureFrame).
        CompoundTag materials = new CompoundTag();
        frame.writeSettings(materials);
        frame.readSettings(msg.settings);
        // Список картинок на сервере меняет только сервер (PacketOfferFiles), клиенту здесь не верим.
        frame.files = origin != null && origin.equals(frame.getOriginKey()) ? files : PictureFrame.NO_FILES;
        for (PictureFrame.Material m : frame.materials()) m.read(materials);
        // Владельца клиент только просит: замок закрыт — картина становится его (если ничья), открыт — снова ничья. Чужое имя подставить нельзя.
        boolean lock = frame.owner != null;
        frame.owner = owner;
        frame.ownerName = ownerName;
        if (lock && owner == null) {
            frame.owner = player.getUUID();
            frame.ownerName = player.getGameProfile().getName();
        } else if (!lock) {
            frame.owner = null; // сюда доходит только владелец или когда картина ничья (canChange)
            frame.ownerName = "";
        }
        picture.settingsChanged();
    }
}
