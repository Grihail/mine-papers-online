package com.grihail.minepapers.picture;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Геометрия холста, свет светящейся картины, ломание и материалы стопки. */
class GeometryTest {

    private static PictureSettings at(int facing, int rotation, float w, float h) {
        PictureSettings s = new PictureSettings();
        s.x = 10;
        s.y = 64;
        s.z = -5;
        s.facing = facing;
        s.rotation = rotation;
        s.width = w;
        s.height = h;
        return s;
    }

    @Test
    void canvasLiesOnItsSurface() {
        for (int facing = 0; facing < 6; facing++) {
            for (int rot = 0; rot < 4; rot++) {
                PictureSettings s = at(facing, rot, 3, 2);
                double[] b = CanvasGeometry.canvasBounds(s);
                double dx = b[3] - b[0], dy = b[4] - b[1], dz = b[5] - b[2];
                double[] sizes = { dx, dy, dz };
                java.util.Arrays.sort(sizes);
                assertEquals(s.getDepth(), sizes[0], 1e-9, "thickness along the normal");
                assertEquals(2, sizes[1], 1e-9);
                assertEquals(3, sizes[2], 1e-9);
                // Холст касается клетки рамки (где кликнули при установке).
                assertTrue(b[0] <= s.x + 1 && b[3] >= s.x && b[1] <= s.y + 1 && b[4] >= s.y && b[2] <= s.z + 1 && b[5] >= s.z,
                    "facing " + facing + " rotation " + rot);
            }
        }
    }

    @Test
    void wallPictureGrowsRightAndUp() {
        // Смотрит на юг (+z): правая сторона для смотрящего — +x.
        PictureSettings s = at(3, 0, 2, 3);
        double[] b = CanvasGeometry.canvasBounds(s);
        assertArrayEquals(new double[] { 10, 64, -5, 12, 67, -5 + s.getDepth() }, b, 1e-9);
    }

    @Test
    void lightCellsCoverTheCanvas() {
        PictureSettings small = at(3, 0, 1, 1);
        assertEquals(1, CanvasGeometry.lightCells(small).size());
        PictureSettings big = at(3, 0, 16, 8);
        List<int[]> cells = CanvasGeometry.lightCells(big);
        assertEquals(4 * 2, cells.size());
        for (int[] c : cells) assertEquals(0, c[2], "a quarter block in front of the wall cell");
    }

    @Test
    void perpendicularPicturesMeetInCorner() {
        // Внутренний угол: стена смотрит на юг (+z), пол — вверх; картина на полу упирается краем в стену.
        PictureSettings wall = at(3, 0, 1, 1);
        wall.x = 0;
        wall.y = 0;
        wall.z = 0;
        PictureSettings floor = at(1, 0, 1, 1);
        floor.x = 0;
        floor.y = 0;
        floor.z = 0;
        double[] a = CanvasGeometry.canvasBounds(wall), b = CanvasGeometry.canvasBounds(floor);
        assertEquals(0, b[1], 1e-9, "floor picture lies on y = 0");
        assertEquals(0, a[2], 1e-9, "wall picture lies on z = 0");
        assertTrue(CanvasGeometry.cornerJoint(wall, a, floor, b));
        // Параллельные — никогда не стык.
        assertFalse(CanvasGeometry.cornerJoint(wall, a, wall, a));
    }

    @Test
    void lightsFollowGlowAndStayOutOfForeignBlocks() {
        List<String> world = new ArrayList<String>();
        PictureLights lights = new PictureLights() {
            @Override
            protected boolean place(int x, int y, int z, int level) {
                if (x == 16) return false; // занято чужим блоком
                String key = x + "," + y + "," + z;
                if (!world.contains(key)) world.add(key);
                return true;
            }

            @Override
            protected void remove(int x, int y, int z) {
                world.remove(x + "," + y + "," + z);
            }
        };
        PictureSettings s = at(3, 0, 8, 1);
        lights.tick(s, 15);
        assertTrue(world.isEmpty(), "nothing before markDirty");
        lights.markDirty();
        lights.tick(s, 15);
        assertEquals(1, world.size(), "one of two cells is taken by a foreign block");
        assertTrue(lights.owns(s, 12, 64, -5));
        assertFalse(lights.owns(s, 16, 64, -5));

        PictureLights loaded = new PictureLights() {
            @Override
            protected boolean place(int x, int y, int z, int level) {
                return true;
            }

            @Override
            protected void remove(int x, int y, int z) {
                world.remove(x + "," + y + "," + z);
            }
        };
        loaded.load(lights.save());
        loaded.clear(s);
        assertTrue(world.isEmpty(), "saved cells are removed after loading");

        lights.markDirty();
        lights.tick(s, 0);
        assertTrue(world.isEmpty(), "no glow, no light");
    }

    @Test
    void breakingTakesTime() {
        UUID p = UUID.randomUUID();
        BreakTimer.start(p, 7, 100);
        assertFalse(BreakTimer.finish(p, 7, 101, 0.05f), "20 ticks needed");
        BreakTimer.start(p, 7, 100);
        assertTrue(BreakTimer.finish(p, 7, 120, 0.05f));
        assertFalse(BreakTimer.finish(p, 7, 200, 0.05f), "one start - one break");
        BreakTimer.start(p, 7, 100);
        assertFalse(BreakTimer.finish(p, 8, 200, 0.05f), "another picture");
    }

    @Test
    void stackMaterialCounts() {
        assertTrue(MaterialStack.editable(false, 1));
        assertFalse(MaterialStack.editable(false, 2));
        assertEquals(5, MaterialStack.takeCount(10, true, 64));
        assertEquals(10, MaterialStack.takeCount(10, false, 64));
        assertEquals(16, MaterialStack.takeCount(100, false, 16));
        assertEquals(1, MaterialStack.putCount(10, 64, true));
        assertEquals(3, MaterialStack.putCount(10, 3, false));
    }

    @Test
    void numbersParseAndStep() {
        assertEquals(1.5f, Numbers.parse(" 1,5 "));
        assertNull(Numbers.parse("abc"));
        assertNull(Numbers.parse("NaN"));
        assertEquals("2", Numbers.fmt(2f));
        assertEquals("0.125", Numbers.fmt(0.125f));
        assertTrue(Numbers.SIZE.accepts("1."));
        assertFalse(Numbers.SIZE.accepts("-1"));
        assertTrue(Numbers.OFFSET.accepts("-1"));
        assertEquals("64", Numbers.SIZE.step("64", 1, 1));
        assertEquals("1024", Numbers.SIZE.step("1024", 1, 16));
        assertEquals(0.0625f, Numbers.snap(0.01f, false));
    }
}
