package com.grihail.minepapers.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.network.SharedImage;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Картинки с компьютеров игроков, выложенные на сервер (папка minepapers в папке мира).
 * Всё работает в серверном потоке: обработчики пакетов NeoForge вызываются в нём.
 */
public final class ServerImages {

    private static final Logger LOG = LogUtils.getLogger();

    /** Скачивание: куски по 32 КБ, не больше 3 за тик на игрока (~1.9 МБ/с) и 24 за тик на всех — чтобы не забить канал. */
    private static final int CHUNK = 32 * 1024;
    private static final int CHUNKS_PER_PLAYER_TICK = 3;
    private static final int CHUNKS_PER_TICK = 24;
    private static final long OFFER_TIMEOUT_MS = 10 * 60_000L;
    private static final int MAX_PARTIAL_UPLOADS = 4;
    private static final int MAX_QUEUED_DOWNLOADS = 4096;
    /** Недавно отданные файлы держим в памяти: картинку обычно скачивают все игроки рядом с ней. */
    private static final long MEMORY_CACHE_BYTES = 32L * 1024 * 1024;
    private static final double EDIT_DISTANCE = 10.0;

    private static final List<Offer> OFFERS = new ArrayList<Offer>();
    private static final Map<UUID, Peer> PEERS = new HashMap<UUID, Peer>();
    private static final Set<String> PRESENT = new HashSet<String>();
    private static final LinkedHashMap<String, byte[]> MEMORY = new LinkedHashMap<String, byte[]>(16, 0.75f, true);
    private static long memoryBytes;
    private static MinecraftServer server;
    private static Path dir;

    private ServerImages() {}

    /** Мир выгружен — забываем всё, что относилось к нему. */
    public static void reset() {
        OFFERS.clear();
        PEERS.clear();
        PRESENT.clear();
        MEMORY.clear();
        memoryBytes = 0;
        server = null;
        dir = null;
    }

    /** Настройки открывают кликом по любой части картины — проверяем расстояние до холста, а не до его центра. */
    public static boolean canEdit(ServerPlayer player, PictureFrame frame) {
        AABB box = FrameGeometry.canvasBox(frame);
        double dx = Math.max(Math.max(box.minX - player.getX(), player.getX() - box.maxX), 0);
        double dy = Math.max(Math.max(box.minY - player.getY(), player.getY() - box.maxY), 0);
        double dz = Math.max(Math.max(box.minZ - player.getZ(), player.getZ() - box.maxZ), 0);
        return dx * dx + dy * dy + dz * dz <= EDIT_DISTANCE * EDIT_DISTANCE;
    }

    public static EntityPicture pictureById(Level level, int entityId) {
        Entity entity = level != null ? level.getEntity(entityId) : null;
        return entity instanceof EntityPicture picture && !picture.isRemoved() ? picture : null;
    }

    // ---------------------------------------------------------------------
    // Выкладывание

    public static void offer(ServerPlayer player, int entityId, String origin, byte[] hashes) {
        server = player.server;
        EntityPicture picture = pictureById(player.level(), entityId);
        PictureFrame frame = picture != null ? picture.frame : null;
        if (frame == null || hashes.length == 0 || hashes.length > PictureFrame.MAX_FILES * SharedImage.HASH_BYTES
            || !origin.equals(frame.getOriginKey()) || frame.isNetworkSource()
            || !canEdit(player, frame)) {
            return;
        }
        ResourceKey<Level> dim = player.level().dimension();
        OFFERS.removeIf(o -> o.isFor(dim, entityId));
        Offer offer = new Offer(dim, entityId, origin, hashes);
        Set<String> missing = new LinkedHashSet<String>();
        for (int i = 0; i < hashes.length; i += SharedImage.HASH_BYTES) {
            String hex = SharedImage.hex(hashes, i, SharedImage.HASH_BYTES);
            if (!has(hex)) missing.add(hex);
        }
        if (missing.isEmpty()) {
            apply(offer);
            return;
        }
        OFFERS.add(offer);
        Peer peer = peer(player);
        peer.allowedUploads.addAll(missing);
        byte[] need = new byte[missing.size() * SharedImage.HASH_BYTES];
        int at = 0;
        for (String hex : missing) {
            System.arraycopy(SharedImage.unhex(hex), 0, need, at, SharedImage.HASH_BYTES);
            at += SharedImage.HASH_BYTES;
        }
        PacketDistributor.sendToPlayer(player, new PacketHashes.Need(need));
    }

    public static void upload(ServerPlayer player, byte[] hash, int total, int offset, byte[] data) {
        server = player.server;
        String hex = SharedImage.hex(hash);
        Peer peer = PEERS.get(player.getUUID());
        // Принимаем только то, что сами попросили: иначе любой клиент мог бы забить диск сервера.
        if (peer == null || !peer.allowedUploads.contains(hex)) return;
        Partial p = peer.uploads.get(hex);
        if (p == null) {
            if (offset != 0 || total <= 0 || total > SharedImage.MAX_BYTES
                || peer.uploads.size() >= MAX_PARTIAL_UPLOADS) {
                return;
            }
            p = new Partial(total);
            peer.uploads.put(hex, p);
        }
        if (total != p.data.length || offset != p.received || offset + data.length > total) {
            peer.uploads.remove(hex);
            return;
        }
        System.arraycopy(data, 0, p.data, offset, data.length);
        p.received += data.length;
        if (p.received < total) return;

        peer.uploads.remove(hex);
        peer.allowedUploads.remove(hex);
        if (!Arrays.equals(SharedImage.sha1(p.data), hash)) {
            LOG.warn("Картинка от {} повреждена при передаче ({})", player.getName().getString(), hex);
            return;
        }
        if (store(hex, p.data)) checkOffers();
    }

    /** Применяет предложения, для которых уже есть все картинки, и выбрасывает устаревшие. */
    private static void checkOffers() {
        long now = System.currentTimeMillis();
        for (Iterator<Offer> it = OFFERS.iterator(); it.hasNext();) {
            Offer offer = it.next();
            if (now - offer.created > OFFER_TIMEOUT_MS) {
                it.remove();
            } else if (offer.isComplete()) {
                it.remove();
                apply(offer);
            }
        }
    }

    private static void apply(Offer offer) {
        EntityPicture picture = server != null ? pictureById(server.getLevel(offer.dim), offer.entityId) : null;
        // Пока картинки грузились, картину могли сломать или перенастроить на другой источник.
        if (picture == null || !offer.origin.equals(picture.frame.getOriginKey())) return;
        if (Arrays.equals(picture.frame.files, offer.hashes)) return;
        picture.frame.files = offer.hashes;
        picture.settingsChanged();
    }

    // ---------------------------------------------------------------------
    // Скачивание

    public static void request(ServerPlayer player, byte[] hashes) {
        server = player.server;
        Peer peer = peer(player);
        for (int i = 0; i < hashes.length; i += SharedImage.HASH_BYTES) {
            if (peer.queued.size() >= MAX_QUEUED_DOWNLOADS) break;
            String hex = SharedImage.hex(hashes, i, SharedImage.HASH_BYTES);
            if (peer.queued.add(hex)) peer.downloads.add(hex);
        }
    }

    private static void sendDownloads() {
        int budget = CHUNKS_PER_TICK;
        for (Peer peer : PEERS.values()) {
            for (int i = 0; i < CHUNKS_PER_PLAYER_TICK && budget > 0; i++) {
                if (peer.current == null) {
                    String hex = peer.downloads.poll();
                    if (hex == null) break;
                    peer.current = new Transfer(hex, read(hex));
                }
                Transfer t = peer.current;
                budget--;
                if (t.data == null) {
                    PacketDistributor.sendToPlayer(peer.player, new PacketFileChunk(t.hash, -1, 0, new byte[0]));
                    finish(peer);
                    continue;
                }
                int len = Math.min(CHUNK, t.data.length - t.offset);
                PacketDistributor.sendToPlayer(peer.player, new PacketFileChunk(t.hash, t.data.length, t.offset,
                    Arrays.copyOfRange(t.data, t.offset, t.offset + len)));
                t.offset += len;
                if (t.offset >= t.data.length) finish(peer);
            }
        }
    }

    private static void finish(Peer peer) {
        peer.queued.remove(peer.current.hex);
        peer.current = null;
    }

    // ---------------------------------------------------------------------
    // Хранилище

    private static Path dir() {
        if (dir == null) {
            if (server == null) return null;
            dir = server.getWorldPath(LevelResource.ROOT).resolve(MinePapers.MODID);
        }
        return dir;
    }

    private static boolean has(String hex) {
        if (PRESENT.contains(hex)) return true;
        Path d = dir();
        if (d != null && Files.isRegularFile(d.resolve(hex))) {
            PRESENT.add(hex);
            return true;
        }
        return false;
    }

    private static byte[] read(String hex) {
        byte[] cached = MEMORY.get(hex);
        if (cached != null) return cached;
        Path d = dir();
        if (d == null) return null;
        Path f = d.resolve(hex);
        try {
            if (!Files.isRegularFile(f) || Files.size(f) > SharedImage.MAX_BYTES) return null;
            byte[] data = Files.readAllBytes(f);
            remember(hex, data);
            return data;
        } catch (IOException e) {
            LOG.warn("Не удалось прочитать картинку " + f, e);
            return null;
        }
    }

    private static boolean store(String hex, byte[] data) {
        Path d = dir();
        if (d == null) return false;
        Path target = d.resolve(hex);
        Path tmp = d.resolve(hex + ".tmp");
        try {
            Files.createDirectories(d);
            Files.write(tmp, data);
            if (!Files.exists(target)) Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("Не удалось сохранить картинку " + target, e);
            return false;
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {}
        }
        PRESENT.add(hex);
        remember(hex, data);
        return true;
    }

    private static void remember(String hex, byte[] data) {
        if (data.length > MEMORY_CACHE_BYTES / 4) return;
        if (MEMORY.put(hex, data) == null) memoryBytes += data.length;
        for (Iterator<byte[]> it = MEMORY.values().iterator(); memoryBytes > MEMORY_CACHE_BYTES && it.hasNext();) {
            memoryBytes -= it.next().length;
            it.remove();
        }
    }

    // ---------------------------------------------------------------------

    private static Peer peer(ServerPlayer player) {
        Peer peer = PEERS.computeIfAbsent(player.getUUID(), k -> new Peer());
        // После возрождения у игрока новый объект — шлём через актуальный.
        peer.player = player;
        return peer;
    }

    public static final class Events {
        @SubscribeEvent
        public void onServerTick(ServerTickEvent.Pre event) {
            server = event.getServer();
            if (!PEERS.isEmpty()) sendDownloads();
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            PEERS.remove(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            reset();
        }
    }

    private static final class Peer {
        ServerPlayer player;
        final Set<String> allowedUploads = new HashSet<String>();
        final Map<String, Partial> uploads = new HashMap<String, Partial>();
        final Queue<String> downloads = new ArrayDeque<String>();
        final Set<String> queued = new HashSet<String>();
        Transfer current;
    }

    private static final class Partial {
        final byte[] data;
        int received;

        Partial(int total) {
            data = new byte[total];
        }
    }

    private static final class Transfer {
        final String hex;
        final byte[] hash;
        final byte[] data;
        int offset;

        Transfer(String hex, byte[] data) {
            this.hex = hex;
            this.hash = SharedImage.unhex(hex);
            this.data = data;
        }
    }

    private static final class Offer {
        final ResourceKey<Level> dim;
        final int entityId;
        final String origin;
        final byte[] hashes;
        final long created = System.currentTimeMillis();

        Offer(ResourceKey<Level> dim, int entityId, String origin, byte[] hashes) {
            this.dim = dim;
            this.entityId = entityId;
            this.origin = origin;
            this.hashes = hashes;
        }

        boolean isFor(ResourceKey<Level> dim, int entityId) {
            return this.dim == dim && this.entityId == entityId;
        }

        boolean isComplete() {
            for (int i = 0; i < hashes.length; i += SharedImage.HASH_BYTES) {
                if (!has(SharedImage.hex(hashes, i, SharedImage.HASH_BYTES))) return false;
            }
            return true;
        }
    }
}
