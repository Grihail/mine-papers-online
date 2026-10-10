package com.grihail.minepapers.network;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Картинка, выложенная на сервер: хранится и раздаётся игрокам по SHA-1 своего содержимого,
 * поэтому одинаковые файлы из разных рамок и от разных игроков хранятся и скачиваются один раз.
 * Формат: "MPI1", исходные ширина и высота (перед отправкой большие картинки уменьшаются), затем сам файл картинки.
 */
public final class SharedImage {

    public static final int HASH_BYTES = 20;
    public static final int HEX_CHARS = HASH_BYTES * 2;
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int HEADER_BYTES = 12;
    private static final int MAGIC = 0x4D504931;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SharedImage() {}

    public static byte[] pack(int origW, int origH, byte[] image) {
        byte[] blob = new byte[HEADER_BYTES + image.length];
        putInt(blob, 0, MAGIC);
        putInt(blob, 4, origW);
        putInt(blob, 8, origH);
        System.arraycopy(image, 0, blob, HEADER_BYTES, image.length);
        return blob;
    }

    /** {ширина, высота} исходной картинки или null, если это не наш формат. */
    public static int[] readHeader(byte[] blob) {
        if (blob.length < HEADER_BYTES || getInt(blob, 0) != MAGIC) return null;
        return new int[] { getInt(blob, 4), getInt(blob, 8) };
    }

    public static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hex(byte[] bytes) {
        return hex(bytes, 0, bytes.length);
    }

    public static String hex(byte[] bytes, int offset, int length) {
        char[] out = new char[length * 2];
        for (int i = 0; i < length; i++) {
            int b = bytes[offset + i] & 0xFF;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0xF];
        }
        return new String(out);
    }

    public static byte[] unhex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }

    private static int getInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }
}
