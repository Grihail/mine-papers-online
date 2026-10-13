package com.grihail.minepapers.picture;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

/** NBTTagCompound для общего кода (PictureSettings.Tag). UUID хранится строкой. */
public final class NbtTag implements PictureSettings.Tag {

    public final NBTTagCompound tag;

    public NbtTag(NBTTagCompound tag) {
        this.tag = tag;
    }

    @Override public boolean contains(String key) { return tag.hasKey(key); }
    @Override public byte getByte(String key) { return tag.getByte(key); }
    @Override public short getShort(String key) { return tag.getShort(key); }
    @Override public int getInt(String key) { return tag.getInteger(key); }
    @Override public float getFloat(String key) { return tag.getFloat(key); }
    @Override public boolean getBoolean(String key) { return tag.getBoolean(key); }
    @Override public String getString(String key) { return tag.getString(key); }
    @Override public byte[] getByteArray(String key) { return tag.getByteArray(key); }
    @Override public UUID getUuid(String key) { return PictureSettings.parseUuid(tag.getString(key)); }

    @Override public void putByte(String key, byte value) { tag.setByte(key, value); }
    @Override public void putShort(String key, short value) { tag.setShort(key, value); }
    @Override public void putInt(String key, int value) { tag.setInteger(key, value); }
    @Override public void putFloat(String key, float value) { tag.setFloat(key, value); }
    @Override public void putBoolean(String key, boolean value) { tag.setBoolean(key, value); }
    @Override public void putString(String key, String value) { tag.setString(key, value); }
    @Override public void putByteArray(String key, byte[] value) { tag.setByteArray(key, value); }
    @Override public void putUuid(String key, UUID value) { tag.setString(key, value.toString()); }
}
