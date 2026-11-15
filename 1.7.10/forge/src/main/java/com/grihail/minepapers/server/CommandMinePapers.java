package com.grihail.minepapers.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerSelector;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * /minepapers reload [что] [to &lt;игроки&gt;] — загрузить картинки картин заново (PictureReload). Что перезагрузить:
 * без слова — картина, на которую смотрит игрок; group &lt;номер&gt; [владелец] — группа пазла (без владельца — среди картин без привата,
 * с ним — среди его заприваченных; номер можно дать и одиночной картине); near &lt;радиус&gt; — картины вокруг игрока или командного блока;
 * at &lt;x&gt; &lt;y&gt; &lt;z&gt; — картина в этой клетке или вплотную к ней (~ — от себя); all — все загруженные картины.
 * to — у кого перезагрузить (ники и селекторы @a, @p, @r); без него — у всех. Оператор (и командный блок) перезагружает любые картины,
 * обычный игрок — только те, что может менять сам, и без all.
 */
public class CommandMinePapers extends CommandBase {

    public static final String NAME = "minepapers";
    private static final String USAGE = "minepapers.command.usage";
    /** Дальше этого картину, на которую смотрит игрок, не ищем. */
    private static final double LOOK_REACH = 64;

    @Override
    public String getCommandName() {
        return NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    /** Игрок может всегда (права на картины проверяются при выборе), остальные — с уровнем оператора, как командный блок. */
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender instanceof EntityPlayerMP || super.canCommandSenderUseCommand(sender);
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) throw new WrongUsageException(USAGE);
        boolean op = sender.canCommandSenderUseCommand(2, NAME);
        int to = toIndex(args);
        String[] what = Arrays.copyOfRange(args, 1, to < 0 ? args.length : to);
        if (to >= 0 && to == args.length - 1) throw new WrongUsageException(USAGE);

        List<EntityPicture> pictures = select(sender, what, op);
        if (!op && sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            List<EntityPicture> mine = new ArrayList<EntityPicture>();
            for (EntityPicture p : pictures) {
                if (p.frame.mayEdit(player.getUniqueID())) mine.add(p);
            }
            pictures = mine;
        }
        if (pictures.isEmpty()) throw new CommandException("minepapers.command.reload.none");

        List<EntityPlayerMP> players = to < 0 ? PictureReload.allPlayers() : players(sender, Arrays.copyOfRange(args, to + 1, args.length));
        PictureReload.report(sender, PictureReload.reload(pictures, players));
    }

    /** Где начинается "to" (после "reload"), или -1. */
    private static int toIndex(String[] args) {
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("to")) return i;
        }
        return -1;
    }

    private List<EntityPicture> select(ICommandSender sender, String[] what, boolean op) {
        if (what.length == 0) {
            if (!(sender instanceof EntityPlayerMP)) throw new WrongUsageException(USAGE);
            EntityPicture looked = lookedAt((EntityPlayerMP) sender);
            return looked != null ? Collections.singletonList(looked) : Collections.<EntityPicture>emptyList();
        }
        String mode = what[0].toLowerCase(java.util.Locale.ROOT);
        List<EntityPicture> found = new ArrayList<EntityPicture>();
        if (mode.equals("group") && (what.length == 2 || what.length == 3)) {
            int group = parseIntWithMin(sender, what[1], 1);
            String owner = what.length == 3 ? what[2] : null;
            for (EntityPicture p : PictureReload.allPictures()) {
                if (p.frame.puzzle != group) continue;
                if (owner == null ? p.frame.owner == null : p.frame.owner != null && p.frame.ownerName.equalsIgnoreCase(owner)) found.add(p);
            }
        } else if (mode.equals("near") && what.length == 2) {
            double r = parseDoubleBounded(sender, what[1], 0, 1024);
            double[] c = center(sender);
            for (EntityPicture p : PictureReload.pictures(sender.getEntityWorld())) {
                if (distanceSq(FrameGeometry.canvasBox(p.frame), c) <= r * r) found.add(p);
            }
        } else if (mode.equals("at") && what.length == 4) {
            double[] c = center(sender);
            int x = MathHelper.floor_double(func_110666_a(sender, c[0], what[1]));
            int y = MathHelper.floor_double(func_110666_a(sender, c[1], what[2]));
            int z = MathHelper.floor_double(func_110666_a(sender, c[2], what[3]));
            // Вплотную тоже: картина висит перед блоком стены, а указать удобно и на сам блок.
            AxisAlignedBB cell = AxisAlignedBB.getBoundingBox(x - 0.01, y - 0.01, z - 0.01, x + 1.01, y + 1.01, z + 1.01);
            for (EntityPicture p : PictureReload.pictures(sender.getEntityWorld())) {
                if (FrameGeometry.canvasBox(p.frame).intersectsWith(cell)) found.add(p);
            }
        } else if (mode.equals("all") && what.length == 1) {
            if (!op) throw new CommandException("commands.generic.permission");
            found.addAll(PictureReload.allPictures());
        } else {
            throw new WrongUsageException(USAGE);
        }
        return found;
    }

    /** Откуда считать радиус и "~": игрок — его ноги, командный блок — центр блока. */
    private static double[] center(ICommandSender sender) {
        if (sender instanceof Entity) {
            Entity e = (Entity) sender;
            return new double[] { e.posX, e.posY, e.posZ };
        }
        ChunkCoordinates c = sender.getPlayerCoordinates();
        return new double[] { c.posX + 0.5, c.posY + 0.5, c.posZ + 0.5 };
    }

    private static double distanceSq(AxisAlignedBB box, double[] p) {
        double dx = Math.max(0, Math.max(box.minX - p[0], p[0] - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - p[1], p[1] - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - p[2], p[2] - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Ближайшая картина на луче взгляда игрока, не дальше первого блока на нём. */
    private static EntityPicture lookedAt(EntityPlayerMP player) {
        World world = player.worldObj;
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLook(1f);
        Vec3 end = eye.addVector(look.xCoord * LOOK_REACH, look.yCoord * LOOK_REACH, look.zCoord * LOOK_REACH);
        MovingObjectPosition block = world.func_147447_a(eye, end, false, true, false);
        // Картина висит перед блоком стены — до него луч доходит позже, чем до неё.
        double max = block != null && block.hitVec != null ? eye.distanceTo(block.hitVec) + 0.2 : LOOK_REACH;
        EntityPicture best = null;
        double bestDist = max;
        for (EntityPicture p : PictureReload.pictures(world)) {
            AxisAlignedBB box = FrameGeometry.canvasBox(p.frame);
            double d;
            if (box.isVecInside(eye)) {
                d = 0;
            } else {
                MovingObjectPosition hit = box.calculateIntercept(eye, end);
                if (hit == null) continue;
                d = eye.distanceTo(hit.hitVec);
            }
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Ники и селекторы (@a, @p, @r) — без повторов. */
    private static List<EntityPlayerMP> players(ICommandSender sender, String[] names) {
        Set<EntityPlayerMP> found = new LinkedHashSet<EntityPlayerMP>();
        for (String name : names) {
            EntityPlayerMP[] matched = PlayerSelector.matchPlayers(sender, name);
            if (matched != null) {
                found.addAll(Arrays.asList(matched));
            } else {
                found.add(getPlayer(sender, name));
            }
        }
        return new ArrayList<EntityPlayerMP>(found);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "reload");
        if (!args[0].equalsIgnoreCase("reload")) return null;
        String[] names = MinecraftServer.getServer().getAllUsernames();
        int to = toIndex(args);
        if (to >= 0 && to < args.length - 1) return getListOfStringsMatchingLastWord(args, names);
        if (args.length == 2) return getListOfStringsMatchingLastWord(args, "group", "near", "at", "all", "to");
        String mode = args[1].toLowerCase(java.util.Locale.ROOT);
        int n = args.length;
        if (mode.equals("group") && n == 4) {
            String[] options = Arrays.copyOf(names, names.length + 1);
            options[names.length] = "to";
            return getListOfStringsMatchingLastWord(args, options);
        }
        if ((mode.equals("group") && n == 5) || (mode.equals("near") && n == 4) || (mode.equals("at") && n == 6) || (mode.equals("all") && n == 3)) {
            return getListOfStringsMatchingLastWord(args, "to");
        }
        return null;
    }
}
