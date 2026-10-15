package com.grihail.minepapers.picture;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Сервер ломает картину, только если игрок "копал" её не меньше, чем нужно ему с его инструментом (см. PacketBreakPicture). Только серверный поток. */
public final class BreakTimer {

    /** Игрок -> {id картины, тик начала}. */
    private static final Map<UUID, long[]> STARTED = new HashMap<UUID, long[]>();

    private BreakTimer() {}

    /** Игрок начал копать картину entityId в тик now. */
    public static void start(UUID player, int entityId, long now) {
        STARTED.put(player, new long[] { entityId, now });
    }

    /** Игрок докопал картину entityId в тик now: хватило ли времени при скорости rate (доля поломки за тик). С небольшим запасом на задержки сети: клиент считает по своим тикам. */
    public static boolean finish(UUID player, int entityId, long now, float rate) {
        long[] started = STARTED.remove(player);
        if (started == null || started[0] != entityId) return false;
        int required = (int) Math.ceil(1 / Math.max(rate, 1e-4f));
        return now - started[1] >= required * 0.7 - 2;
    }

    /** Забыть начатое (творческий режим ломает сразу). */
    public static void cancel(UUID player) {
        STARTED.remove(player);
    }
}
