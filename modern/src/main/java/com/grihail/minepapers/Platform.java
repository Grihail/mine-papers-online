package com.grihail.minepapers;

import com.grihail.minepapers.network.ModPacket;
import com.grihail.minepapers.picture.ContainerPictureFrame;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

/** Всё, что общему коду версии нужно от движка (NeoForge, Fabric): пакеты, окно с данными для клиента, текущий сервер. Движок ставит свою реализацию в MinePapers.init. */
public interface Platform {

    /** Пакет с клиента на сервер. */
    void sendToServer(ModPacket packet);

    void sendToPlayer(ServerPlayer player, ModPacket packet);

    /** Открыть игроку окно картины: клиент создаст своё меню по data (ContainerPictureFrame.fromData). */
    void openMenu(ServerPlayer player, MenuProvider provider, ContainerPictureFrame.OpenData data);

    /** Запущенный сервер (на клиенте — встроенный одиночной игры) или null. */
    MinecraftServer server();
}
