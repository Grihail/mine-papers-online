package com.grihail.minepapers.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.grihail.minepapers.picture.CanvasGeometry;
import com.grihail.minepapers.picture.PictureSettings;

/** Сетка картины для всех видов рамки и холста, размеров и сторон: только четырёхугольники, всё внутри хитбокса, координаты текстуры внутри спрайта. */
class PictureMeshTest {

    /** Спрайт в атласе: пиксели 0..16 -> [0.25, 0.5]. */
    private static final PictureMesh.Skin SKIN = new PictureMesh.Skin(0x80FF40) {
        @Override
        public Object atlas() {
            return "atlas";
        }

        @Override
        public double u(double px) {
            return 0.25 + px / 64;
        }

        @Override
        public double v(double px) {
            return 0.25 + px / 64;
        }
    };

    private static final class Recorder extends PictureMesh {
        final List<double[]> vertices = new ArrayList<double[]>();
        final List<Integer> lights = new ArrayList<Integer>();
        final List<Integer> kinds = new ArrayList<Integer>();
        int finished;

        @Override
        protected void emit(double x, double y, double z, double tu, double tv) {
            vertices.add(new double[] { x, y, z, tu, tv, r, g, b });
            lights.add(light);
        }

        @Override
        protected int lightAt(int x, int y, int z) {
            return 7 << 20 | 3 << 4;
        }

        @Override
        protected boolean opaqueAt(int x, int y, int z) {
            return false;
        }

        @Override
        protected void texture(int kind, Object texture) {
            kinds.add(kind);
        }

        @Override
        protected void finish() {
            finished++;
        }
    }

    @Test
    void everyLookStaysInsideItsHitbox() {
        float[][] sizes = { { 1, 1 }, { 0.25f, 3 }, { 5.5f, 2 }, { 64, 64 } };
        for (int look = 0; look < PictureSettings.LOOK_COUNT; look++) {
            for (int facing = 0; facing < 6; facing++) {
                for (float[] size : sizes) {
                    PictureSettings s = new PictureSettings();
                    s.mode = look;
                    s.facing = facing;
                    s.rotation = facing % 4;
                    s.width = size[0];
                    s.height = size[1];
                    s.moldingWidth = 3;
                    Recorder r = new Recorder();
                    r.draw(s, null, 0, SKIN, SKIN);
                    String what = "look " + look + " facing " + facing + " size " + size[0] + "x" + size[1];
                    assertFalse(r.vertices.isEmpty(), what);
                    assertEquals(0, r.vertices.size() % 4, what + ": quads only");
                    assertEquals(1, r.finished, what);
                    double[] box = CanvasGeometry.canvasBounds(s);
                    double[] o = CanvasGeometry.origin(s);
                    for (double[] v : r.vertices) {
                        for (int i = 0; i < 3; i++) {
                            assertTrue(o[i] + v[i] >= box[i] - 1e-6 && o[i] + v[i] <= box[i + 3] + 1e-6, what + ": vertex outside the hitbox");
                        }
                        assertTrue(v[3] >= 0.25 - 1e-9 && v[3] <= 0.5 + 1e-9 && v[4] >= 0.25 - 1e-9 && v[4] <= 0.5 + 1e-9,
                            what + ": texture outside the sprite");
                    }
                }
            }
        }
    }

    @Test
    void canvasIsTintedAndGlowLightsIt() {
        PictureSettings s = new PictureSettings();
        s.mode = PictureSettings.MODE_PANEL;
        Recorder r = new Recorder();
        r.draw(s, null, 12, SKIN, SKIN);
        assertEquals(PictureMesh.ATLAS, (int) r.kinds.get(0));
        double[] first = r.vertices.get(0);
        assertEquals(0x80 / 255f, first[5], 1e-6, "tint of the material");
        for (int light : r.lights) {
            assertEquals(7 << 20, light & 0xFFFF0000, "sky light is kept");
            assertEquals(12 << 4, light & 0xFFFF, "block light is raised to the glow");
        }
    }

    @Test
    void graffitiWithoutPictureShowsPlainCanvasOnly() {
        PictureSettings s = new PictureSettings();
        s.mode = PictureSettings.MODE_DECAL;
        Recorder r = new Recorder();
        r.draw(s, null, 0, SKIN, SKIN);
        assertEquals(4, r.vertices.size(), "one placeholder quad, no edges");
    }
}
