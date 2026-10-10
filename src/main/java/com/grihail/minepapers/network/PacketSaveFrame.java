package com.grihail.minepapers.network;

import com.grihail.minepapers.server.ServerImages;
import com.grihail.minepapers.tile.FrameStructure;
import com.grihail.minepapers.tile.TilePictureFrame;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Клиент -> сервер: новые настройки рамки. */
public class PacketSaveFrame implements IMessage {

    private int x, y, z;
    private NBTTagCompound settings;

    public PacketSaveFrame() {}

    public PacketSaveFrame(TilePictureFrame tile) {
        this.x = tile.xCoord;
        this.y = tile.yCoord;
        this.z = tile.zCoord;
        this.settings = new NBTTagCompound();
        tile.writeSettings(settings);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        settings = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        ByteBufUtils.writeTag(buf, settings);
    }

    private static void apply(EntityPlayerMP player, PacketSaveFrame msg) {
        TilePictureFrame frame = ServerImages.frameAt(player.worldObj, msg.x, msg.y, msg.z);
        if (frame == null || !ServerImages.canEdit(player, frame)) return;
        String origin = frame.getOriginKey();
        byte[] files = frame.files;
        // Холст мог поменять размер/место — переставляем части хитбокса.
        FrameStructure.clear(player.worldObj, frame);
        frame.readSettings(msg.settings);
        // Список картинок на сервере меняет только сервер (PacketOfferFiles), клиенту здесь не верим.
        frame.files = origin != null && origin.equals(frame.getOriginKey()) ? files : TilePictureFrame.NO_FILES;
        FrameStructure.place(player.worldObj, frame);
        frame.markDirty();
        player.worldObj.markBlockForUpdate(msg.x, msg.y, msg.z);
    }

    public static class Handler implements IMessageHandler<PacketSaveFrame, IMessage> {

        @Override
        public IMessage onMessage(final PacketSaveFrame msg, MessageContext ctx) {
            if (msg.settings == null) return null;
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            // Обработчик работает в сетевом потоке, а мир можно менять только из серверного.
            ServerImages.schedule(() -> apply(player, msg));
            return null;
        }
    }
}
