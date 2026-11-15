package com.grihail.minepapers.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
//? if >=26.1 {
/*import net.minecraft.server.permissions.Permissions;
*///?}

/**
 * /minepapers reload [что] [to &lt;игроки&gt;] — загрузить картинки картин заново (PictureReload). Что перезагрузить:
 * без слова — картина, на которую смотрит игрок; group &lt;номер&gt; [владелец] — группа пазла (без владельца — среди картин без привата,
 * с ним — среди его заприваченных; номер можно дать и одиночной картине); near &lt;радиус&gt; — картины вокруг игрока или командного блока;
 * at &lt;x&gt; &lt;y&gt; &lt;z&gt; — картина в этой клетке или вплотную к ней (~ — от себя); all — все загруженные картины.
 * to — у кого перезагрузить (ники и селекторы); без него — у всех. Оператор (и командный блок) перезагружает любые картины,
 * обычный игрок — только те, что может менять сам, и без all. Регистрирует движок (событие регистрации команд).
 */
public final class CommandMinePapers {

    public static final String NAME = "minepapers";
    /** Дальше этого картину, на которую смотрит игрок, не ищем. */
    private static final double LOOK_REACH = 64;
    private static final SimpleCommandExceptionType NONE = new SimpleCommandExceptionType(Component.translatable("minepapers.command.reload.none"));

    private CommandMinePapers() {}

    /** Что перезагружать — по разобранной команде. */
    private interface Selector {
        List<EntityPicture> select(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(NAME)
            // Игрок может всегда (права на картины проверяются при выборе), остальные — с уровнем оператора, как командный блок.
            .requires(source -> source.getEntity() instanceof ServerPlayer || op(source))
            .then(withTo(Commands.literal("reload"), ctx -> lookedAt(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("group").then(withTo(Commands.argument("number", IntegerArgumentType.integer(1)),
                        ctx -> group(IntegerArgumentType.getInteger(ctx, "number"), null))
                    .then(withTo(Commands.argument("owner", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder)),
                        ctx -> group(IntegerArgumentType.getInteger(ctx, "number"), StringArgumentType.getString(ctx, "owner"))))))
                .then(Commands.literal("near").then(withTo(Commands.argument("radius", DoubleArgumentType.doubleArg(0, 1024)),
                    ctx -> near(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "radius")))))
                .then(Commands.literal("at").then(withTo(Commands.argument("pos", BlockPosArgument.blockPos()),
                    ctx -> at(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(withTo(Commands.literal("all").requires(CommandMinePapers::op), ctx -> PictureReload.allPictures()))));
    }

    /** Узел, на котором команда может закончиться: дальше — необязательное "to &lt;игроки&gt;". */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withTo(T node, Selector selector) {
        return node.executes(ctx -> run(ctx, selector, null))
            .then(Commands.literal("to").then(Commands.argument("targets", EntityArgument.players())
                .executes(ctx -> run(ctx, selector, EntityArgument.getPlayers(ctx, "targets")))));
    }

    /** Уровень оператора (2), как у командного блока. */
    public static boolean op(CommandSourceStack source) {
        //? if >=26.1 {
        /*return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        *///?} else {
        return source.hasPermission(2);
        //?}
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Selector selector, Collection<ServerPlayer> to) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        List<EntityPicture> pictures = selector.select(ctx);
        if (!op(source) && source.getEntity() instanceof ServerPlayer player) {
            List<EntityPicture> mine = new ArrayList<EntityPicture>();
            for (EntityPicture p : pictures) {
                if (p.frame.mayEdit(player.getUUID())) mine.add(p);
            }
            pictures = mine;
        }
        if (pictures.isEmpty()) throw NONE.create();
        PictureReload.Result result = PictureReload.reload(pictures, to != null ? to : PictureReload.allPlayers());
        PictureReload.report(text -> source.sendSuccess(() -> text, false), result);
        return Math.max(result.pictures, Command.SINGLE_SUCCESS);
    }

    /** Группа пазла number: без владельца — среди картин без привата, с ним — среди его заприваченных. */
    private static List<EntityPicture> group(int number, String owner) {
        List<EntityPicture> found = new ArrayList<EntityPicture>();
        for (EntityPicture p : PictureReload.allPictures()) {
            if (p.frame.puzzle != number) continue;
            if (owner == null ? p.frame.owner == null : p.frame.owner != null && p.frame.ownerName.equalsIgnoreCase(owner)) found.add(p);
        }
        return found;
    }

    /** Картины, чей холст не дальше radius от игрока (его ног) или командного блока (центра блока). */
    private static List<EntityPicture> near(CommandSourceStack source, double radius) {
        Vec3 c = source.getPosition();
        List<EntityPicture> found = new ArrayList<EntityPicture>();
        for (EntityPicture p : PictureReload.pictures(source.getLevel())) {
            if (distanceSq(FrameGeometry.canvasBox(p.frame), c) <= radius * radius) found.add(p);
        }
        return found;
    }

    /** Картины в клетке pos — и вплотную к ней: картина висит перед блоком стены, а указать удобно и на сам блок. */
    private static List<EntityPicture> at(CommandSourceStack source, BlockPos pos) {
        AABB cell = new AABB(pos.getX() - 0.01, pos.getY() - 0.01, pos.getZ() - 0.01, pos.getX() + 1.01, pos.getY() + 1.01, pos.getZ() + 1.01);
        List<EntityPicture> found = new ArrayList<EntityPicture>();
        for (EntityPicture p : PictureReload.pictures(source.getLevel())) {
            if (FrameGeometry.canvasBox(p.frame).intersects(cell)) found.add(p);
        }
        return found;
    }

    private static double distanceSq(AABB box, Vec3 p) {
        double dx = Math.max(0, Math.max(box.minX - p.x, p.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - p.y, p.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - p.z, p.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Ближайшая картина на луче взгляда игрока, не дальше первого блока на нём. */
    private static List<EntityPicture> lookedAt(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(LOOK_REACH));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        // Картина висит перед блоком стены — до него луч доходит позже, чем до неё.
        double max = block.getType() != HitResult.Type.MISS ? eye.distanceTo(block.getLocation()) + 0.2 : LOOK_REACH;
        EntityPicture best = null;
        double bestDist = max;
        for (EntityPicture p : PictureReload.pictures(level)) {
            AABB box = FrameGeometry.canvasBox(p.frame);
            double d;
            if (box.contains(eye)) {
                d = 0;
            } else {
                Vec3 hit = box.clip(eye, end).orElse(null);
                if (hit == null) continue;
                d = eye.distanceTo(hit);
            }
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best != null ? Collections.singletonList(best) : Collections.<EntityPicture>emptyList();
    }
}
