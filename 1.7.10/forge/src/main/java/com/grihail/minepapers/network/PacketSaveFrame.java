package com.grihail.minepapers.network;

import java.util.UUID;

import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.MaterialStack;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Клиент -> сервер: новые настройки картины target (PictureTarget: сущность или предмет в инвентаре). Приходит при закрытии окна: блоки материалов, которые выбранному виду не нужны (рамки без рамки, холста без холста), возвращаются игроку. */
public class PacketSaveFrame implements IMessage {

    private int target;
    private NBTTagCompound settings;

    public PacketSaveFrame() {}

    public PacketSaveFrame(FrameHolder holder) {
        this.target = holder.target;
        this.settings = new NBTTagCompound();
        holder.frame.writeSettings(settings);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        target = buf.readInt();
        settings = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(target);
        ByteBufUtils.writeTag(buf, settings);
    }

    private static void apply(EntityPlayerMP player, PacketSaveFrame msg) {
        FrameHolder holder = ServerImages.changeable(player, msg.target);
        if (holder == null || msg.settings == null) return;
        PictureFrame frame = holder.frame;
        String origin = frame.getOriginKey();
        byte[] files = frame.files;
        UUID owner = frame.owner;
        String ownerName = frame.ownerName;
        UUID signer = frame.signer;
        String signerName = frame.signerName;
        // Материалы рамки и холста — настоящие предметы, их меняют только слоты окна (ContainerPictureFrame).
        NBTTagCompound materials = new NBTTagCompound();
        frame.writeSettings(materials);
        frame.readSettings(msg.settings);
        for (PictureFrame.Material m : frame.materials()) m.read(materials);
        frame.keepServerFields(origin, files, owner, ownerName, signer, signerName, player.getUniqueID(),
            player.getCommandSenderName());
        // Подпись уникальна: стопку картин-предметов не подписывают (подпись размножилась бы).
        if (signer == null && holder.count() > 1) {
            frame.signer = null;
            frame.signerName = "";
        }
        if (frame.mayEdit(player.getUniqueID()) && MaterialStack.editable(ModConfig.stackMaterials, holder.count())) {
            returnUnused(player, frame, holder.count());
        }
        holder.settingsChanged();
    }

    /** Блоки рамки и холста, которые виду не нужны, — игроку, по одному с каждой картины. */
    private static void returnUnused(EntityPlayerMP player, PictureFrame frame, int count) {
        if (!frame.usesBorder()) take(player, frame.border, count);
        if (!frame.usesCanvas()) take(player, frame.canvas, count);
    }

    private static void take(EntityPlayerMP player, PictureFrame.Material m, int count) {
        if (m.stack == null) return;
        PacketPasteMaterials.giveBack(player, m.stack, count);
        m.set(null);
    }

    public static class Handler implements IMessageHandler<PacketSaveFrame, IMessage> {

        @Override
        public IMessage onMessage(final PacketSaveFrame msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            // Обработчик работает в сетевом потоке, а мир можно менять только из серверного.
            ServerImages.schedule(() -> apply(player, msg));
            return null;
        }
    }
}
