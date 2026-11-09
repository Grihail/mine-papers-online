package com.grihail.minepapers.network;

import com.grihail.minepapers.picture.NbtTag;
import java.util.List;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.HolderMaterials;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Клиент -> сервер: вставка конфига картины — какие предметы положить в слоты материалов (кладёт MaterialSlots.paste), а чего не хватило — перечисляется в чате. Ключи тега — PictureConfig.MATERIALS: "" — вынуть, id — положить. */
public record PacketPasteMaterials(int target, CompoundTag materials) implements ModPacket {

    public static final ResourceLocation ID = MinePapers.id("paste_materials");

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
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(target);
        buf.writeNbt(materials);
    }

    public static PacketPasteMaterials read(FriendlyByteBuf buf) {
        return new PacketPasteMaterials(buf.readVarInt(), buf.readNbt());
    }

    /** Применить пакет от игрока player (в серверном потоке). */
    public static void apply(ServerPlayer player, PacketPasteMaterials msg) {
        FrameHolder holder = ServerImages.changeable(player, msg.target);
        if (holder == null) return;
        boolean[] has = new boolean[PictureConfig.MATERIALS.length];
        PictureConfig.Item[] items = new PictureConfig.Item[has.length];
        for (int i = 0; i < has.length; i++) {
            String key = PictureConfig.MATERIALS[i];
            has[i] = msg.materials.contains(key);
            String id = NbtTag.string(msg.materials, key);
            items[i] = id.isEmpty() ? null : new PictureConfig.Item(id, -1);
        }
        List<PictureConfig.Item> missing = new HolderMaterials(holder, player).paste(has, items);
        if (missing.isEmpty()) return;
        MutableComponent names = null;
        for (PictureConfig.Item item : missing) {
            Component name = name(item.id);
            names = names == null ? name.copy() : names.append(", ").append(name);
        }
        player.sendSystemMessage(Component.translatable("minepapers.chat.missing", names).withStyle(ChatFormatting.YELLOW));
    }

    /** Название предмета — переводит клиент игрока. */
    private static Component name(String id) {
        Item item = HolderMaterials.item(id);
        return item != null ? new ItemStack(item).getHoverName() : Component.literal(id);
    }
}
