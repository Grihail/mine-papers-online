package com.grihail.minepapers.client;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.grihail.minepapers.network.SharedImage;

/** Подготовка файла к выкладыванию на сервер с разным лимитом веса. */
class ImageSyncTest {

    private static final int MB = 1024 * 1024;

    @TempDir
    Path dir;

    @AfterEach
    void forgetLimit() {
        ImageSync.setServerImageMb(0);
    }

    private static BufferedImage noise(int size, int type, long seed) {
        BufferedImage img = new BufferedImage(size, size, type);
        Random random = new Random(seed);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) img.setRGB(x, y, random.nextInt(0x1000000));
        }
        return img;
    }

    private File gif(int frames) throws IOException {
        File file = dir.resolve("animated.gif").toFile();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames; i++) {
                writer.writeToSequence(new IIOImage(noise(1500, BufferedImage.TYPE_BYTE_INDEXED, i), null, null), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return file;
    }

    @Test
    void stillPictureIsShrunkToFitTheLimit() throws IOException {
        File file = dir.resolve("noise.png").toFile();
        ImageIO.write(noise(2048, BufferedImage.TYPE_INT_RGB, 1), "png", file);
        ImageSync.setServerImageMb(1);
        byte[] blob = ImageSync.encode(file);
        assertTrue(blob.length <= MB, "weighs " + blob.length);
        assertArrayEquals(new int[] { 2048, 2048 }, SharedImage.readHeader(blob), "original size is kept for fitting");
    }

    @Test
    void animationIsKeptWhileItFitsTheLimit() throws IOException {
        File file = gif(2);
        assertTrue(file.length() > MB, "the test needs a GIF heavier than the low limit");

        ImageSync.setServerImageMb(16);
        assertEquals(file.length() + SharedImage.HEADER_BYTES, ImageSync.encode(file).length, "sent as is");

        ImageSync.setServerImageMb(1);
        byte[] still = ImageSync.encode(file);
        assertTrue(still.length <= MB, "weighs " + still.length);
    }
}
