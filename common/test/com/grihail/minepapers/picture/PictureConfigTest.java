package com.grihail.minepapers.picture;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PictureConfigTest {

    @Test
    void jsonRoundTrip() {
        PictureConfig c = new PictureConfig();
        c.minecraft = "1.21.1";
        c.title = "Cat";
        c.description = "multi\nline";
        c.nsfw = false;
        c.source = PictureSettings.SOURCE_FOLDER;
        c.path = "C:\\pics";
        c.interval = 12;
        c.width = 2.5f;
        c.height = 3f;
        c.sizePixels = false;
        c.offsetRight = -1f;
        c.offsetUp = 0f;
        c.offsetPixels = true;
        c.padding = 1f;
        c.paddingPixels = true;
        c.zoom = -2f;
        c.zoomPixels = true;
        c.fit = PictureSettings.FIT_SCALE_DOWN;
        c.glowPart = PictureSettings.GLOW_FRAME;
        c.look = PictureSettings.MODE_REALISTIC;
        c.frame = PictureSettings.FRAME_MOLDING;
        c.moldingWidth = 3;
        c.canvas = PictureSettings.CANVAS_PICTURE;
        c.style = PictureSettings.STYLE_MAP_DITHER;
        c.sharpPixels = true;
        c.mapPalette = true;
        c.colors = 9;
        c.dither = 50;
        c.ditherMode = PictureSettings.DITHER_ORDERED;
        c.pixelsPerBlock = 32;
        c.hasMaterial[0] = true;
        c.materials[0] = new PictureConfig.Item("minecraft:oak_planks", -1);
        c.hasMaterial[2] = true; // явный null — вынуть

        String json = c.toJson();
        PictureConfig d = PictureConfig.fromJson(json);
        assertEquals(json, d.toJson());
        assertEquals("multi\nline", d.description);
        assertEquals(2.5f, d.width);
        assertEquals(PictureSettings.DITHER_ORDERED, (int) d.ditherMode);
        assertEquals(PictureSettings.GLOW_FRAME, (int) d.glowPart);
        assertTrue(json.contains("\"glowPart\": \"frame\""));
        assertTrue(d.hasMaterial[0]);
        assertFalse(d.hasMaterial[1]);
        assertTrue(d.hasMaterial[2]);
        assertNull(d.materials[2]);
        assertTrue(d.anyMaterial());
    }

    @Test
    void handWrittenShortForms() {
        PictureConfig c = PictureConfig.fromJson("{\"fit\": 2, \"look\": \"GRAFFITI\", \"materials\": {\"frame\": \"minecraft:gold_block\"}}");
        assertEquals(PictureSettings.FIT_COVER, (int) c.fit);
        assertEquals(PictureSettings.MODE_DECAL, (int) c.look);
        assertEquals("minecraft:gold_block", c.materials[0].id);
        assertNull(c.width, "missing means unchanged");
    }

    @Test
    void garbageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PictureConfig.fromJson("not json"));
        assertThrows(IllegalArgumentException.class, () -> PictureConfig.fromJson("[1, 2]"));
        assertThrows(IllegalArgumentException.class, () -> PictureConfig.fromJson("{\"fit\": 99}"));
        assertThrows(IllegalArgumentException.class, () -> PictureConfig.fromJson(null));
    }

    @Test
    void flatteningByVersion() {
        assertFalse(PictureConfig.isFlattened("1.7.10"));
        assertFalse(PictureConfig.isFlattened("1.12.2"));
        assertTrue(PictureConfig.isFlattened("1.13"));
        assertTrue(PictureConfig.isFlattened("1.21.1"));
        assertTrue(PictureConfig.isFlattened("26.1"), "year-based versions");
    }

    @Test
    void itemsTranslateBetweenEras() {
        PictureConfig fromOld = new PictureConfig();
        fromOld.minecraft = "1.7.10";
        PictureConfig.Item wool = fromOld.translate(new PictureConfig.Item("minecraft:wool", 14), true, id -> false);
        assertEquals("minecraft:red_wool", wool.id);

        PictureConfig fromNew = new PictureConfig();
        fromNew.minecraft = "1.21.1";
        PictureConfig.Item old = fromNew.translate(new PictureConfig.Item("minecraft:red_wool", -1), false, id -> false);
        assertEquals("minecraft:wool", old.id);
        assertEquals(14, old.meta);

        // Без версии (написан руками): имя как есть, если такой предмет есть.
        PictureConfig manual = new PictureConfig();
        PictureConfig.Item same = manual.translate(new PictureConfig.Item("minecraft:stone", -1), true, id -> true);
        assertEquals("minecraft:stone", same.id);

        // Непонятная версия не роняет вставку.
        PictureConfig weird = new PictureConfig();
        weird.minecraft = "snapshot";
        assertNotNull(weird.translate(new PictureConfig.Item("minecraft:stone", -1), true, id -> true));
    }
}
