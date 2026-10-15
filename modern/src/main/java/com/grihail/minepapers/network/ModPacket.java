package com.grihail.minepapers.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.20.5 {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** Пакет мода: имя канала (id) и свои байты (write; читает статический read пакета). Регистрирует пакеты движок — по id, write и read; в 1.20.5+ пакет заодно ванильный CustomPacketPayload. */
//? if >=1.20.5 {
public interface ModPacket extends CustomPacketPayload {
//?} else {
/*public interface ModPacket {
*///?}

    ResourceLocation id();

    void write(FriendlyByteBuf buf);

    //? if >=1.20.5 {
    @Override
    default Type<? extends CustomPacketPayload> type() {
        return new Type<>(id());
    }
    //?}
}
