package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.EntityPicture;
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

/** Клиент -> сервер: вставка конфига картины — какие предметы положить в слоты материалов. Сервер берёт их из инвентаря игрока (прежние возвращает), в творческом режиме недостающие создаёт, а чего нет — перечисляет в чате. Ключи тега — PictureConfig.MATERIALS: "" — вынуть, id — положить. */
public record PacketPasteMaterials(int entityId, CompoundTag materials) implements CustomPacketPayload {

    public static final Type<PacketPasteMaterials> TYPE = new Type<>(MinePapers.id("paste_materials"));
    public static final StreamCodec<ByteBuf, PacketPasteMaterials> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PacketPasteMaterials::entityId,
        ByteBufCodecs.COMPOUND_TAG, PacketPasteMaterials::materials,
        PacketPasteMaterials::new);

    public static PacketPasteMaterials of(int entityId, PictureConfig config) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < PictureConfig.MATERIALS.length; i++) {
            if (!config.hasMaterial[i]) continue;
            PictureConfig.Item item = config.materials[i];
            tag.putString(PictureConfig.MATERIALS[i], item != null ? item.id : "");
        }
        return new PacketPasteMaterials(entityId, tag);
    }

    @Override
    public Type<PacketPasteMaterials> type() {
        return TYPE;
    }

    public static void handle(PacketPasteMaterials msg, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        EntityPicture picture = ServerImages.pictureById(player.level(), msg.entityId);
        if (picture == null || !ServerImages.canChange(player, picture)) return;
        PictureFrame.Material[] slots = picture.frame.materials();
        MutableComponent missing = null;
        for (int i = 0; i < slots.length && i < PictureConfig.MATERIALS.length; i++) {
            String key = PictureConfig.MATERIALS[i];
            if (!msg.materials.contains(key)) continue;
            String id = msg.materials.getString(key);
            PictureFrame.Material m = slots[i];
            ItemStack current = m.stack();
            if (id.isEmpty()) {
                giveBack(player, current);
                m.set(ItemStack.EMPTY);
                continue;
            }
            if (matches(current, id)) continue;
            ItemStack taken = take(player, m, id);
            if (taken.isEmpty() && player.isCreative()) taken = create(m, id);
            if (taken.isEmpty()) {
                Component name = name(id);
                missing = missing == null ? name.copy() : missing.append(", ").append(name);
                continue;
            }
            giveBack(player, current);
            m.set(taken);
        }
        picture.settingsChanged();
        if (missing != null) {
            player.sendSystemMessage(Component.translatable("minepapers.chat.missing", missing).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static boolean matches(ItemStack stack, String id) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
    }

    /** Один подходящий предмет из инвентаря игрока (или пусто). */
    private static ItemStack take(ServerPlayer player, PictureFrame.Material m, String id) {
        Inventory inv = player.getInventory();
        for (int s = 0; s < inv.items.size(); s++) {
            ItemStack stack = inv.items.get(s);
            if (matches(stack, id) && m.accepts(stack)) return inv.removeItem(s, 1);
        }
        return ItemStack.EMPTY;
    }

    /** В творческом режиме недостающий предмет берётся из ниоткуда, как из творческого инвентаря (пусто — нет такого или слот его не примет). */
    private static ItemStack create(PictureFrame.Material m, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key != null ? BuiltInRegistries.ITEM.getOptional(key).orElse(null) : null;
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        return m.accepts(stack) ? stack : ItemStack.EMPTY;
    }

    private static void giveBack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack copy = stack.copy();
        if (!player.getInventory().add(copy)) player.drop(copy, false);
    }

    /** Название предмета — переводит клиент игрока. */
    private static Component name(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key != null ? BuiltInRegistries.ITEM.getOptional(key).orElse(null) : null;
        return item != null ? new ItemStack(item).getHoverName() : Component.literal(id);
    }
}
