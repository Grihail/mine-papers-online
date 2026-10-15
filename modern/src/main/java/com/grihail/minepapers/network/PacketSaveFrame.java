package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.HolderMaterials;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Клиент -> сервер: новые настройки картины target (PictureTarget: сущность или предмет в инвентаре). Приходит при закрытии окна; применяет MaterialSlots.save: блоки материалов, которые выбранному виду не нужны (рамки без рамки, холста без холста), возвращаются игроку. */
public record PacketSaveFrame(int target, CompoundTag settings) implements ModPacket {

    public static final ResourceLocation ID = MinePapers.id("save_frame");

    public static PacketSaveFrame of(FrameHolder holder) {
        CompoundTag settings = new CompoundTag();
        holder.frame.writeSettings(settings);
        return new PacketSaveFrame(holder.target, settings);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(target);
        buf.writeNbt(settings);
    }

    public static PacketSaveFrame read(FriendlyByteBuf buf) {
        return new PacketSaveFrame(buf.readVarInt(), buf.readNbt());
    }

    /** Применить пакет от игрока player (в серверном потоке). */
    public static void apply(ServerPlayer player, PacketSaveFrame msg) {
        FrameHolder holder = ServerImages.changeable(player, msg.target);
        if (holder == null) return;
        PictureFrame frame = holder.frame;
        new HolderMaterials(holder, player).save(() -> {
            CompoundTag materials = new CompoundTag();
            frame.writeSettings(materials);
            frame.readSettings(msg.settings);
            for (PictureFrame.Material m : frame.materials()) m.read(materials);
        }, player.getUUID(), player.getGameProfile().getName());
    }
}
