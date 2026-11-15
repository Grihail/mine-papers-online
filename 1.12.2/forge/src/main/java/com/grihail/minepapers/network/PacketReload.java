package com.grihail.minepapers.network;

import com.grihail.minepapers.MinePapers;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Сервер -> клиент (команда перезагрузки, см. server.PictureReload): загрузить источник key заново. entity >= 0 — это локальный источник, картинки которого этот игрок выложил на сервер: перечитать его файлы и выложить заново для картины entity (сервер это разрешит, даже если игрок далеко от неё). */
public class PacketReload implements IMessage {

    private String key;
    private int entity;

    public PacketReload() {}

    public PacketReload(String key, int entity) {
        this.key = key;
        this.entity = entity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        key = ByteBufUtils.readUTF8String(buf);
        entity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, key);
        buf.writeInt(entity);
    }

    public static class Handler implements IMessageHandler<PacketReload, IMessage> {
        @Override
        public IMessage onMessage(PacketReload msg, MessageContext ctx) {
            MinePapers.proxy.onReload(msg.key, msg.entity);
            return null;
        }
    }
}
