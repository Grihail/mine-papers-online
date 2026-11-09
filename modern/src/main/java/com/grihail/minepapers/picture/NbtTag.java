package com.grihail.minepapers.picture;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
//? if >=26.1 {
/*import net.minecraft.core.UUIDUtil;
*///?}

/** CompoundTag для общего кода (PictureSettings.Tag). С 26.1 чтение из тега возвращает Optional, здесь же - значения по умолчанию, как раньше. */
public final class NbtTag implements PictureSettings.Tag {

    public final CompoundTag tag;

    public NbtTag(CompoundTag tag) {
        this.tag = tag;
    }

    //? if >=26.1 {
    /*@Override public boolean contains(String key) { return tag.contains(key); }
    @Override public int getInt(String key) { return tag.getIntOr(key, 0); }
    @Override public float getFloat(String key) { return tag.getFloatOr(key, 0f); }
    @Override public String getString(String key) { return string(tag, key); }
    @Override public byte[] getByteArray(String key) { return tag.getByteArray(key).orElse(new byte[0]); }
    @Override public UUID getUuid(String key) { return tag.read(key, UUIDUtil.CODEC).orElse(null); }
    @Override public void putUuid(String key, UUID value) { tag.store(key, UUIDUtil.CODEC, value); }
    *///?} else {
    @Override public boolean contains(String key) { return tag.contains(key); }
    @Override public int getInt(String key) { return tag.getInt(key); }
    @Override public float getFloat(String key) { return tag.getFloat(key); }
    @Override public String getString(String key) { return tag.getString(key); }
    @Override public byte[] getByteArray(String key) { return tag.getByteArray(key); }
    @Override public UUID getUuid(String key) { return tag.hasUUID(key) ? tag.getUUID(key) : null; }
    @Override public void putUuid(String key, UUID value) { tag.putUUID(key, value); }
    //?}

    @Override public void putInt(String key, int value) { tag.putInt(key, value); }
    @Override public void putFloat(String key, float value) { tag.putFloat(key, value); }
    @Override public void putString(String key, String value) { tag.putString(key, value); }
    @Override public void putByteArray(String key, byte[] value) { tag.putByteArray(key, value); }

    /** Строка тега ("" - нет ключа). */
    public static String string(CompoundTag tag, String key) {
        //? if >=26.1 {
        /*return tag.getStringOr(key, "");
        *///?} else {
        return tag.getString(key);
        //?}
    }

    /** Вложенный тег (пустой - нет ключа). */
    public static CompoundTag compound(CompoundTag tag, String key) {
        //? if >=26.1 {
        /*return tag.getCompoundOrEmpty(key);
        *///?} else {
        return tag.getCompound(key);
        //?}
    }
}
