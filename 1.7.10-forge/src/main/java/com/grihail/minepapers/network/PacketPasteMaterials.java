package com.grihail.minepapers.network;

import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/** Клиент -> сервер: вставка конфига картины — какие предметы положить в слоты материалов. Сервер берёт их из инвентаря игрока (прежние возвращает), в творческом режиме недостающие создаёт, а чего нет — перечисляет в чате. Ключи тега — PictureConfig.MATERIALS: "" — вынуть, id — положить; meta — в ключе + "Meta" (-1 — любые). */
public class PacketPasteMaterials implements IMessage {

    private int entityId;
    private NBTTagCompound materials;

    public PacketPasteMaterials() {}

    public PacketPasteMaterials(int entityId, PictureConfig config) {
        this.entityId = entityId;
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
        entityId = buf.readInt();
        materials = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        ByteBufUtils.writeTag(buf, materials);
    }

    private static void apply(EntityPlayerMP player, PacketPasteMaterials msg) {
        EntityPicture picture = ServerImages.pictureById(player.worldObj, msg.entityId);
        if (picture == null || msg.materials == null || !ServerImages.canChange(player, picture)) return;
        PictureFrame.Material[] slots = picture.frame.materials();
        IChatComponent missing = null;
        for (int i = 0; i < slots.length && i < PictureConfig.MATERIALS.length; i++) {
            String key = PictureConfig.MATERIALS[i];
            if (!msg.materials.hasKey(key)) continue;
            String id = msg.materials.getString(key);
            int meta = msg.materials.getInteger(key + "Meta");
            PictureFrame.Material m = slots[i];
            if (id.isEmpty()) {
                giveBack(player, m.stack);
                m.set(null);
                continue;
            }
            if (matches(m.stack, id, meta)) continue;
            ItemStack taken = take(player, m, id, meta);
            if (taken == null && player.capabilities.isCreativeMode) taken = create(m, id, meta);
            if (taken == null) {
                IChatComponent name = name(id, meta);
                if (missing == null) {
                    missing = name;
                } else {
                    missing.appendText(", ").appendSibling(name);
                }
                continue;
            }
            giveBack(player, m.stack);
            m.set(taken);
        }
        picture.settingsChanged();
        if (missing != null) {
            IChatComponent text = new ChatComponentTranslation("minepapers.chat.missing", missing);
            text.getChatStyle().setColor(EnumChatFormatting.YELLOW);
            player.addChatMessage(text);
        }
    }

    private static boolean matches(ItemStack stack, String id, int meta) {
        return stack != null && id.equals(Item.itemRegistry.getNameForObject(stack.getItem()))
            && (meta < 0 || stack.getItemDamage() == meta);
    }

    /** Один подходящий предмет из инвентаря игрока (или null). */
    private static ItemStack take(EntityPlayerMP player, PictureFrame.Material m, String id, int meta) {
        ItemStack[] inv = player.inventory.mainInventory;
        for (int s = 0; s < inv.length; s++) {
            if (matches(inv[s], id, meta) && m.accepts(inv[s])) {
                ItemStack taken = player.inventory.decrStackSize(s, 1);
                player.inventoryContainer.detectAndSendChanges();
                return taken;
            }
        }
        return null;
    }

    /** В творческом режиме недостающий предмет берётся из ниоткуда, как из творческого инвентаря (null — нет такого или слот его не примет). */
    private static ItemStack create(PictureFrame.Material m, String id, int meta) {
        Item item = (Item) Item.itemRegistry.getObject(id);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item, 1, Math.max(meta, 0));
        return m.accepts(stack) ? stack : null;
    }

    private static void giveBack(EntityPlayerMP player, ItemStack stack) {
        if (stack == null) return;
        ItemStack copy = stack.copy();
        if (!player.inventory.addItemStackToInventory(copy)) player.dropPlayerItemWithRandomChoice(copy, false);
    }

    /** Название предмета — переводит клиент игрока. */
    private static IChatComponent name(String id, int meta) {
        Item item = (Item) Item.itemRegistry.getObject(id);
        if (item == null) return new ChatComponentText(id);
        return new ChatComponentTranslation(new ItemStack(item, 1, Math.max(meta, 0)).getUnlocalizedName() + ".name");
    }

    public static class Handler implements IMessageHandler<PacketPasteMaterials, IMessage> {
        @Override
        public IMessage onMessage(final PacketPasteMaterials msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerImages.schedule(() -> apply(player, msg));
            return null;
        }
    }
}
