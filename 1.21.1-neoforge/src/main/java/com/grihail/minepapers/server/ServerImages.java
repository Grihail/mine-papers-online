package com.grihail.minepapers.server;

import java.io.File;

import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureSettings;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Права игроков на картины и связь общего хранилища картинок (ImageStore) с миром 1.21. Всё работает в серверном потоке: обработчики пакетов NeoForge вызываются в нём. Мир для ImageStore — ResourceKey измерения, игрок — ServerPlayer. */
public final class ServerImages extends ServerHost {

    private static MinecraftServer server;

    private ServerImages() {}

    public static void install() {
        ImageStore.install(new ServerImages());
    }

    /** Настройки открывают кликом по любой части картины (см. PictureSettings.editReach). Расстояние — ванильным Entity.distanceToSqr: его поправляют моды, которые переносят картину в свои координаты (движущиеся конструкции). */
    public static boolean canEdit(ServerPlayer player, EntityPicture picture) {
        double reach = picture.frame.editReach();
        return player.distanceToSqr(picture.position()) <= reach * reach;
    }

    /** Менять настройки, материалы и картинки: рядом с картиной и она не запривачена другим игроком. Открыть окно (только заголовок и описание) и сломать картину может любой рядом — см. canEdit. */
    public static boolean canChange(ServerPlayer player, EntityPicture picture) {
        return canEdit(player, picture) && picture.frame.mayEdit(player.getUUID());
    }

    public static EntityPicture pictureById(Level level, int entityId) {
        Entity entity = level != null ? level.getEntity(entityId) : null;
        return entity instanceof EntityPicture picture && !picture.isRemoved() ? picture : null;
    }

    // --------------------------------------------------------------------- Пакеты

    public static void offer(ServerPlayer player, int entityId, String origin, byte[] hashes) {
        server = player.server;
        EntityPicture picture = pictureById(player.level(), entityId);
        if (picture == null || !canChange(player, picture)) return;
        ImageStore.offer(player.getUUID(), player, player.level().dimension(), entityId, origin, hashes);
    }

    public static void upload(ServerPlayer player, byte[] hash, int total, int offset, byte[] data) {
        server = player.server;
        ImageStore.upload(player.getUUID(), player.getName().getString(), hash, total, offset, data);
    }

    public static void request(ServerPlayer player, byte[] hashes) {
        server = player.server;
        ImageStore.request(player.getUUID(), player, hashes);
    }

    // --------------------------------------------------------------------- ServerHost

    @Override
    public File worldDir() {
        return server != null ? server.getWorldPath(LevelResource.ROOT).toFile() : null;
    }

    @Override
    public PictureSettings frame(Object world, int entityId) {
        EntityPicture picture = picture(world, entityId);
        return picture != null ? picture.frame : null;
    }

    @Override
    public void settingsChanged(Object world, int entityId) {
        EntityPicture picture = picture(world, entityId);
        if (picture != null) picture.settingsChanged();
    }

    @SuppressWarnings("unchecked")
    private static EntityPicture picture(Object world, int entityId) {
        return server != null ? pictureById(server.getLevel((ResourceKey<Level>) world), entityId) : null;
    }

    @Override
    public void sendNeed(Object player, byte[] hashes) {
        PacketDistributor.sendToPlayer((ServerPlayer) player, new PacketHashes.Need(hashes));
    }

    @Override
    public void sendChunk(Object player, byte[] hash, int total, int offset, byte[] data) {
        PacketDistributor.sendToPlayer((ServerPlayer) player, new PacketFileChunk(hash, total, offset, data));
    }

    public static final class Events {
        @SubscribeEvent
        public void onServerTick(ServerTickEvent.Pre event) {
            server = event.getServer();
            ImageStore.tick();
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            ImageStore.logout(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            ImageStore.reset();
            server = null;
        }
    }
}
