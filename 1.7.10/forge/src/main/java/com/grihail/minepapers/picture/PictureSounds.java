package com.grihail.minepapers.picture;

import net.minecraft.world.World;

/** Звуки картины — как у ванильной рамки для предметов из новых версий. В 1.7.10 у рамки своих звуков нет, поэтому они лежат в самом моде (assets/minepapers/sounds.json): установка, разрушение и удар при ломании (своего звука удара у рамки нет — тихий щелчок поворота предмета в ней). */
public final class PictureSounds {

    public static final String PLACE = "minepapers:picture.place", BREAK = "minepapers:picture.break", HIT = "minepapers:picture.hit";

    private PictureSounds() {}

    /** Картину повесили. Только на сервере. */
    public static void place(World world, double x, double y, double z) {
        world.playSoundEffect(x, y, z, PLACE, 1f, 1f);
    }

    /** Картину сломали. Только на сервере. */
    public static void destroy(World world, double x, double y, double z) {
        world.playSoundEffect(x, y, z, BREAK, 1f, 1f);
    }
}
