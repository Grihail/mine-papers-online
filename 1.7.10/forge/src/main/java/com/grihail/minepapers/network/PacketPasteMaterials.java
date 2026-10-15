package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.MaterialStack;
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

/** Клиент -> сервер: вставка конфига картины — какие предметы положить в слоты материалов. Сервер берёт их из инвентаря игрока, по одному на каждую картину стопки (прежние возвращает), в творческом режиме недостающие создаёт, а чего нет — перечисляет в чате. Ключи тега — PictureConfig.MATERIALS: "" — вынуть, id — положить; meta — в ключе + "Meta" (-1 — любые). */
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

    private static void apply(EntityPlayerMP player, PacketPasteMaterials msg) {
        FrameHolder holder = ServerImages.changeable(player, msg.target);
        if (holder == null || msg.materials == null) return;
        // По блоку на каждую картину: у стопки картин-предметов — на всю стопку, если сервер это разрешает.
        int count = holder.count();
        if (!MaterialStack.editable(ModConfig.stackMaterials, count)) return;
        PictureFrame.Material[] slots = holder.frame.materials();
        IChatComponent missing = null;
        for (int i = 0; i < slots.length && i < PictureConfig.MATERIALS.length; i++) {
            String key = PictureConfig.MATERIALS[i];
            if (!msg.materials.hasKey(key)) continue;
            String id = msg.materials.getString(key);
            int meta = msg.materials.getInteger(key + "Meta");
            PictureFrame.Material m = slots[i];
            if (id.isEmpty()) {
                giveBack(player, m.stack, count);
                m.set(null);
                continue;
            }
            if (matches(m.stack, id, meta)) continue;
            ItemStack taken = take(player, m, id, meta, count);
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
            giveBack(player, m.stack, count);
            m.set(taken);
        }
        holder.settingsChanged();
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

    /** count подходящих предметов из инвентаря игрока; возвращает один из них. Если столько нет — ничего не забирает и возвращает null. */
    private static ItemStack take(EntityPlayerMP player, PictureFrame.Material m, String id, int meta, int count) {
        ItemStack[] inv = player.inventory.mainInventory;
        int found = 0;
        for (ItemStack stack : inv) {
            if (fits(stack, m, id, meta)) found += stack.stackSize;
        }
        if (found < count) return null;
        ItemStack taken = null;
        for (int s = 0; s < inv.length && count > 0; s++) {
            if (!fits(inv[s], m, id, meta)) continue;
            ItemStack part = player.inventory.decrStackSize(s, Math.min(count, inv[s].stackSize));
            if (taken == null) {
                taken = part.copy();
                taken.stackSize = 1;
            }
            count -= part.stackSize;
        }
        player.openContainer.detectAndSendChanges();
        return taken;
    }

    /** Картину в картину не кладут (и настраиваемую картину-предмет так не забрать). */
    private static boolean fits(ItemStack stack, PictureFrame.Material m, String id, int meta) {
        return matches(stack, id, meta) && m.accepts(stack) && stack.getItem() != Item.getItemFromBlock(MinePapers.frame);
    }

    /** В творческом режиме недостающий предмет берётся из ниоткуда, как из творческого инвентаря (null — нет такого или слот его не примет). */
    private static ItemStack create(PictureFrame.Material m, String id, int meta) {
        Item item = (Item) Item.itemRegistry.getObject(id);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item, 1, Math.max(meta, 0));
        return m.accepts(stack) ? stack : null;
    }

    /** count таких предметов — в инвентарь, а что не влезло — на землю. */
    static void giveBack(EntityPlayerMP player, ItemStack stack, int count) {
        if (stack == null) return;
        while (count > 0) {
            ItemStack part = stack.copy();
            part.stackSize = Math.min(count, stack.getMaxStackSize());
            count -= part.stackSize;
            if (!player.inventory.addItemStackToInventory(part)) player.dropPlayerItemWithRandomChoice(part, false);
        }
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
