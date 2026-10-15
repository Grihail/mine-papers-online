package com.grihail.minepapers.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Ресемплинг картинки в текстуру: пиксель-арт не размывается. */
class ImageStylizerTest {

    private static final int B = 0xFF000000, W = 0xFFFFFFFF;

    /** Та же сетка со сдвигом на полпикселя (вписывание обрезало край): с резкими пикселями — пиксели как есть, без них — сглажены. */
    @Test
    void shiftedSameSizeGridIsSharpOnlyWithSharpPixels() {
        int[] src = { B, W, B, W, B };
        assertArrayEquals(new int[] { W, B, W, B }, ImageStylizer.resample(src, 5, 1, 0.5, 0, 4.5, 1, 4, 1, true));
        assertEquals(0xFF7F7F7F, ImageStylizer.resample(src, 5, 1, 0.5, 0, 4.5, 1, 4, 1, false)[0]);
    }

    /** Настоящее уменьшение усредняет, а с резкими пикселями — берёт ближайший. */
    @Test
    void downscaleAveragesUnlessSharp() {
        int[] src = { B, W, B, W };
        int[] soft = ImageStylizer.resample(src, 4, 1, 0, 0, 4, 1, 2, 1, false);
        assertEquals(0xFF7F7F7F, soft[0]);
        int[] sharp = ImageStylizer.resample(src, 4, 1, 0, 0, 4, 1, 2, 1, true);
        for (int c : sharp) assertTrue(c == B || c == W, "no mixed colors: " + Integer.toHexString(c));
    }
}
