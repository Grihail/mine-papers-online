package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.MaterialStack;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.PictureFrame;
import com.grihail.minepapers.server.ServerImages;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент -> сервер: вставка конфига картины — какие предметы положить в слоты материалов. Сервер берёт их из инвентаря игрока, по одному на каждую картину стопки (прежние возвращает), в творческом режиме недостающие создаёт, а чего нет — перечисляет в чате. Ключи тега — PictureConfig.MATERIALS: "" — вынуть, id — положить. */
public record PacketPasteMaterials(int target, CompoundTag materials) implements CustomPacketPayload {

    public static final Type<PacketPasteMaterials> TYPE = new Type<>(MinePapers.id("paste_materials"));
    public static final StreamCodec<ByteBuf, PacketPasteMaterials> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PacketPasteMaterials::target,
        ByteBufCodecs.COMPOUND_TAG, PacketPasteMaterials::materials,
        PacketPasteMaterials::new);

    /** target — PictureTarget. */
    public static PacketPasteMaterials of(int target, PictureConfig config) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < PictureConfig.MATERIALS.length; i++) {
            if (!config.hasMaterial[i]) continue;
            PictureConfig.Item item = config.materials[i];
            tag.putString(PictureConfig.MATERIALS[i], item != null ? item.id : "");
        }
        return new PacketPasteMaterials(target, tag);
    }

    @Override
    public Type<PacketPasteMaterials> type() {
        return TYPE;
    }

    public static void handle(PacketPasteMaterials msg, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        FrameHolder holder = ServerImages.changeable(player, msg.target);
        if (holder == null) return;
        // По блоку на каждую картину: у стопки картин-предметов — на всю стопку, если сервер это разрешает.
        int count = holder.count();
        if (!MaterialStack.editable(ModConfig.stackMaterials, count)) return;
        PictureFrame.Material[] slots = holder.frame.materials();
        MutableComponent missing = null;
        for (int i = 0; i < slots.length && i < PictureConfig.MATERIALS.length; i++) {
            String key = PictureConfig.MATERIALS[i];
            if (!msg.materials.contains(key)) continue;
            String id = msg.materials.getString(key);
            PictureFrame.Material m = slots[i];
            ItemStack current = m.stack();
            if (id.isEmpty()) {
                giveBack(player, current, count);
                m.set(ItemStack.EMPTY);
                continue;
            }
            if (matches(current, id)) continue;
            ItemStack taken = take(player, m, id, count);
            if (taken.isEmpty() && player.isCreative()) taken = create(m, id);
            if (taken.isEmpty()) {
                Component name = name(id);
                missing = missing == null ? name.copy() : missing.append(", ").append(name);
                continue;
            }
            giveBack(player, current, count);
            m.set(taken);
        }
        holder.settingsChanged();
        if (missing != null) {
            player.sendSystemMessage(Component.translatable("minepapers.chat.missing", missing).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static boolean matches(ItemStack stack, String id) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
    }

    /** count подходящих предметов из инвентаря игрока; возвращает один из них. Если столько нет — ничего не забирает и возвращает пусто. */
    private static ItemStack take(ServerPlayer player, PictureFrame.Material m, String id, int count) {
        Inventory inv = player.getInventory();
        int found = 0;
        for (ItemStack stack : inv.items) {
            if (fits(stack, m, id)) found += stack.getCount();
        }
        if (found < count) return ItemStack.EMPTY;
        ItemStack taken = ItemStack.EMPTY;
        for (int s = 0; s < inv.items.size() && count > 0; s++) {
            ItemStack stack = inv.items.get(s);
            if (!fits(stack, m, id)) continue;
            ItemStack part = inv.removeItem(s, Math.min(count, stack.getCount()));
            if (taken.isEmpty()) taken = part.copyWithCount(1);
            count -= part.getCount();
        }
        return taken;
    }

    /** Картину в картину не кладут (и настраиваемую картину-предмет так не забрать). */
    private static boolean fits(ItemStack stack, PictureFrame.Material m, String id) {
        return matches(stack, id) && m.accepts(stack) && !stack.is(MinePapers.FRAME.get());
    }

    /** В творческом режиме недостающий предмет берётся из ниоткуда, как из творческого инвентаря (пусто — нет такого или слот его не примет). */
    private static ItemStack create(PictureFrame.Material m, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key != null ? BuiltInRegistries.ITEM.getOptional(key).orElse(null) : null;
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        return m.accepts(stack) ? stack : ItemStack.EMPTY;
    }

    /** count таких предметов — в инвентарь, а что не влезло — на землю. */
    private static void giveBack(ServerPlayer player, ItemStack stack, int count) {
        if (stack.isEmpty()) return;
        while (count > 0) {
            ItemStack part = stack.copyWithCount(Math.min(count, stack.getMaxStackSize()));
            count -= part.getCount();
            if (!player.getInventory().add(part)) player.drop(part, false);
        }
    }

    /** Название предмета — переводит клиент игрока. */
    private static Component name(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key != null ? BuiltInRegistries.ITEM.getOptional(key).orElse(null) : null;
        return item != null ? new ItemStack(item).getHoverName() : Component.literal(id);
    }
}
