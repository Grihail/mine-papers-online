package com.grihail.minepapers.server;

import java.io.File;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.PictureSettings;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/** Права игроков на картины и связь общего хранилища картинок (ImageStore) с миром 1.7.10. Всё, кроме приёма пакетов, работает в серверном потоке: обработчики пакетов только ставят задачи в очередь (schedule). Мир для ImageStore — номер измерения (у картины-предмета — UUID игрока), игрок — EntityPlayerMP. */
public final class ServerImages extends ServerHost {

    private static final Logger LOG = LogManager.getLogger(MinePapers.MODID);

    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<Runnable>();

    private ServerImages() {}

    public static void install() {
        ImageStore.install(new ServerImages());
    }

    /** Выполнить в серверном потоке (из обработчика пакета). */
    public static void schedule(Runnable task) {
        TASKS.add(task);
    }

    /** Мир выгружен — забываем всё, что относилось к нему. */
    public static void reset() {
        TASKS.clear();
        ImageStore.reset();
    }

    /** Настройки открывают кликом по любой части картины (см. PictureSettings.editReach). */
    public static boolean canEdit(EntityPlayerMP player, EntityPicture picture) {
        double reach = picture.frame.editReach();
        return player.getDistanceSqToEntity(picture) <= reach * reach;
    }

    /** Менять настройки, материалы и картинки: рядом с картиной и она не запривачена другим игроком. Открыть окно (только заголовок и описание) и сломать картину может любой рядом — см. canEdit. */
    public static boolean canChange(EntityPlayerMP player, EntityPicture picture) {
        return canEdit(player, picture) && picture.frame.mayEdit(player.getUniqueID());
    }

    /** Картина-предмет в инвентаре игрока всегда под рукой; у сущности — как canEdit выше. */
    public static boolean canEdit(EntityPlayerMP player, FrameHolder holder) {
        return holder.picture != null ? canEdit(player, holder.picture) : holder.exists();
    }

    public static boolean canChange(EntityPlayerMP player, FrameHolder holder) {
        return canEdit(player, holder) && holder.frame.mayEdit(player.getUniqueID());
    }

    /** Картина target (PictureTarget), которую игрок может менять, или null. */
    public static FrameHolder changeable(EntityPlayerMP player, int target) {
        FrameHolder holder = FrameHolder.of(player, target);
        return holder != null && canChange(player, holder) ? holder : null;
    }

    public static EntityPicture pictureById(World world, int entityId) {
        Entity entity = world != null ? world.getEntityByID(entityId) : null;
        return entity instanceof EntityPicture && !entity.isDead ? (EntityPicture) entity : null;
    }

    // --------------------------------------------------------------------- Пакеты (в серверном потоке)

    public static void offer(EntityPlayerMP player, int target, String origin, byte[] hashes) {
        FrameHolder holder = changeable(player, target);
        if (holder == null) return;
        Object world = holder.isItem() ? player.getUniqueID() : (Object) player.dimension;
        ImageStore.offer(player.getUniqueID(), player, world, target, origin, hashes);
    }

    public static void upload(EntityPlayerMP player, byte[] hash, int total, int offset, byte[] data) {
        ImageStore.upload(player.getUniqueID(), player.getCommandSenderName(), hash, total, offset, data);
    }

    public static void request(EntityPlayerMP player, byte[] hashes) {
        ImageStore.request(player.getUniqueID(), player, hashes);
    }

    // --------------------------------------------------------------------- ServerHost

    @Override
    public File worldDir() {
        return DimensionManager.getCurrentSaveRootDirectory();
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

    /** Мир картины-сущности — номер измерения, картины-предмета — UUID игрока, у которого она лежит. */
    private static FrameHolder holder(Object world, int target) {
        if (world instanceof UUID) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return null;
            for (Object o : server.getConfigurationManager().playerEntityList) {
                EntityPlayerMP player = (EntityPlayerMP) o;
                if (player.getUniqueID().equals(world)) return FrameHolder.of(player, target);
            }
            return null;
        }
        EntityPicture picture = pictureById(DimensionManager.getWorld((Integer) world), target);
        return picture != null ? FrameHolder.of(picture, null) : null;
    }

    @Override
    public void sendNeed(Object player, byte[] hashes) {
        MinePapers.network.sendTo(new PacketHashes.Need(hashes), (EntityPlayerMP) player);
    }

    @Override
    public void sendChunk(Object player, byte[] hash, int total, int offset, byte[] data) {
        MinePapers.network.sendTo(new PacketFileChunk(hash, total, offset, data, 0, data.length), (EntityPlayerMP) player);
    }

    public static final class Events {
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            Runnable task;
            while ((task = TASKS.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    LOG.warn("Ошибка при обработке пакета картинок", t);
                }
            }
            ImageStore.tick();
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            ImageStore.logout(event.player.getUniqueID());
        }
    }
}
