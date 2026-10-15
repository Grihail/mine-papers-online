package com.grihail.minepapers.client;

import static com.grihail.minepapers.client.FrameEditor.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.PictureSettings;
import com.grihail.minepapers.picture.PictureSettingsFixtures;

/** Окно настройки без виджетов: поля — массив строк. */
class FrameEditorTest {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final class Editor extends FrameEditor {
        final String[] fields = new String[FIELD_COUNT];
        PictureConfig.Item[] materials = new PictureConfig.Item[PictureConfig.MATERIALS.length];
        int count = 1;

        Editor(PictureSettings tile, String version) {
            this(tile, version, ME);
        }

        Editor(PictureSettings tile, String version, UUID player) {
            super(tile, -1, player, "Me", version);
            for (int f = 0; f < FIELD_COUNT; f++) fields[f] = initialText(f);
        }

        @Override protected String text(int field) { return fields[field]; }
        @Override protected void setText(int field, String value) { fields[field] = value; }
        @Override protected String tr(String key, Object... args) { return key; }
        @Override protected int count() { return count; }
        @Override protected PictureConfig.Item material(int index) { return materials[index]; }
        @Override protected boolean itemExists(String id) { return true; }
        @Override protected long worldTime() { return 0; }
    }

    @Test
    void openAndCloseSendsNothing() {
        Editor ed = new Editor(new PictureSettings(), "1.21.1");
        assertFalse(ed.save(true));
        ed.materials[0] = new PictureConfig.Item("minecraft:stone", -1);
        assertFalse(ed.save(true), "panel uses its frame block");
    }

    @Test
    void unusedMaterialsMustGoBack() {
        PictureSettings tile = new PictureSettings();
        tile.mode = PictureSettings.MODE_DECAL;
        Editor ed = new Editor(tile, "1.21.1");
        ed.materials[0] = new PictureConfig.Item("minecraft:stone", -1);
        assertTrue(ed.save(true), "graffiti has no frame: its block goes back to the player");
        assertFalse(ed.save(false), "unless materials of this stack cannot be changed");
    }

    @Test
    void typedValuesReachThePicture() {
        PictureSettings tile = new PictureSettings();
        Editor ed = new Editor(tile, "1.21.1");
        ed.fields[F_TITLE] = "  Hello  ";
        ed.fields[F_WIDTH] = "2,5";
        ed.fields[F_PATH] = "cats\n";
        ed.fields[F_MOLDING] = "4";
        ed.moldingChanged();
        ed.toggleSizePx();
        assertEquals("40", ed.fields[F_WIDTH], "blocks -> pixels");
        assertTrue(ed.save(false));
        assertEquals("Hello", tile.title);
        assertEquals("cats", tile.localPath);
        assertTrue(tile.sizeInPixels);
        assertEquals(40, tile.width);
        assertEquals(PictureSettings.FRAME_MOLDING, tile.getFrameShape());
        assertEquals(4, tile.moldingWidth);
    }

    @Test
    void closingAndOpeningTheLock() {
        PictureSettings tile = new PictureSettings();
        Editor ed = new Editor(tile, "1.21.1");
        assertNull(ed.lockOwner());
        ed.locked = true;
        assertEquals(ME, ed.lockOwner());
        assertFalse(ed.canSign(), "locked pictures are not signed");
        ed.save(false);
        assertEquals(ME, tile.owner);
        assertFalse(new Editor(tile, "1.21.1").readOnly, "the owner edits the picture");
        Editor stranger = new Editor(tile, "1.21.1", UUID.randomUUID());
        assertTrue(stranger.readOnly);
        assertFalse(stranger.canSign());
        assertFalse(stranger.fieldShown(F_TITLE));
    }

    @Test
    void stacksAreNotSigned() {
        Editor ed = new Editor(new PictureSettings(), "1.21.1");
        assertTrue(ed.canSign());
        ed.count = 5;
        assertFalse(ed.canSign());
    }

    @Test
    void copyThenPasteGivesTheSameConfig() {
        PictureSettings tile = PictureSettingsFixtures.custom();
        tile.owner = null; // иначе окно только для чтения
        Editor a = new Editor(tile, "1.21.1");
        a.materials[1] = new PictureConfig.Item("minecraft:oak_planks", -1);
        String json = a.copyConfig().toJson();

        Editor b = new Editor(new PictureSettings(), "1.21.1");
        PictureConfig pasted = b.paste(json);
        assertNotNull(pasted);
        b.materials[1] = new PictureConfig.Item("minecraft:oak_planks", -1);
        assertEquals(json, b.copyConfig().toJson());
        assertEquals("minecraft:oak_planks", pasted.materials[1].id);
    }

    @Test
    void pasteTranslatesItemsFromOtherEra() {
        Editor old = new Editor(new PictureSettings(), "1.7.10");
        PictureConfig c = old.paste("{\"minecraft\": \"1.21.1\", \"materials\": {\"frame\": \"minecraft:red_wool\"}}");
        assertEquals("minecraft:wool", c.materials[0].id);
        assertEquals(14, c.materials[0].meta);
        assertNull(old.paste("garbage"));
    }

    @Test
    void numberFieldsStepInChosenUnits() {
        Editor ed = new Editor(new PictureSettings(), "1.21.1");
        ed.step(F_WIDTH, 1);
        assertEquals("2", ed.fields[F_WIDTH]);
        ed.toggleSizePx();
        ed.step(F_WIDTH, 1);
        assertEquals("33", ed.fields[F_WIDTH]);
        ed.fields[F_WIDTH] = "1024";
        ed.step(F_WIDTH, 1);
        assertEquals("1024", ed.fields[F_WIDTH], "64 blocks at most");
        ed.step(F_MOLDING, 1);
        assertEquals(PictureSettings.FRAME_MOLDING, ed.frameShape, "molding width turns the frame into molding");
        ed.chooseFrame(PictureSettings.FRAME_NONE);
        assertEquals("0", ed.fields[F_MOLDING]);
    }

    @Test
    void onlyChosenSourcePathIsShown() {
        Editor ed = new Editor(new PictureSettings(), "1.21.1");
        ed.selectTab(TAB_SOURCE);
        assertTrue(ed.fieldShown(F_PATH));
        assertFalse(ed.fieldShown(F_URL));
        ed.source = PictureSettings.SOURCE_YANDEX;
        assertTrue(ed.fieldShown(F_URL));
        assertFalse(ed.fieldShown(F_WIDTH), "other tab");
    }
}
