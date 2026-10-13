package com.grihail.minepapers.server;

import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureSettings;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/** Права игроков на картины и связь общего хранилища картинок (ImageStore) с миром 1.7.10. Всё, кроме приёма пакетов, работает в серверном потоке: обработчики пакетов только ставят задачи в очередь (schedule). Мир для ImageStore — номер измерения, игрок — EntityPlayerMP. */
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

    public static EntityPicture pictureById(World world, int entityId) {
        Entity entity = world != null ? world.getEntityByID(entityId) : null;
        return entity instanceof EntityPicture && !entity.isDead ? (EntityPicture) entity : null;
    }

    // --------------------------------------------------------------------- Пакеты (в серверном потоке)

    public static void offer(EntityPlayerMP player, int entityId, String origin, byte[] hashes) {
        EntityPicture picture = pictureById(player.worldObj, entityId);
        if (picture == null || !canChange(player, picture)) return;
        ImageStore.offer(player.getUniqueID(), player, player.dimension, entityId, origin, hashes);
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
    public PictureSettings frame(Object world, int entityId) {
        EntityPicture picture = picture(world, entityId);
        return picture != null ? picture.frame : null;
    }

    @Override
    public void settingsChanged(Object world, int entityId) {
        EntityPicture picture = picture(world, entityId);
        if (picture != null) picture.settingsChanged();
    }

    private static EntityPicture picture(Object world, int entityId) {
        return pictureById(DimensionManager.getWorld((Integer) world), entityId);
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
