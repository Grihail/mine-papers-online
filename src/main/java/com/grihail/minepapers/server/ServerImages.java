package com.grihail.minepapers.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
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
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.network.SharedImage;
import com.grihail.minepapers.tile.FrameGeometry;
import com.grihail.minepapers.tile.TilePictureFrame;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * Картинки с компьютеров игроков, выложенные на сервер (папка minepapers в папке мира).
 * Всё, кроме приёма пакетов, работает в серверном потоке: обработчики пакетов только ставят задачи в очередь.
 */
public final class ServerImages {

    private static final Logger LOG = LogManager.getLogger(MinePapers.MODID);

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

    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<Runnable>();
    private static final List<Offer> OFFERS = new ArrayList<Offer>();
    private static final Map<UUID, Peer> PEERS = new HashMap<UUID, Peer>();
    private static final Set<String> PRESENT = new HashSet<String>();
    private static final LinkedHashMap<String, byte[]> MEMORY = new LinkedHashMap<String, byte[]>(16, 0.75f, true);
    private static long memoryBytes;
    private static File dir;

    private ServerImages() {}

    /** Выполнить в серверном потоке (из обработчика пакета). */
    public static void schedule(Runnable task) {
        TASKS.add(task);
    }

    /** Мир выгружен — забываем всё, что относилось к нему. */
    public static void reset() {
        TASKS.clear();
        OFFERS.clear();
        PEERS.clear();
        PRESENT.clear();
        MEMORY.clear();
        memoryBytes = 0;
        dir = null;
    }

    /** Настройки открывают кликом по любой части картины — проверяем расстояние до холста, а не до рамки. */
    public static boolean canEdit(EntityPlayerMP player, TilePictureFrame frame) {
        AxisAlignedBB box = FrameGeometry.canvasBox(frame);
        double dx = Math.max(Math.max(box.minX - player.posX, player.posX - box.maxX), 0);
        double dy = Math.max(Math.max(box.minY - player.posY, player.posY - box.maxY), 0);
        double dz = Math.max(Math.max(box.minZ - player.posZ, player.posZ - box.maxZ), 0);
        return dx * dx + dy * dy + dz * dz <= EDIT_DISTANCE * EDIT_DISTANCE;
    }

    public static TilePictureFrame frameAt(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) return null;
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TilePictureFrame ? (TilePictureFrame) te : null;
    }

    // ---------------------------------------------------------------------
    // Выкладывание

    public static void offer(EntityPlayerMP player, int x, int y, int z, String origin, byte[] hashes) {
        TilePictureFrame frame = frameAt(player.worldObj, x, y, z);
        if (frame == null || hashes.length == 0 || hashes.length > TilePictureFrame.MAX_FILES * SharedImage.HASH_BYTES
            || !origin.equals(frame.getOriginKey()) || frame.source == TilePictureFrame.SOURCE_YANDEX
            || !canEdit(player, frame)) {
            return;
        }
        for (Iterator<Offer> it = OFFERS.iterator(); it.hasNext();) {
            if (it.next().isAt(player.dimension, x, y, z)) it.remove();
        }
        Offer offer = new Offer(player.dimension, x, y, z, origin, hashes);
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
        MinePapers.network.sendTo(new PacketHashes.Need(need), player);
    }

    public static void upload(EntityPlayerMP player, byte[] hash, int total, int offset, byte[] data) {
        String hex = SharedImage.hex(hash);
        Peer peer = PEERS.get(player.getUniqueID());
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
            LOG.warn("Картинка от {} повреждена при передаче ({})", player.getCommandSenderName(), hex);
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
        World world = DimensionManager.getWorld(offer.dim);
        TilePictureFrame frame = frameAt(world, offer.x, offer.y, offer.z);
        // Пока картинки грузились, рамку могли перенастроить на другой источник.
        if (frame == null || !offer.origin.equals(frame.getOriginKey())) return;
        if (Arrays.equals(frame.files, offer.hashes)) return;
        frame.files = offer.hashes;
        frame.markDirty();
        world.markBlockForUpdate(offer.x, offer.y, offer.z);
    }

    // ---------------------------------------------------------------------
    // Скачивание

    public static void request(EntityPlayerMP player, byte[] hashes) {
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
                    MinePapers.network.sendTo(new PacketFileChunk(t.hash, -1, 0, new byte[0], 0, 0), peer.player);
                    finish(peer);
                    continue;
                }
                int len = Math.min(CHUNK, t.data.length - t.offset);
                MinePapers.network.sendTo(new PacketFileChunk(t.hash, t.data.length, t.offset, t.data, t.offset, len),
                    peer.player);
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

    private static File dir() {
        if (dir == null) {
            File root = DimensionManager.getCurrentSaveRootDirectory();
            if (root == null) return null;
            dir = new File(root, MinePapers.MODID);
        }
        return dir;
    }

    private static boolean has(String hex) {
        if (PRESENT.contains(hex)) return true;
        File d = dir();
        if (d != null && new File(d, hex).isFile()) {
            PRESENT.add(hex);
            return true;
        }
        return false;
    }

    private static byte[] read(String hex) {
        byte[] cached = MEMORY.get(hex);
        if (cached != null) return cached;
        File d = dir();
        if (d == null) return null;
        File f = new File(d, hex);
        if (!f.isFile() || f.length() > SharedImage.MAX_BYTES) return null;
        try {
            byte[] data = Files.readAllBytes(f.toPath());
            remember(hex, data);
            return data;
        } catch (IOException e) {
            LOG.warn("Не удалось прочитать картинку " + f, e);
            return null;
        }
    }

    private static boolean store(String hex, byte[] data) {
        File d = dir();
        if (d == null) return false;
        File target = new File(d, hex);
        File tmp = new File(d, hex + ".tmp");
        try {
            if (!d.isDirectory() && !d.mkdirs()) throw new IOException("Cannot create " + d);
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(data);
            } finally {
                out.close();
            }
            if (!target.exists() && !tmp.renameTo(target)) throw new IOException("Cannot rename " + tmp);
        } catch (IOException e) {
            LOG.warn("Не удалось сохранить картинку " + target, e);
            return false;
        } finally {
            tmp.delete();
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

    private static Peer peer(EntityPlayerMP player) {
        Peer peer = PEERS.get(player.getUniqueID());
        if (peer == null) {
            peer = new Peer();
            PEERS.put(player.getUniqueID(), peer);
        }
        // После возрождения у игрока новый объект — шлём через актуальный.
        peer.player = player;
        return peer;
    }

    private static void tick() {
        Runnable task;
        while ((task = TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                LOG.warn("Ошибка при обработке пакета картинок", t);
            }
        }
        if (!PEERS.isEmpty()) sendDownloads();
    }

    public static final class Events {
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.START) tick();
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            PEERS.remove(event.player.getUniqueID());
        }
    }

    private static final class Peer {
        EntityPlayerMP player;
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
        final int dim, x, y, z;
        final String origin;
        final byte[] hashes;
        final long created = System.currentTimeMillis();

        Offer(int dim, int x, int y, int z, String origin, byte[] hashes) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.origin = origin;
            this.hashes = hashes;
        }

        boolean isAt(int dim, int x, int y, int z) {
            return this.dim == dim && this.x == x && this.y == y && this.z == z;
        }

        boolean isComplete() {
            for (int i = 0; i < hashes.length; i += SharedImage.HASH_BYTES) {
                if (!has(SharedImage.hex(hashes, i, SharedImage.HASH_BYTES))) return false;
            }
            return true;
        }
    }
}
