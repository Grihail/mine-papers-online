package com.grihail.minepapers.picture;

import java.util.UUID;

/** Картины для тестов. */
public final class PictureSettingsFixtures {

    public static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    public static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private PictureSettingsFixtures() {}

    /** Картина со всеми настройками не по умолчанию (заприваченная Алисой, подписанная Бобом). */
    public static PictureSettings custom() {
        PictureSettings s = new PictureSettings();
        s.source = PictureSettings.SOURCE_URL;
        s.localPath = "cats";
        s.yandexUrl = "https://disk.yandex.ru/d/abc";
        s.folderPath = "C:/pics";
        s.imageUrl = "https://example.com/a.png";
        s.interval = 7;
        s.width = 3;
        s.height = 2;
        s.offsetX = -1;
        s.offsetY = 0.5f;
        s.padding = 2;
        s.zoom = -1;
        s.imageX = 3;
        s.imageY = -2.5f;
        s.imageInPixels = true;
        s.title = "Title";
        s.description = "Line 1\nLine 2";
        s.owner = ALICE;
        s.ownerName = "Alice";
        s.signer = BOB;
        s.signerName = "Bob";
        s.nsfw = true;
        s.fitMode = PictureSettings.FIT_CONTAIN;
        s.glowPart = PictureSettings.GLOW_ALL;
        s.pixelsPerBlock = 64;
        s.sharpPixels = false;
        s.mapPalette = true;
        s.colors = 8;
        s.dither = 30;
        s.ditherMode = PictureSettings.DITHER_ORDERED;
        s.mode = 5;
        s.moldingWidth = 4;
        s.files = new byte[] { 1, 2, 3 };
        return s;
    }
}
