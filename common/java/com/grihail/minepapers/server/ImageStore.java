package com.grihail.minepapers.server;

import java.io.File;
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.grihail.minepapers.network.SharedImage;
import com.grihail.minepapers.picture.PictureSettings;

/** Картинки с компьютеров игроков, выложенные на сервер (папка minepapers в папке мира): приём, хранение и раздача по SHA-1. Права игрока проверяет версия (ServerImages) до вызова; мир, картины и пакеты — через ServerHost. Все методы вызываются из серверного потока. */
public final class ImageStore {

    private static final Logger LOG = LogManager.getLogger("minepapers");

    /** Скачивание: куски по 32 КБ, не больше 3 за тик на игрока (~1.9 МБ/с) и 24 за тик на всех — чтобы не забить канал. */
    private static final int CHUNKS_PER_PLAYER_TICK = 3;
    private static final int CHUNKS_PER_TICK = 24;
    private static final long OFFER_TIMEOUT_MS = 10 * 60_000L;
    private static final int MAX_PARTIAL_UPLOADS = 4;
    private static final int MAX_QUEUED_DOWNLOADS = 4096;
    /** Недавно отданные файлы держим в памяти: картинку обычно скачивают все игроки рядом с ней. */
    private static final long MEMORY_CACHE_BYTES = 32L * 1024 * 1024;

    private static ServerHost host;
    private static final List<Offer> OFFERS = new ArrayList<Offer>();
    private static final Map<UUID, Peer> PEERS = new HashMap<UUID, Peer>();
    private static final Set<String> PRESENT = new HashSet<String>();
    private static final LinkedHashMap<String, byte[]> MEMORY = new LinkedHashMap<String, byte[]>(16, 0.75f, true);
    private static long memoryBytes;
    private static Path dir;

    private ImageStore() {}

    /** Вызывается версией при запуске мода. */
    public static void install(ServerHost serverHost) {
        host = serverHost;
    }

    /** Мир выгружен — забываем всё, что относилось к нему. */
    public static void reset() {
        OFFERS.clear();
        PEERS.clear();
        PRESENT.clear();
        MEMORY.clear();
        memoryBytes = 0;
        dir = null;
    }

    /** Игрок вышел. */
    public static void logout(UUID player) {
        PEERS.remove(player);
    }

    // --------------------------------------------------------------------- Выкладывание

    /** Игрок предлагает картинки для рамки entityId; у кого их ещё нет на сервере — просим прислать. Права игрока менять картину версия уже проверила. @param player объект игрока для отправки пакетов */
    public static void offer(UUID playerId, Object player, Object world, int entityId, String origin, byte[] hashes) {
        PictureSettings frame = host.frame(world, entityId);
        if (frame == null || hashes.length == 0 || hashes.length % SharedImage.HASH_BYTES != 0
            || hashes.length > PictureSettings.MAX_FILES * SharedImage.HASH_BYTES
            || !origin.equals(frame.getOriginKey()) || frame.isNetworkSource()) {
            return;
        }
        for (Iterator<Offer> it = OFFERS.iterator(); it.hasNext();) {
            if (it.next().isFor(world, entityId)) it.remove();
        }
        Offer offer = new Offer(world, entityId, origin, hashes);
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
        Peer peer = peer(playerId, player);
        peer.allowedUploads.addAll(missing);
        byte[] need = new byte[missing.size() * SharedImage.HASH_BYTES];
        int at = 0;
        for (String hex : missing) {
            System.arraycopy(SharedImage.unhex(hex), 0, need, at, SharedImage.HASH_BYTES);
            at += SharedImage.HASH_BYTES;
        }
        host.sendNeed(player, need);
    }

    /** Кусок картинки от игрока. @param playerName для журнала */
    public static void upload(UUID playerId, String playerName, byte[] hash, int total, int offset, byte[] data) {
        String hex = SharedImage.hex(hash);
        Peer peer = PEERS.get(playerId);
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
            LOG.warn("Картинка от {} повреждена при передаче ({})", playerName, hex);
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
        PictureSettings frame = host.frame(offer.world, offer.entityId);
        // Пока картинки грузились, картину могли сломать или перенастроить на другой источник.
        if (frame == null || !offer.origin.equals(frame.getOriginKey())) return;
        if (Arrays.equals(frame.files, offer.hashes)) return;
        frame.files = offer.hashes;
        host.settingsChanged(offer.world, offer.entityId);
    }

    // --------------------------------------------------------------------- Скачивание

    /** Игрок просит картинки. */
    public static void request(UUID playerId, Object player, byte[] hashes) {
        Peer peer = peer(playerId, player);
        for (int i = 0; i + SharedImage.HASH_BYTES <= hashes.length; i += SharedImage.HASH_BYTES) {
            if (peer.queued.size() >= MAX_QUEUED_DOWNLOADS) break;
            String hex = SharedImage.hex(hashes, i, SharedImage.HASH_BYTES);
            if (peer.queued.add(hex)) peer.downloads.add(hex);
        }
    }

    /** Каждый серверный тик: раздаёт куски картинок. */
    public static void tick() {
        if (PEERS.isEmpty()) return;
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
                    host.sendChunk(peer.player, t.hash, -1, 0, new byte[0]);
                    finish(peer);
                    continue;
                }
                int len = Math.min(SharedImage.DOWNLOAD_CHUNK, t.data.length - t.offset);
                host.sendChunk(peer.player, t.hash, t.data.length, t.offset,
                    Arrays.copyOfRange(t.data, t.offset, t.offset + len));
                t.offset += len;
                if (t.offset >= t.data.length) finish(peer);
            }
        }
    }

    private static void finish(Peer peer) {
        peer.queued.remove(peer.current.hex);
        peer.current = null;
    }

    // --------------------------------------------------------------------- Хранилище

    private static Path dir() {
        if (dir == null) {
            File world = host.worldDir();
            if (world == null) return null;
            dir = new File(world, "minepapers").toPath();
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

    private static Peer peer(UUID playerId, Object player) {
        Peer peer = PEERS.get(playerId);
        if (peer == null) {
            peer = new Peer();
            PEERS.put(playerId, peer);
        }
        // После возрождения у игрока новый объект — шлём через актуальный.
        peer.player = player;
        return peer;
    }

    private static final class Peer {
        Object player;
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
        final Object world;
        final int entityId;
        final String origin;
        final byte[] hashes;
        final long created = System.currentTimeMillis();

        Offer(Object world, int entityId, String origin, byte[] hashes) {
            this.world = world;
            this.entityId = entityId;
            this.origin = origin;
            this.hashes = hashes;
        }

        boolean isFor(Object world, int entityId) {
            return this.world.equals(world) && this.entityId == entityId;
        }

        boolean isComplete() {
            for (int i = 0; i < hashes.length; i += SharedImage.HASH_BYTES) {
                if (!has(SharedImage.hex(hashes, i, SharedImage.HASH_BYTES))) return false;
            }
            return true;
        }
    }
}
