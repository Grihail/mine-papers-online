package com.grihail.minepapers.network;

import java.util.UUID;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.MaterialStack;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент -> сервер: новые настройки картины target (PictureTarget: сущность или предмет в инвентаре). Приходит при закрытии окна: блоки материалов, которые выбранному виду не нужны (рамки без рамки, холста без холста), возвращаются игроку. */
public record PacketSaveFrame(int target, CompoundTag settings) implements CustomPacketPayload {

    public static final Type<PacketSaveFrame> TYPE = new Type<>(MinePapers.id("save_frame"));
    public static final StreamCodec<ByteBuf, PacketSaveFrame> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PacketSaveFrame::target,
        ByteBufCodecs.COMPOUND_TAG, PacketSaveFrame::settings,
        PacketSaveFrame::new);

    public static PacketSaveFrame of(FrameHolder holder) {
        CompoundTag settings = new CompoundTag();
        holder.frame.writeSettings(settings);
        return new PacketSaveFrame(holder.target, settings);
    }

    @Override
    public Type<PacketSaveFrame> type() {
        return TYPE;
    }

    public static void handle(PacketSaveFrame msg, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        FrameHolder holder = ServerImages.changeable(player, msg.target);
        if (holder == null) return;
        PictureFrame frame = holder.frame;
        String origin = frame.getOriginKey();
        byte[] files = frame.files;
        UUID owner = frame.owner;
        String ownerName = frame.ownerName;
        // Материалы рамки и холста — настоящие предметы, их меняют только слоты окна (ContainerPictureFrame).
        CompoundTag materials = new CompoundTag();
        frame.writeSettings(materials);
        frame.readSettings(msg.settings);
        for (PictureFrame.Material m : frame.materials()) m.read(materials);
        frame.keepServerFields(origin, files, owner, ownerName, player.getUUID(), player.getGameProfile().getName());
        if (frame.mayEdit(player.getUUID()) && MaterialStack.editable(ModConfig.stackMaterials, holder.count())) {
            if (!frame.usesBorder()) take(player, frame.border, holder.count());
            if (!frame.usesCanvas()) take(player, frame.canvas, holder.count());
        }
        holder.settingsChanged();
    }

    /** Блоки материала, который виду не нужен, — игроку, по одному с каждой картины. */
    private static void take(ServerPlayer player, PictureFrame.Material m, int count) {
        if (m.stack().isEmpty()) return;
        PacketPasteMaterials.giveBack(player, m.stack(), count);
        m.set(ItemStack.EMPTY);
    }
}
