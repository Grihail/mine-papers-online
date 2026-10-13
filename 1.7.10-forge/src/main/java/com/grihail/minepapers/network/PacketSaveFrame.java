package com.grihail.minepapers.network;

import java.util.UUID;

import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Клиент -> сервер: новые настройки картины target (PictureTarget: сущность или предмет в инвентаре). */
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
        // Материалы рамки и холста — настоящие предметы, их меняют только слоты окна (ContainerPictureFrame).
        NBTTagCompound materials = new NBTTagCompound();
        frame.writeSettings(materials);
        frame.readSettings(msg.settings);
        for (PictureFrame.Material m : frame.materials()) m.read(materials);
        frame.keepServerFields(origin, files, owner, ownerName, player.getUniqueID(), player.getCommandSenderName());
        holder.settingsChanged();
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
