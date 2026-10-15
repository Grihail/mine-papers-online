package com.grihail.minepapers.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.grihail.minepapers.network.SharedImage;
import com.grihail.minepapers.picture.PictureSettings;

/** Выкладывание картинок на сервер и их раздача — с поддельным миром. */
class ImageStoreTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final Object WORLD = "overworld";

    @TempDir
    Path worldDir;

    private final PictureSettings frame = new PictureSettings();
    private byte[] shared;
    private final List<byte[]> needs = new ArrayList<byte[]>();
    private final List<int[]> chunks = new ArrayList<int[]>();
    private final List<byte[]> chunkData = new ArrayList<byte[]>();

    @BeforeEach
    void install() {
        frame.localPath = "cats";
        ImageStore.reset();
        ImageStore.install(new ServerHost() {
            @Override
            public File worldDir() {
                return worldDir.toFile();
            }

            @Override
            public PictureSettings frame(Object world, int target) {
                return WORLD.equals(world) && target == 1 ? frame : null;
            }

            @Override
            public void shareFiles(Object world, int target, byte[] files) {
                shared = files;
            }

            @Override
            public void sendNeed(Object player, byte[] hashes) {
                needs.add(hashes);
            }

            @Override
            public void sendChunk(Object player, byte[] hash, int total, int offset, byte[] data) {
                chunks.add(new int[] { total, offset });
                chunkData.add(data);
            }
        });
    }

    @AfterEach
    void reset() {
        ImageStore.reset();
    }

    private static byte[] image(int size, int seed) {
        byte[] raw = new byte[size];
        for (int i = 0; i < size; i++) raw[i] = (byte) (i * 31 + seed);
        return SharedImage.pack(10, 20, raw);
    }

    private static void upload(byte[] blob) {
        byte[] hash = SharedImage.sha1(blob);
        for (int at = 0; at < blob.length; at += SharedImage.UPLOAD_CHUNK) {
            byte[] part = Arrays.copyOfRange(blob, at, Math.min(blob.length, at + SharedImage.UPLOAD_CHUNK));
            ImageStore.upload(PLAYER, "Player", hash, blob.length, at, part);
        }
    }

    @Test
    void offeredImagesAreUploadedThenShared() {
        byte[] a = image(70_000, 1), b = image(100, 2);
        byte[] hashes = concat(SharedImage.sha1(a), SharedImage.sha1(b));
        ImageStore.offer(PLAYER, "p", WORLD, 1, "local:cats", hashes);
        assertEquals(1, needs.size());
        assertArrayEquals(hashes, needs.get(0), "server asks for both");
        assertNull(shared);
        upload(a);
        assertNull(shared, "one is still missing");
        upload(b);
        assertArrayEquals(hashes, shared);

        // Второй раз те же картинки уже на сервере: сразу в рамку, ничего не просим.
        shared = null;
        ImageStore.offer(PLAYER, "p", WORLD, 1, "local:cats", hashes);
        assertEquals(1, needs.size());
        assertArrayEquals(hashes, shared);
    }

    @Test
    void foreignOrBrokenUploadsAreIgnored() {
        byte[] a = image(500, 3);
        upload(a); // не просили
        ImageStore.offer(PLAYER, "p", WORLD, 1, "local:cats", SharedImage.sha1(a));
        assertNull(shared, "unrequested upload is not stored");

        byte[] broken = a.clone();
        broken[100] ^= 1;
        ImageStore.upload(PLAYER, "Player", SharedImage.sha1(a), broken.length, 0, broken);
        assertNull(shared, "corrupted image is rejected");
        upload(a);
        assertNull(shared, "after a failed transfer the image must be offered again");
    }

    @Test
    void offerForChangedSourceIsDropped() {
        byte[] a = image(500, 4);
        ImageStore.offer(PLAYER, "p", WORLD, 1, "local:dogs", SharedImage.sha1(a));
        assertTrue(needs.isEmpty(), "picture shows another source");
        ImageStore.offer(PLAYER, "p", WORLD, 1, "local:cats", SharedImage.sha1(a));
        frame.localPath = "dogs"; // перенастроили, пока грузилось
        upload(a);
        assertNull(shared);
    }

    @Test
    void requestedImagesAreSentInChunks() {
        byte[] a = image(100_000, 5);
        ImageStore.offer(PLAYER, "p", WORLD, 1, "local:cats", SharedImage.sha1(a));
        upload(a);
        UUID reader = UUID.randomUUID();
        byte[] missing = new byte[SharedImage.HASH_BYTES];
        ImageStore.request(reader, "r", concat(SharedImage.sha1(a), missing));
        for (int i = 0; i < 10; i++) ImageStore.tick();
        byte[] got = new byte[a.length];
        boolean notFound = false;
        for (int i = 0; i < chunks.size(); i++) {
            int[] c = chunks.get(i);
            if (c[0] < 0) {
                notFound = true;
                continue;
            }
            System.arraycopy(chunkData.get(i), 0, got, c[1], chunkData.get(i).length);
            assertTrue(chunkData.get(i).length <= SharedImage.DOWNLOAD_CHUNK);
        }
        assertArrayEquals(a, got);
        assertTrue(notFound, "unknown image is answered with total = -1");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
