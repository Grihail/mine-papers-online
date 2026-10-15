package com.grihail.minepapers.network;

import java.util.List;

import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.HolderMaterials;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.server.ServerImages;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ITextComponent;

/** Клиент -> сервер: вставка конфига картины — какие предметы положить в слоты материалов (кладёт MaterialSlots.paste), а чего не хватило — перечисляется в чате. Ключи тега — PictureConfig.MATERIALS: "" — вынуть, id — положить; meta — в ключе + "Meta" (-1 — любые). */
public class PacketPasteMaterials implements IMessage {

    private int target;
    private NBTTagCompound materials;

    public PacketPasteMaterials() {}

    public PacketPasteMaterials(int target, PictureConfig config) {
        this.target = target;
        this.materials = new NBTTagCompound();
        for (int i = 0; i < PictureConfig.MATERIALS.length; i++) {
            if (!config.hasMaterial[i]) continue;
            PictureConfig.Item item = config.materials[i];
            materials.setString(PictureConfig.MATERIALS[i], item != null ? item.id : "");
            materials.setInteger(PictureConfig.MATERIALS[i] + "Meta", item != null ? item.meta : -1);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        target = buf.readInt();
        materials = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(target);
        ByteBufUtils.writeTag(buf, materials);
    }

    public static void apply(EntityPlayerMP player, PacketPasteMaterials msg) {
        FrameHolder holder = ServerImages.changeable(player, msg.target);
        if (holder == null || msg.materials == null) return;
        boolean[] has = new boolean[PictureConfig.MATERIALS.length];
        PictureConfig.Item[] items = new PictureConfig.Item[has.length];
        for (int i = 0; i < has.length; i++) {
            String key = PictureConfig.MATERIALS[i];
            has[i] = msg.materials.hasKey(key);
            String id = msg.materials.getString(key);
            items[i] = id.isEmpty() ? null : new PictureConfig.Item(id, msg.materials.getInteger(key + "Meta"));
        }
        List<PictureConfig.Item> missing = new HolderMaterials(holder, player).paste(has, items);
        player.openContainer.detectAndSendChanges();
        if (missing.isEmpty()) return;
        ITextComponent names = null;
        for (PictureConfig.Item item : missing) {
            ITextComponent name = name(item.id, item.meta);
            if (names == null) {
                names = name;
            } else {
                names.appendText(", ").appendSibling(name);
            }
        }
        ITextComponent text = new TextComponentTranslation("minepapers.chat.missing", names);
        text.getStyle().setColor(TextFormatting.YELLOW);
        player.sendMessage(text);
    }

    /** Название предмета — переводит клиент игрока. */
    private static ITextComponent name(String id, int meta) {
        Item item = Item.getByNameOrId(id);
        if (item == null) return new TextComponentString(id);
        return new TextComponentTranslation(new ItemStack(item, 1, Math.max(meta, 0)).getTranslationKey() + ".name");
    }

    public static class Handler implements IMessageHandler<PacketPasteMaterials, IMessage> {
        @Override
        public IMessage onMessage(final PacketPasteMaterials msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            ServerImages.schedule(() -> apply(player, msg));
            return null;
        }
    }
}
