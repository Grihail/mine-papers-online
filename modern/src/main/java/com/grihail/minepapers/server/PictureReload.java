package com.grihail.minepapers.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketReload;
import com.grihail.minepapers.picture.EntityPicture;
import com.mojang.authlib.GameProfile;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Перезагрузка картинок картин с сервера (команда /minepapers reload, Shift+клик по "Обновить"). Ссылку и онлайн-папку каждый игрок скачивает сам — выбранным игрокам уходит "загрузи источник заново". Картинки с компьютера (config/minepapers, папка) есть только у того, кто их выложил: его клиент перечитывает файлы и выкладывает заново, остальные получают новые картинки с сервера сами. Только в серверном потоке. */
public final class PictureReload {

    /** Столько времени игроку, которого попросили выложить картинки заново, можно выкладывать их для этой картины откуда угодно (подготовка сотен файлов бывает долгой). */
    private static final long GRANT_MS = 5 * 60 * 1000L;
    /** "UUID игрока|id картины" -> измерение картины и до какого времени. */
    private static final Map<String, Grant> GRANTS = new HashMap<String, Grant>();

    private record Grant(ResourceKey<Level> dimension, long until) {}

    private PictureReload() {}

    public static void reset() {
        GRANTS.clear();
    }

    /** Измерение картины entity, если сервер сам попросил игрока выложить её картинки заново (и время не вышло), иначе null. */
    static ResourceKey<Level> grantedDimension(ServerPlayer player, int entity) {
        long now = System.currentTimeMillis();
        GRANTS.values().removeIf(g -> g.until < now);
        Grant grant = GRANTS.get(player.getUUID() + "|" + entity);
        return grant != null ? grant.dimension : null;
    }

    private static MinecraftServer server() {
        return MinePapers.platform.server();
    }

    /** Все картины во всех загруженных мирах. */
    public static List<EntityPicture> allPictures() {
        List<EntityPicture> found = new ArrayList<EntityPicture>();
        MinecraftServer server = server();
        if (server == null) return found;
        for (ServerLevel level : server.getAllLevels()) found.addAll(pictures(level));
        return found;
    }

    public static List<EntityPicture> pictures(ServerLevel level) {
        List<EntityPicture> found = new ArrayList<EntityPicture>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof EntityPicture p && !p.isRemoved()) found.add(p);
        }
        return found;
    }

    public static List<ServerPlayer> allPlayers() {
        MinecraftServer server = server();
        return server != null ? new ArrayList<ServerPlayer>(server.getPlayerList().getPlayers()) : new ArrayList<ServerPlayer>();
    }

    private static ServerPlayer online(UUID id) {
        MinecraftServer server = server();
        return server != null ? server.getPlayerList().getPlayer(id) : null;
    }

    /** Хозяин одиночной игры или открытого для сети мира (null — выделенный сервер). */
    private static ServerPlayer singleplayerHost() {
        MinecraftServer server = server();
        if (server == null || server.isDedicatedServer()) return null;
        GameProfile host = server.getSingleplayerProfile();
        if (host == null) return null;
        //? if >=26.1 {
        /*return online(host.id());
        *///?} else {
        return online(host.getId());
        //?}
    }

    /** Что получилось: сколько картин обновляется, чьих картинок обновить некому (игрок не в сети) и у каких картин неизвестно, кто их выложил. */
    public static final class Result {
        public int pictures;
        public final Set<String> offline = new LinkedHashSet<String>();
        public final List<EntityPicture> unknown = new ArrayList<EntityPicture>();
    }

    /** Перезагрузить картины pictures у игроков to (картинки с компьютера — у всех, см. описание класса). */
    public static Result reload(Collection<EntityPicture> pictures, Collection<ServerPlayer> to) {
        Result result = new Result();
        Set<String> keys = new LinkedHashSet<String>();
        long until = System.currentTimeMillis() + GRANT_MS;
        for (EntityPicture picture : pictures) {
            String origin = picture.frame.getOriginKey();
            if (origin == null) continue; // источник не задан
            result.pictures++;
            if (!picture.frame.hasSharedFiles()) {
                keys.add(picture.frame.getSourceKey());
                continue;
            }
            // Картины прежних версий не знают, кто выложил картинки: у заприваченной это, скорее всего, владелец, в одиночной игре — хозяин мира.
            UUID who = picture.uploader != null ? picture.uploader : picture.frame.owner;
            String name = picture.uploader != null ? picture.uploaderName : picture.frame.ownerName;
            ServerPlayer uploader = who != null ? online(who) : singleplayerHost();
            if (uploader == null) {
                if (who == null) {
                    result.unknown.add(picture);
                } else {
                    result.offline.add(name.isEmpty() ? who.toString() : name);
                }
                continue;
            }
            GRANTS.put(uploader.getUUID() + "|" + picture.getId(), new Grant(picture.level().dimension(), until));
            MinePapers.platform.sendToPlayer(uploader, new PacketReload(origin, picture.getId()));
        }
        for (ServerPlayer player : to) {
            for (String key : keys) MinePapers.platform.sendToPlayer(player, new PacketReload(key, -1));
        }
        return result;
    }

    /** Итог — тому, кто перезагружал (в чат или в вывод командного блока). */
    public static void report(Consumer<Component> out, Result result) {
        out.accept(Component.translatable("minepapers.command.reload.done", result.pictures));
        for (String name : result.offline) {
            out.accept(Component.translatable("minepapers.command.reload.offline", name).withStyle(ChatFormatting.YELLOW));
        }
        for (EntityPicture p : result.unknown) {
            out.accept(Component.translatable("minepapers.command.reload.unknown", p.frame.x, p.frame.y, p.frame.z).withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Shift+клик по "Обновить" в окне картины entity: у всех игроков. Можно тому, кто может менять картину, и оператору. */
    public static void fromWindow(ServerPlayer player, int entity) {
        EntityPicture picture = ServerImages.pictureById(player.level(), entity);
        if (picture == null) return;
        if (!ServerImages.canChange(player, picture) && !CommandMinePapers.op(player.createCommandSourceStack())) return;
        List<EntityPicture> one = new ArrayList<EntityPicture>();
        one.add(picture);
        report(player::sendSystemMessage, reload(one, allPlayers()));
    }
}
