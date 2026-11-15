package com.grihail.minepapers.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketReload;
import com.grihail.minepapers.picture.EntityPicture;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

/** Перезагрузка картинок картин с сервера (команда /minepapers reload, Shift+клик по "Обновить"). Ссылку и онлайн-папку каждый игрок скачивает сам — выбранным игрокам уходит "загрузи источник заново". Картинки с компьютера (config/minepapers, папка) есть только у того, кто их выложил: его клиент перечитывает файлы и выкладывает заново, остальные получают новые картинки с сервера сами. Только в серверном потоке. */
public final class PictureReload {

    /** Столько времени игроку, которого попросили выложить картинки заново, можно выкладывать их для этой картины откуда угодно (подготовка сотен файлов бывает долгой). */
    private static final long GRANT_MS = 5 * 60 * 1000L;
    /** "UUID игрока|id картины" -> {измерение картины, до какого времени}. */
    private static final Map<String, long[]> GRANTS = new HashMap<String, long[]>();

    private PictureReload() {}

    public static void reset() {
        GRANTS.clear();
    }

    /** Измерение картины entity, если сервер сам попросил игрока выложить её картинки заново (и время не вышло), иначе null. */
    static Integer grantedDimension(EntityPlayerMP player, int entity) {
        long now = System.currentTimeMillis();
        for (Iterator<long[]> it = GRANTS.values().iterator(); it.hasNext();) {
            if (it.next()[1] < now) it.remove();
        }
        long[] grant = GRANTS.get(player.getUniqueID() + "|" + entity);
        return grant != null ? Integer.valueOf((int) grant[0]) : null;
    }

    private static MinecraftServer server() {
        return FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    /** Все картины во всех загруженных мирах. */
    public static List<EntityPicture> allPictures() {
        List<EntityPicture> found = new ArrayList<EntityPicture>();
        MinecraftServer server = server();
        if (server == null) return found;
        for (WorldServer world : server.worlds) found.addAll(pictures(world));
        return found;
    }

    public static List<EntityPicture> pictures(World world) {
        List<EntityPicture> found = new ArrayList<EntityPicture>();
        for (Entity e : world.loadedEntityList) {
            if (e instanceof EntityPicture && !e.isDead) found.add((EntityPicture) e);
        }
        return found;
    }

    public static List<EntityPlayerMP> allPlayers() {
        MinecraftServer server = server();
        return server != null ? new ArrayList<EntityPlayerMP>(server.getPlayerList().getPlayers()) : new ArrayList<EntityPlayerMP>();
    }

    private static EntityPlayerMP online(UUID id) {
        MinecraftServer server = server();
        return server != null ? server.getPlayerList().getPlayerByUUID(id) : null;
    }

    /** Хозяин одиночной игры или открытого для сети мира (null — выделенный сервер). */
    private static EntityPlayerMP singleplayerHost() {
        MinecraftServer server = server();
        if (server == null || server.isDedicatedServer() || server.getServerOwner() == null) return null;
        return server.getPlayerList().getPlayerByUsername(server.getServerOwner());
    }

    /** Что получилось: сколько картин обновляется, чьих картинок обновить некому (игрок не в сети) и у каких картин неизвестно, кто их выложил. */
    public static final class Result {
        public int pictures;
        public final Set<String> offline = new LinkedHashSet<String>();
        public final List<EntityPicture> unknown = new ArrayList<EntityPicture>();
    }

    /** Перезагрузить картины pictures у игроков to (картинки с компьютера — у всех, см. описание класса). */
    public static Result reload(Collection<EntityPicture> pictures, Collection<EntityPlayerMP> to) {
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
            EntityPlayerMP uploader = who != null ? online(who) : singleplayerHost();
            if (uploader == null) {
                if (who == null) {
                    result.unknown.add(picture);
                } else {
                    result.offline.add(name.isEmpty() ? who.toString() : name);
                }
                continue;
            }
            GRANTS.put(uploader.getUniqueID() + "|" + picture.getEntityId(), new long[] { picture.world.provider.getDimension(), until });
            MinePapers.network.sendTo(new PacketReload(origin, picture.getEntityId()), uploader);
        }
        for (EntityPlayerMP player : to) {
            for (String key : keys) MinePapers.network.sendTo(new PacketReload(key, -1), player);
        }
        return result;
    }

    /** Итог — тому, кто перезагружал (в чат или в вывод командного блока). */
    public static void report(ICommandSender sender, Result result) {
        sender.sendMessage(new TextComponentTranslation("minepapers.command.reload.done", result.pictures));
        for (String name : result.offline) {
            sender.sendMessage(colored(new TextComponentTranslation("minepapers.command.reload.offline", name), TextFormatting.YELLOW));
        }
        for (EntityPicture p : result.unknown) {
            sender.sendMessage(colored(new TextComponentTranslation("minepapers.command.reload.unknown", p.frame.x, p.frame.y, p.frame.z),
                TextFormatting.YELLOW));
        }
    }

    private static ITextComponent colored(ITextComponent text, TextFormatting color) {
        text.getStyle().setColor(color);
        return text;
    }

    /** Shift+клик по "Обновить" в окне картины entity: у всех игроков. Можно тому, кто может менять картину, и оператору. */
    public static void fromWindow(EntityPlayerMP player, int entity) {
        EntityPicture picture = ServerImages.pictureById(player.world, entity);
        if (picture == null) return;
        if (!ServerImages.canChange(player, picture) && !player.canUseCommand(2, CommandMinePapers.NAME)) return;
        List<EntityPicture> one = new ArrayList<EntityPicture>();
        one.add(picture);
        report(player, reload(one, allPlayers()));
    }
}
