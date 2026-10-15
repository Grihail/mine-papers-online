package com.grihail.minepapers.picture;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Тег настроек в памяти, без классов версии: чтобы сравнить настройки или прогнать их через ограничения readSettings. Равны два тега с одинаковыми ключами и значениями (массивы — по содержимому). */
public final class MapTag implements PictureSettings.Tag {

    private final Map<String, Object> values = new LinkedHashMap<String, Object>();

    @Override public boolean contains(String key) { return values.containsKey(key); }
    @Override public int getInt(String key) { Object v = values.get(key); return v instanceof Number ? ((Number) v).intValue() : 0; }
    @Override public float getFloat(String key) { Object v = values.get(key); return v instanceof Number ? ((Number) v).floatValue() : 0f; }
    @Override public String getString(String key) { Object v = values.get(key); return v instanceof String ? (String) v : ""; }
    @Override public byte[] getByteArray(String key) { Object v = values.get(key); return v instanceof byte[] ? (byte[]) v : new byte[0]; }
    @Override public UUID getUuid(String key) { Object v = values.get(key); return v instanceof UUID ? (UUID) v : null; }

    @Override public void putInt(String key, int value) { values.put(key, value); }
    @Override public void putFloat(String key, float value) { values.put(key, value); }
    @Override public void putString(String key, String value) { values.put(key, value); }
    @Override public void putByteArray(String key, byte[] value) { values.put(key, value.clone()); }
    @Override public void putUuid(String key, UUID value) { values.put(key, value); }

    /** Настройки картины (без материалов) в новом теге. */
    public static MapTag of(PictureSettings settings) {
        MapTag tag = new MapTag();
        settings.writeSettings(tag);
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MapTag)) return false;
        Map<String, Object> other = ((MapTag) o).values;
        if (other.size() != values.size()) return false;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object a = e.getValue(), b = other.get(e.getKey());
            if (a instanceof byte[] ? !(b instanceof byte[] && Arrays.equals((byte[]) a, (byte[]) b)) : !a.equals(b)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return values.keySet().hashCode();
    }
}
