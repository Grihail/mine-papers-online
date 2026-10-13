package com.grihail.minepapers.picture;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/** CompoundTag для общего кода (PictureSettings.Tag). */
public final class NbtTag implements PictureSettings.Tag {

    public final CompoundTag tag;

    public NbtTag(CompoundTag tag) {
        this.tag = tag;
    }

    @Override public boolean contains(String key) { return tag.contains(key); }
    @Override public byte getByte(String key) { return tag.getByte(key); }
    @Override public short getShort(String key) { return tag.getShort(key); }
    @Override public int getInt(String key) { return tag.getInt(key); }
    @Override public float getFloat(String key) { return tag.getFloat(key); }
    @Override public boolean getBoolean(String key) { return tag.getBoolean(key); }
    @Override public String getString(String key) { return tag.getString(key); }
    @Override public byte[] getByteArray(String key) { return tag.getByteArray(key); }
    @Override public UUID getUuid(String key) { return tag.hasUUID(key) ? tag.getUUID(key) : null; }

    @Override public void putByte(String key, byte value) { tag.putByte(key, value); }
    @Override public void putShort(String key, short value) { tag.putShort(key, value); }
    @Override public void putInt(String key, int value) { tag.putInt(key, value); }
    @Override public void putFloat(String key, float value) { tag.putFloat(key, value); }
    @Override public void putBoolean(String key, boolean value) { tag.putBoolean(key, value); }
    @Override public void putString(String key, String value) { tag.putString(key, value); }
    @Override public void putByteArray(String key, byte[] value) { tag.putByteArray(key, value); }
    @Override public void putUuid(String key, UUID value) { tag.putUUID(key, value); }
}
