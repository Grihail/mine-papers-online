package com.grihail.minepapers.network;

import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Клиент -> сервер: новые настройки картины. */
public class PacketSaveFrame implements IMessage {

    private int entityId;
    private NBTTagCompound settings;

    public PacketSaveFrame() {}

    public PacketSaveFrame(EntityPicture picture) {
        this.entityId = picture.getEntityId();
        this.settings = new NBTTagCompound();
        picture.frame.writeSettings(settings);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        settings = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        ByteBufUtils.writeTag(buf, settings);
    }

    private static void apply(EntityPlayerMP player, PacketSaveFrame msg) {
        EntityPicture picture = ServerImages.pictureById(player.worldObj, msg.entityId);
        if (picture == null || !ServerImages.canEdit(player, picture.frame)) return;
        PictureFrame frame = picture.frame;
        String origin = frame.getOriginKey();
        byte[] files = frame.files;
        frame.readSettings(msg.settings);
        // Список картинок на сервере меняет только сервер (PacketOfferFiles), клиенту здесь не верим.
        frame.files = origin != null && origin.equals(frame.getOriginKey()) ? files : PictureFrame.NO_FILES;
        picture.settingsChanged();
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
