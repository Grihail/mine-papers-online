package com.grihail.minepapers.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.HolderMaterials;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Клиент -> сервер: новые настройки картины target (PictureTarget: сущность или предмет в инвентаре). Приходит при закрытии окна; применяет MaterialSlots.save: блоки материалов, которые выбранному виду не нужны (рамки без рамки, холста без холста), возвращаются игроку. */
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
        int oldPuzzle = frame.puzzle;
        UUID oldOwner = frame.owner;
        new HolderMaterials(holder, player).save(() -> {
            NBTTagCompound materials = new NBTTagCompound();
            frame.writeSettings(materials);
            frame.readSettings(msg.settings);
            for (PictureFrame.Material m : frame.materials()) m.read(materials);
        }, player.getUniqueID(), player.getCommandSenderName());
        if (holder.picture != null) syncPuzzle(holder.picture, player, frame.puzzle != oldPuzzle || !Objects.equals(frame.owner, oldOwner));
    }

    /** Общие настройки группы пазла (PictureSettings.copyPuzzleSettings). Картина только что вошла в группу (joined) — берёт их у группы; иначе её настройки расходятся по остальным кускам, которые игрок может менять. */
    private static void syncPuzzle(EntityPicture picture, EntityPlayerMP player, boolean joined) {
        List<EntityPicture> members = FrameGeometry.puzzleMembers(picture.worldObj, picture);
        if (members.isEmpty()) return;
        if (joined) {
            picture.frame.copyPuzzleSettings(members.get(0).frame);
            picture.settingsChanged();
            return;
        }
        for (EntityPicture other : members) {
            if (!other.frame.mayEdit(player.getUniqueID())) continue;
            other.frame.copyPuzzleSettings(picture.frame);
            other.settingsChanged();
        }
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
