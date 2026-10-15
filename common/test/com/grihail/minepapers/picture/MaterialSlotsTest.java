package com.grihail.minepapers.picture;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.grihail.minepapers.ModConfig;

class MaterialSlotsTest {

    private static final UUID ALICE = PictureSettingsFixtures.ALICE;
    private static final UUID BOB = PictureSettingsFixtures.BOB;

    /** Стопка предметов: id, число, максимум стопки; данные предмета — tag. Пустая — null, как в 1.7.10. */
    static final class Stack {
        final String id;
        int count;
        final int max;
        final String tag;

        Stack(String id, int count) {
            this(id, count, 64, "");
        }

        Stack(String id, int count, int max, String tag) {
            this.id = id;
            this.count = count;
            this.max = max;
            this.tag = tag;
        }

        @Override
        public String toString() {
            return count + "x" + id + tag;
        }
    }

    /** Картина-предмет в инвентаре: стопка и её материалы. */
    static final class Picture {
        Stack stack;
        final String[] materials = new String[3];
    }

    /** Висящая картина (held == null) или стопка картин-предметов (frame — "frame"); игрок с инвентарём из 36 слотов. */
    static final class Slots extends MaterialSlots<Stack> {
        final PictureSettings settings = new PictureSettings();
        final Stack[] window = new Stack[3];
        final Stack[] inventory = new Stack[36];
        final List<Stack> received = new ArrayList<>();
        /** Стопки картин-предметов: первая — та, что в руке; отделившиеся добавляются (с материалами в Picture). */
        final List<Picture> pictures = new ArrayList<>();
        Stack carried;
        boolean creative;
        int changes;

        static Slots hanging() {
            return new Slots();
        }

        static Slots stack(int count) {
            Slots s = new Slots();
            Picture p = new Picture();
            p.stack = new Stack("frame", count, 64, "");
            s.pictures.add(p);
            return s;
        }

        Picture held0() {
            return pictures.get(0);
        }

        @Override
        protected Stack empty() {
            return null;
        }

        @Override
        protected boolean isEmpty(Stack stack) {
            return stack == null || stack.count <= 0;
        }

        @Override
        protected int count(Stack stack) {
            return stack.count;
        }

        @Override
        protected int maxStack(Stack stack) {
            return stack.max;
        }

        @Override
        protected Stack copy(Stack stack, int count) {
            return new Stack(stack.id, count, stack.max, stack.tag);
        }

        @Override
        protected void shrink(Stack stack, int n) {
            stack.count -= n;
        }

        @Override
        protected boolean same(Stack a, Stack b) {
            return a.id.equals(b.id) && a.tag.equals(b.tag);
        }

        @Override
        protected boolean matches(Stack stack, PictureConfig.Item item) {
            return stack.id.equals(item.id);
        }

        @Override
        protected Stack create(PictureConfig.Item item) {
            return item.id.equals("unknown") ? null : new Stack(item.id, 1);
        }

        @Override
        protected PictureSettings frame() {
            return settings;
        }

        @Override
        protected int pictures() {
            return pictures.isEmpty() ? 1 : held0().stack.count;
        }

        @Override
        protected Stack material(int index) {
            return window[index];
        }

        @Override
        protected void setMaterial(int index, Stack stack) {
            window[index] = isEmpty(stack) ? null : copy(stack, 1);
        }

        @Override
        protected boolean accepts(int index, Stack stack) {
            return !stack.id.equals("frame") && (index != GLOW || stack.id.equals("glowstone"));
        }

        @Override
        protected void settingsChanged() {
            changes++;
            if (!pictures.isEmpty()) System.arraycopy(ids(window), 0, held0().materials, 0, 3);
        }

        @Override
        protected Stack held() {
            return pictures.isEmpty() ? null : held0().stack;
        }

        @Override
        protected void storeMaterial(Stack stack, int index, Stack material) {
            Picture p = pictureOf(stack);
            if (p == null) {
                // Отделившаяся стопка: настройки — как у той, от которой она отделилась.
                p = new Picture();
                p.stack = stack;
                System.arraycopy(held0().materials, 0, p.materials, 0, 3);
                pictures.add(p);
            }
            p.materials[index] = isEmpty(material) ? null : material.id;
        }

        private Picture pictureOf(Stack stack) {
            for (Picture p : pictures) {
                if (p.stack == stack) return p;
            }
            return null;
        }

        @Override
        protected void reload() {
            String[] m = held0().materials;
            for (int i = 0; i < 3; i++) window[i] = m[i] != null ? new Stack(m[i], 1) : null;
        }

        @Override
        protected Stack carried() {
            return carried;
        }

        @Override
        protected void setCarried(Stack stack) {
            carried = stack;
        }

        @Override
        protected void addToInventory(Stack stack) {
            assertTrue(stack.count <= stack.max, "стопка больше максимальной");
            received.add(stack);
        }

        @Override
        protected int inventorySize() {
            return inventory.length;
        }

        @Override
        protected Stack inventoryItem(int slot) {
            return inventory[slot];
        }

        @Override
        protected Stack removeFromInventory(int slot, int n) {
            Stack s = inventory[slot];
            s.count -= n;
            if (s.count <= 0) inventory[slot] = null;
            return copy(s, n);
        }

        @Override
        protected boolean creative() {
            return creative;
        }

        static String[] ids(Stack[] stacks) {
            String[] ids = new String[stacks.length];
            for (int i = 0; i < stacks.length; i++) ids[i] = stacks[i] != null ? stacks[i].id : null;
            return ids;
        }

        /** Сколько предметов id получил игрок. */
        int receivedCount(String id) {
            int n = 0;
            for (Stack s : received) {
                if (s.id.equals(id)) n += s.count;
            }
            return n;
        }

        /** Отделившиеся от стопки картины. */
        Picture split() {
            return pictures.size() > 1 ? pictures.get(1) : null;
        }
    }

    @AfterEach
    void restoreConfig() {
        ModConfig.stackMaterials = true;
    }

    // ---------- Клик ----------

    @Test
    void putIntoHangingPictureTakesOneBlock() {
        Slots s = Slots.hanging();
        s.carried = new Stack("planks", 10);
        s.click(MaterialSlots.BORDER, false);
        assertEquals("planks", s.window[0].id);
        assertEquals(9, s.carried.count);
        assertEquals(1, s.changes);
    }

    @Test
    void putIntoWholeStack() {
        Slots s = Slots.stack(16);
        s.carried = new Stack("planks", 20);
        s.click(MaterialSlots.BORDER, false);
        assertEquals(4, s.carried.count);
        assertEquals("planks", s.held0().materials[0]);
        assertEquals(16, s.held0().stack.count);
        assertNull(s.split());
        assertEquals("planks", s.window[0].id);
    }

    @Test
    void fewerBlocksThanPicturesSplitsChangedOnes() {
        Slots s = Slots.stack(16);
        s.carried = new Stack("planks", 5);
        s.click(MaterialSlots.BORDER, false);
        assertNull(s.carried);
        assertEquals(11, s.held0().stack.count);
        assertNull(s.held0().materials[0]);
        assertEquals(5, s.split().stack.count);
        assertEquals("planks", s.split().materials[0]);
        assertEquals(5, s.receivedCount("frame"));
    }

    @Test
    void rightClickPutsOne() {
        Slots s = Slots.stack(4);
        s.carried = new Stack("planks", 10);
        s.click(MaterialSlots.CANVAS, true);
        assertEquals(9, s.carried.count);
        assertEquals(3, s.held0().stack.count);
        assertEquals(1, s.split().stack.count);
        assertEquals("planks", s.split().materials[1]);
    }

    @Test
    void takeAllAndHalf() {
        Slots s = Slots.stack(5);
        s.held0().materials[0] = "planks";
        s.reload();
        s.click(MaterialSlots.BORDER, true);
        assertEquals(3, s.carried.count);
        assertEquals("planks", s.carried.id);
        assertEquals(2, s.held0().stack.count);
        assertEquals("planks", s.held0().materials[0]);
        assertNull(s.split().materials[0]);

        s.carried = null;
        s.click(MaterialSlots.BORDER, false);
        assertEquals(2, s.carried.count);
        assertNull(s.held0().materials[0]);
    }

    @Test
    void takeIsLimitedByMaxStack() {
        Slots s = Slots.stack(20);
        s.held0().materials[2] = "glowstone";
        s.window[2] = new Stack("glowstone", 1, 16, "");
        s.click(MaterialSlots.GLOW, false);
        assertEquals(16, s.carried.count);
        assertEquals(4, s.held0().stack.count);
    }

    @Test
    void replaceReturnsOldBlocksToHand() {
        Slots s = Slots.stack(3);
        s.held0().materials[0] = "planks";
        s.reload();
        s.carried = new Stack("stone", 3);
        s.click(MaterialSlots.BORDER, false);
        assertEquals("planks", s.carried.id);
        assertEquals(3, s.carried.count);
        assertEquals("stone", s.held0().materials[0]);
    }

    @Test
    void replaceWithBlocksLeftGivesOldToInventory() {
        Slots s = Slots.stack(3);
        s.held0().materials[0] = "planks";
        s.reload();
        s.carried = new Stack("stone", 10);
        s.click(MaterialSlots.BORDER, false);
        assertEquals("stone", s.carried.id);
        assertEquals(7, s.carried.count);
        assertEquals(3, s.receivedCount("planks"));
    }

    @Test
    void sameItemOrWrongSlotDoesNothing() {
        Slots s = Slots.hanging();
        s.window[0] = new Stack("planks", 1);
        s.carried = new Stack("planks", 5);
        s.click(MaterialSlots.BORDER, false);
        assertEquals(5, s.carried.count);
        s.carried = new Stack("stone", 5);
        s.click(MaterialSlots.GLOW, false);
        assertEquals(5, s.carried.count);
        assertNull(s.window[2]);
        s.carried = new Stack("frame", 1);
        s.click(MaterialSlots.CANVAS, false);
        assertNull(s.window[1]);
        assertEquals(0, s.changes);
    }

    @Test
    void sameItemWithOtherDataReplaces() {
        Slots s = Slots.hanging();
        s.window[0] = new Stack("planks", 1, 64, "{a}");
        s.carried = new Stack("planks", 1, 64, "{b}");
        s.click(MaterialSlots.BORDER, false);
        assertEquals("{b}", s.window[0].tag);
        assertEquals("{a}", s.carried.tag);
    }

    @Test
    void shiftClickTakesAllToInventory() {
        Slots s = Slots.stack(70);
        s.held0().materials[1] = "planks";
        s.reload();
        s.takeToInventory(MaterialSlots.CANVAS);
        assertEquals(70, s.receivedCount("planks"));
        assertEquals(2, s.received.size()); // 64 + 6
        assertNull(s.held0().materials[1]);
        assertNull(s.split());
    }

    @Test
    void quickMoveFillsFirstEmptyAcceptingSlot() {
        Slots s = Slots.stack(4);
        s.held0().materials[0] = "planks";
        s.reload();
        Stack stone = new Stack("stone", 10);
        assertTrue(s.quickMove(stone));
        assertEquals(6, stone.count);
        assertEquals("stone", s.held0().materials[1]);
        assertFalse(s.quickMove(stone)); // свечение камень не примет
        Stack glow = new Stack("glowstone", 2);
        assertTrue(s.quickMove(glow));
        assertEquals(0, glow.count);
        assertEquals(2, s.held0().stack.count);
        assertEquals("glowstone", s.split().materials[2]);

        Stack frame = new Stack("frame", 1);
        assertFalse(s.quickMove(frame));
    }

    // ---------- Вставка конфига ----------

    private static PictureConfig.Item item(String id) {
        return new PictureConfig.Item(id, -1);
    }

    @Test
    void pasteTakesFromInventoryAndReturnsOld() {
        Slots s = Slots.stack(5);
        s.held0().materials[0] = "planks";
        s.reload();
        s.inventory[3] = new Stack("stone", 3);
        s.inventory[7] = new Stack("stone", 4);
        List<PictureConfig.Item> missing = s.paste(new boolean[] { true, false, false },
            new PictureConfig.Item[] { item("stone"), null, null });
        assertTrue(missing.isEmpty());
        assertEquals("stone", s.held0().materials[0]);
        assertNull(s.inventory[3]);
        assertEquals(2, s.inventory[7].count);
        assertEquals(5, s.receivedCount("planks"));
    }

    @Test
    void pasteNotEnoughTakesNothing() {
        Slots s = Slots.stack(5);
        s.inventory[0] = new Stack("stone", 4);
        List<PictureConfig.Item> missing = s.paste(new boolean[] { true, true, true },
            new PictureConfig.Item[] { item("stone"), null, item("stone") });
        assertEquals(2, missing.size());
        assertEquals(4, s.inventory[0].count);
        assertNull(s.held0().materials[0]);
    }

    @Test
    void pasteCreativeCreatesMissing() {
        Slots s = Slots.hanging();
        s.creative = true;
        List<PictureConfig.Item> missing = s.paste(new boolean[] { true, false, true },
            new PictureConfig.Item[] { item("unknown"), null, item("stone") });
        // Неизвестного предмета нет, а камень в слот свечения не кладётся.
        assertEquals(2, missing.size());
        missing = s.paste(new boolean[] { true, false, true },
            new PictureConfig.Item[] { item("planks"), null, item("glowstone") });
        assertTrue(missing.isEmpty());
        assertEquals("planks", s.window[0].id);
        assertEquals("glowstone", s.window[2].id);
    }

    @Test
    void pasteNullRemovesAndKeepsSameItem() {
        Slots s = Slots.stack(2);
        s.held0().materials[0] = "planks";
        s.held0().materials[1] = "stone";
        s.reload();
        s.paste(new boolean[] { true, true, false }, new PictureConfig.Item[] { item("planks"), null, null });
        assertEquals("planks", s.held0().materials[0]);
        assertNull(s.held0().materials[1]);
        assertEquals(2, s.receivedCount("stone"));
        assertEquals(0, s.receivedCount("planks"));
    }

    @Test
    void pasteRespectsStackMaterials() {
        ModConfig.stackMaterials = false;
        Slots s = Slots.stack(2);
        s.creative = true;
        s.paste(new boolean[] { true, false, false }, new PictureConfig.Item[] { item("planks"), null, null });
        assertNull(s.held0().materials[0]);
        Slots one = Slots.stack(1);
        one.creative = true;
        one.paste(new boolean[] { true, false, false }, new PictureConfig.Item[] { item("planks"), null, null });
        assertEquals("planks", one.held0().materials[0]);
    }

    // ---------- Сохранение ----------

    @Test
    void saveKeepsServerFieldsAndReturnsUnused() {
        Slots s = Slots.stack(3);
        s.held0().materials[0] = "planks";
        s.held0().materials[1] = "stone";
        s.reload();
        s.settings.localPath = "cats";
        s.settings.files = new byte[] { 1 };
        s.save(() -> {
            s.settings.mode = PictureSettings.MODE_DECAL; // без рамки и без холста
            s.settings.title = "New";
            s.settings.files = new byte[] { 9 };
        }, ALICE, "Alice");
        assertEquals("New", s.settings.title);
        assertArrayEquals(new byte[] { 1 }, s.settings.files);
        assertEquals(3, s.receivedCount("planks"));
        assertEquals(3, s.receivedCount("stone"));
        assertNull(s.held0().materials[0]);
        assertNull(s.held0().materials[1]);
    }

    @Test
    void saveDoesNotSignStack() {
        Slots s = Slots.stack(2);
        s.save(() -> s.settings.signer = ALICE, ALICE, "Alice");
        assertNull(s.settings.signer);
        Slots one = Slots.hanging();
        one.save(() -> one.settings.signer = ALICE, ALICE, "Alice");
        assertEquals(ALICE, one.settings.signer);
    }

    @Test
    void saveByStrangerKeepsLockedMaterials() {
        Slots s = Slots.hanging();
        s.settings.owner = BOB;
        s.window[0] = new Stack("planks", 1);
        s.save(() -> s.settings.mode = PictureSettings.MODE_DECAL, ALICE, "Alice");
        assertEquals("planks", s.window[0].id);
        assertEquals(BOB, s.settings.owner);
    }
}
