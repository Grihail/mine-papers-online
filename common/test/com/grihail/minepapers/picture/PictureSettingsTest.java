package com.grihail.minepapers.picture;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PictureSettingsTest {

    private static final UUID ALICE = PictureSettingsFixtures.ALICE;
    private static final UUID BOB = PictureSettingsFixtures.BOB;

    @Test
    void settingsSurviveRoundTrip() {
        PictureSettings a = PictureSettingsFixtures.custom();
        MapTag tag = MapTag.of(a);
        PictureSettings b = new PictureSettings();
        b.readSettings(tag);
        assertEquals(tag, MapTag.of(b));
        assertEquals("Line 1\nLine 2", b.description);
        assertEquals(ALICE, b.owner);
        assertEquals(BOB, b.signer);
        assertArrayEquals(new byte[] { 1, 2, 3 }, b.files);
    }

    @Test
    void placementSurvivesRoundTrip() {
        PictureSettings a = new PictureSettings();
        a.x = -100;
        a.y = 64;
        a.z = 30_000_000;
        a.facing = 1;
        a.rotation = 3;
        a.surface = -0.5f;
        MapTag tag = new MapTag();
        a.writePlacement(tag);
        PictureSettings b = new PictureSettings();
        b.readPlacement(tag);
        assertEquals(a.x, b.x);
        assertEquals(a.y, b.y);
        assertEquals(a.z, b.z);
        assertEquals(1, b.getFacing());
        assertEquals(3, b.getRotation());
        assertEquals(-0.5f, b.surface);
    }

    @Test
    void readClampsHostileValues() {
        MapTag tag = MapTag.of(new PictureSettings());
        tag.putFloat("W", 1000);
        tag.putFloat("H", -5);
        tag.putFloat("OX", 500);
        tag.putInt("PPB", 100000);
        tag.putInt("Colors", 1);
        tag.putInt("Mode", 99);
        tag.putInt("Interval", -3);
        tag.putString("Title", "a\nb");
        PictureSettings s = new PictureSettings();
        s.readSettings(tag);
        assertEquals(PictureSettings.MAX_SIZE_BLOCKS, s.getWidthBlocks());
        assertEquals(PictureSettings.MAX_SIZE_BLOCKS, s.getHeightBlocks(), "negative height takes the width");
        assertTrue(s.getOffsetXBlocks() < 1, "canvas must overlap the frame cell");
        assertEquals(PictureSettings.MAX_PPB, s.pixelsPerBlock);
        assertEquals(PictureSettings.MIN_COLORS, s.colors);
        assertEquals(PictureSettings.LOOK_COUNT - 1, s.mode);
        assertEquals(1, s.interval);
        assertEquals("a b", s.title);
    }

    @Test
    void oldStyleBecomesPaletteAndDither() {
        MapTag tag = MapTag.of(new PictureSettings());
        // Картина до 1.5.0: стиль вместо числа цветов.
        PictureSettings s = new PictureSettings();
        MapTag old = new MapTag();
        old.putInt("Style", PictureSettings.STYLE_MAP);
        s.readSettings(old);
        assertTrue(s.mapPalette);
        assertEquals(0, s.dither);
        s.readSettings(tag);
        assertEquals(PictureSettings.MAX_COLORS, s.colors);
    }

    @Test
    void oldNegativePaddingBecomesZoom() {
        MapTag tag = new MapTag();
        tag.putFloat("Pad", -3);
        tag.putInt("PadPx", 1);
        PictureSettings s = new PictureSettings();
        s.readSettings(tag);
        assertEquals(0, s.padding);
        assertEquals(3, s.zoom);
        assertTrue(s.zoomInPixels);
    }

    @Test
    void lockBelongsToWhoeverClosesIt() {
        PictureSettings s = new PictureSettings();
        // Клиент Алисы просит закрыть замок от имени Боба: картина всё равно становится её.
        s.owner = BOB;
        s.ownerName = "Bob";
        s.keepServerFields(s.getOriginKey(), PictureSettings.NO_FILES, null, "", null, "", ALICE, "Alice");
        assertEquals(ALICE, s.owner);
        assertEquals("Alice", s.ownerName);
        assertTrue(s.mayEdit(ALICE));
        assertFalse(s.mayEdit(BOB));
        // Открыть замок может только владелец (до этого сервер не пустит чужого), и картина снова ничья.
        s.owner = null;
        s.keepServerFields(s.getOriginKey(), PictureSettings.NO_FILES, ALICE, "Alice", null, "", ALICE, "Alice");
        assertNull(s.owner);
    }

    @Test
    void signatureIsForeverAndOnlyWithoutLock() {
        PictureSettings s = new PictureSettings();
        s.signer = BOB; // просьба подписать
        s.keepServerFields(null, PictureSettings.NO_FILES, null, "", null, "", ALICE, "Alice");
        assertEquals(ALICE, s.signer, "signed by the sender, not by the name in the packet");
        // Снять подпись нельзя: клиент прислал пустую — сервер оставляет прежнюю.
        s.signer = null;
        s.keepServerFields(null, PictureSettings.NO_FILES, null, "", ALICE, "Alice", BOB, "Bob");
        assertEquals(ALICE, s.signer);
        // Заприваченную не подписывают.
        PictureSettings locked = new PictureSettings();
        locked.signer = BOB;
        locked.keepServerFields(null, PictureSettings.NO_FILES, ALICE, "Alice", null, "", ALICE, "Alice");
        assertNull(locked.signer);
    }

    @Test
    void sharedFilesStayOnlyWithSameSource() {
        PictureSettings s = new PictureSettings();
        s.localPath = "cats";
        byte[] files = new byte[20];
        s.keepServerFields("local:cats", files, null, "", null, "", ALICE, "Alice");
        assertSame(files, s.files);
        s.keepServerFields("local:dogs", files, null, "", null, "", ALICE, "Alice");
        assertEquals(0, s.files.length);
    }

    @Test
    void looksCombineFrameAndCanvas() {
        for (int look = 0; look < PictureSettings.LOOK_COUNT; look++) {
            assertEquals(look, PictureSettings.look(PictureSettings.frameShapeOf(look), PictureSettings.canvasKindOf(look)));
        }
    }

    @Test
    void wrapKeepsLinesShort() {
        for (String line : PictureSettings.wrap("a very long description with averyveryveryverylongword inside\nnext", 10)) {
            assertTrue(line.length() <= 10, line);
        }
    }

    @Test
    void paddingAndZoomLeaveAPixel() {
        PictureSettings s = new PictureSettings();
        s.paddingInPixels = false;
        s.padding = 10;
        assertEquals((1 - PictureSettings.PIXEL) / 2, s.getPaddingBlocks(), 1e-6);
        s.zoomInPixels = false;
        s.zoom = -10;
        float inner = 1 - 2 * s.getPaddingBlocks();
        assertEquals((PictureSettings.PIXEL - inner) / 2, s.getZoomBlocks(), 1e-6);
    }
}
