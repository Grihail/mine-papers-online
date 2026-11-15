package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Сервер -> клиент (команда перезагрузки, см. server.PictureReload): загрузить источник key заново. entity >= 0 — это локальный источник, картинки которого этот игрок выложил на сервер: перечитать его файлы и выложить заново для картины entity (сервер это разрешит, даже если игрок далеко от неё). */
public record PacketReload(String key, int entity) implements ModPacket {

    public static final ResourceLocation ID = MinePapers.id("reload");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(key);
        buf.writeInt(entity);
    }

    public static PacketReload read(FriendlyByteBuf buf) {
        return new PacketReload(buf.readUtf(), buf.readInt());
    }
}
