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
    @Override public int getInt(String key) { return tag.getInt(key); }
    @Override public float getFloat(String key) { return tag.getFloat(key); }
    @Override public String getString(String key) { return tag.getString(key); }
    @Override public byte[] getByteArray(String key) { return tag.getByteArray(key); }
    @Override public UUID getUuid(String key) { return tag.hasUUID(key) ? tag.getUUID(key) : null; }

    @Override public void putInt(String key, int value) { tag.putInt(key, value); }
    @Override public void putFloat(String key, float value) { tag.putFloat(key, value); }
    @Override public void putString(String key, String value) { tag.putString(key, value); }
    @Override public void putByteArray(String key, byte[] value) { tag.putByteArray(key, value); }
    @Override public void putUuid(String key, UUID value) { tag.putUUID(key, value); }
}
