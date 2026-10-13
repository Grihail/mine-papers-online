package com.grihail.minepapers.server;

import java.io.File;
import java.util.UUID;

import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameHolder;
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

/** Права игроков на картины и связь общего хранилища картинок (ImageStore) с миром 1.21. Всё работает в серверном потоке: обработчики пакетов NeoForge вызываются в нём. Мир для ImageStore — ResourceKey измерения (у картины-предмета — UUID игрока), игрок — ServerPlayer. */
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

    /** Картина-предмет в инвентаре игрока всегда под рукой; у сущности — как canEdit выше. */
    public static boolean canEdit(ServerPlayer player, FrameHolder holder) {
        return holder.picture != null ? canEdit(player, holder.picture) : holder.exists();
    }

    public static boolean canChange(ServerPlayer player, FrameHolder holder) {
        return canEdit(player, holder) && holder.frame.mayEdit(player.getUUID());
    }

    /** Картина target (PictureTarget), которую игрок может менять, или null. */
    public static FrameHolder changeable(ServerPlayer player, int target) {
        FrameHolder holder = FrameHolder.of(player, target);
        return holder != null && canChange(player, holder) ? holder : null;
    }

    public static EntityPicture pictureById(Level level, int entityId) {
        Entity entity = level != null ? level.getEntity(entityId) : null;
        return entity instanceof EntityPicture picture && !picture.isRemoved() ? picture : null;
    }

    // --------------------------------------------------------------------- Пакеты

    public static void offer(ServerPlayer player, int target, String origin, byte[] hashes) {
        server = player.server;
        FrameHolder holder = changeable(player, target);
        if (holder == null) return;
        Object world = holder.isItem() ? player.getUUID() : player.level().dimension();
        ImageStore.offer(player.getUUID(), player, world, target, origin, hashes);
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
    public PictureSettings frame(Object world, int target) {
        FrameHolder holder = holder(world, target);
        return holder != null ? holder.frame : null;
    }

    @Override
    public void shareFiles(Object world, int target, byte[] files) {
        FrameHolder holder = holder(world, target);
        if (holder == null) return;
        holder.frame.files = files;
        holder.settingsChanged();
    }

    /** Мир картины-сущности — ResourceKey измерения, картины-предмета — UUID игрока, у которого она лежит. */
    @SuppressWarnings("unchecked")
    private static FrameHolder holder(Object world, int target) {
        if (server == null) return null;
        if (world instanceof UUID id) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player != null ? FrameHolder.of(player, target) : null;
        }
        EntityPicture picture = pictureById(server.getLevel((ResourceKey<Level>) world), target);
        return picture != null ? FrameHolder.of(picture, null) : null;
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
