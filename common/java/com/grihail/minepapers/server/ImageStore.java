package com.grihail.minepapers.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    private static ServerHost host;
    private static final List<Offer> OFFERS = new ArrayList<Offer>();
    private static final Map<UUID, Peer> PEERS = new HashMap<UUID, Peer>();
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
        dir = null;
    }

    /** Игрок вышел. */
    public static void logout(UUID player) {
        PEERS.remove(player);
    }

    // --------------------------------------------------------------------- Выкладывание

    /** Игрок предлагает картинки для картины target (PictureTarget); у кого их ещё нет на сервере — просим прислать. Права игрока менять картину версия уже проверила. @param player объект игрока для отправки пакетов @param world мир картины-сущности или UUID владельца картины-предмета (см. ServerHost) */
    public static void offer(UUID playerId, Object player, Object world, int target, String origin, byte[] hashes) {
        PictureSettings frame = host.frame(world, target);
        if (frame == null || !origin.equals(frame.getOriginKey())) return;
        for (Iterator<Offer> it = OFFERS.iterator(); it.hasNext();) {
            if (it.next().isFor(world, target)) it.remove();
        }
        Offer offer = new Offer(world, target, origin, hashes);
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
            if (total > SharedImage.MAX_BYTES) return;
            p = new Partial(total);
            peer.uploads.put(hex, p);
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

    /** Применяет предложения, для которых уже есть все картинки. */
    private static void checkOffers() {
        for (Iterator<Offer> it = OFFERS.iterator(); it.hasNext();) {
            Offer offer = it.next();
            if (offer.isComplete()) {
                it.remove();
                apply(offer);
            }
        }
    }

    private static void apply(Offer offer) {
        PictureSettings frame = host.frame(offer.world, offer.target);
        // Пока картинки грузились, картину могли сломать (убрать предмет) или перенастроить на другой источник.
        if (frame == null || !offer.origin.equals(frame.getOriginKey())) return;
        host.shareFiles(offer.world, offer.target, offer.hashes);
    }

    // --------------------------------------------------------------------- Скачивание

    /** Игрок просит картинки. */
    public static void request(UUID playerId, Object player, byte[] hashes) {
        Peer peer = peer(playerId, player);
        for (int i = 0; i < hashes.length; i += SharedImage.HASH_BYTES) {
            peer.downloads.add(SharedImage.hex(hashes, i, SharedImage.HASH_BYTES));
        }
    }

    /** Каждый серверный тик: раздаёт куски картинок. */
    public static void tick() {
        if (PEERS.isEmpty()) return;
        int budget = CHUNKS_PER_TICK;
        for (Peer peer : PEERS.values()) {
            for (int i = 0; i < CHUNKS_PER_PLAYER_TICK && budget > 0; i++) {
                if (peer.current == null) {
                    Iterator<String> next = peer.downloads.iterator();
                    if (!next.hasNext()) break;
                    String hex = next.next();
                    next.remove();
                    peer.current = new Transfer(hex, read(hex));
                }
                Transfer t = peer.current;
                budget--;
                if (t.data == null) {
                    host.sendChunk(peer.player, t.hash, -1, 0, new byte[0]);
                    peer.current = null;
                    continue;
                }
                int len = Math.min(SharedImage.DOWNLOAD_CHUNK, t.data.length - t.offset);
                host.sendChunk(peer.player, t.hash, t.data.length, t.offset,
                    Arrays.copyOfRange(t.data, t.offset, t.offset + len));
                t.offset += len;
                if (t.offset >= t.data.length) peer.current = null;
            }
        }
    }

    // --------------------------------------------------------------------- Хранилище

    private static Path dir() {
        if (dir == null) {
            dir = new File(host.worldDir(), "minepapers").toPath();
        }
        return dir;
    }

    private static boolean has(String hex) {
        return Files.isRegularFile(dir().resolve(hex));
    }

    /** null — такой картинки нет. */
    private static byte[] read(String hex) {
        try {
            return Files.readAllBytes(dir().resolve(hex));
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean store(String hex, byte[] data) {
        try {
            Files.createDirectories(dir());
            Files.write(dir().resolve(hex), data);
            return true;
        } catch (IOException e) {
            LOG.warn("Не удалось сохранить картинку " + hex, e);
            return false;
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
        /** Очередь скачивания без повторов. */
        final Set<String> downloads = new LinkedHashSet<String>();
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
        final byte[] hash;
        final byte[] data;
        int offset;

        Transfer(String hex, byte[] data) {
            this.hash = SharedImage.unhex(hex);
            this.data = data;
        }
    }

    private static final class Offer {
        final Object world;
        final int target;
        final String origin;
        final byte[] hashes;

        Offer(Object world, int target, String origin, byte[] hashes) {
            this.world = world;
            this.target = target;
            this.origin = origin;
            this.hashes = hashes;
        }

        boolean isFor(Object world, int target) {
            return this.world.equals(world) && this.target == target;
        }

        boolean isComplete() {
            for (int i = 0; i < hashes.length; i += SharedImage.HASH_BYTES) {
                if (!has(SharedImage.hex(hashes, i, SharedImage.HASH_BYTES))) return false;
            }
            return true;
        }
    }
}
