package com.grihail.minepapers.picture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Настройки картины в виде JSON — одинакового для всех версий мода: окно копирует его в буфер обмена, его можно поправить в любом редакторе и вставить в другую картину. Значения записаны словами (look: "graffiti"), числа — как в полях окна. Чего в конфиге нет (поле не указано, null), то при вставке не меняется; у материалов явный null значит "обычный" — вынуть предмет из слота. */
public final class PictureConfig {

    /** Названия значений — по порядку констант PictureFrame (SOURCE_*, FIT_*, MODE_*, STYLE_* прежних версий), материалов — по порядку PictureFrame.materials(). */
    public static final String[] SOURCES = { "config", "yandex", "folder", "url" };
    public static final String[] FITS = { "fill", "contain", "cover", "none", "scale-down" };
    public static final String[] LOOKS = { "panel", "frame", "graffiti", "plate" };
    /** Рамка и холст по отдельности (FRAME_*, CANVAS_*): так вид пишут версии, где их выбирают отдельно. Рядом с ними пишется и ближайший прежний look — для версий, где выбирают только его; при вставке frame и canvas главнее look. */
    public static final String[] FRAMES = { "none", "edges", "molding" };
    public static final String[] CANVASES = { "none", "block", "picture" };
    public static final String[] STYLES = { "original", "map", "map-dither", "map-soft-dither" };
    public static final String[] DITHER_MODES = { "diffusion", "ordered" };
    public static final String[] MATERIALS = { "frame", "canvas", "glow" };

    /** Версия игры, в которой конфиг скопирован: по ней понятно, в каких названиях записаны предметы (до 1.13 и после — разные, см. translate). */
    public String minecraft;
    public String title, description;
    /** Пометка NSFW (18+). */
    public Boolean nsfw;
    public Integer source;
    public String path;
    public Integer interval;
    public Float width, height;
    public Boolean sizePixels;
    public Float offsetRight, offsetUp;
    public Boolean offsetPixels;
    public Float padding;
    public Boolean paddingPixels;
    public Float zoom;
    public Boolean zoomPixels;
    public Integer fit, look, frame, canvas, style, pixelsPerBlock;
    /** Ширина багета в пикселях (у frame "molding"). */
    public Integer moldingWidth;
    /** Резкие пиксели (без полупрозрачности), палитра карт, число цветов и сила дизеринга в процентах. Рядом пишется ближайший старый style — для прежних версий; при вставке эти поля главнее style. */
    public Boolean sharpPixels, mapPalette;
    public Integer colors, dither, ditherMode;

    /** Предмет материала: id в реестре и (только для 1.7.10) метаданные; meta < 0 — любые. */
    public static final class Item {
        public final String id;
        public final int meta;

        public Item(String id, int meta) {
            this.id = id;
            this.meta = meta;
        }
    }

    /** Материалы по порядку MATERIALS: hasMaterial[i] = false — в конфиге нет; иначе materials[i] — предмет или null ("обычный"). */
    public final boolean[] hasMaterial = new boolean[MATERIALS.length];
    public final Item[] materials = new Item[MATERIALS.length];

    // ---------- В JSON ----------

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("mod", "minepapers");
        root.addProperty("format", 1);
        if (minecraft != null) root.addProperty("minecraft", minecraft);
        if (title != null) root.addProperty("title", title);
        if (description != null) root.addProperty("description", description);
        if (nsfw != null) root.addProperty("nsfw", nsfw);

        JsonObject src = new JsonObject();
        if (source != null) src.addProperty("type", name(SOURCES, source));
        if (path != null) src.addProperty("path", path);
        if (interval != null) src.addProperty("interval", interval);
        root.add("source", src);

        JsonObject size = new JsonObject();
        if (width != null) size.addProperty("width", number(width));
        if (height != null) size.addProperty("height", number(height));
        if (sizePixels != null) size.addProperty("pixels", sizePixels);
        root.add("size", size);

        JsonObject offset = new JsonObject();
        if (offsetRight != null) offset.addProperty("right", number(offsetRight));
        if (offsetUp != null) offset.addProperty("up", number(offsetUp));
        if (offsetPixels != null) offset.addProperty("pixels", offsetPixels);
        root.add("offset", offset);

        JsonObject pad = new JsonObject();
        if (padding != null) pad.addProperty("size", number(padding));
        if (paddingPixels != null) pad.addProperty("pixels", paddingPixels);
        root.add("padding", pad);

        JsonObject zoomObject = new JsonObject();
        if (zoom != null) zoomObject.addProperty("size", number(zoom));
        if (zoomPixels != null) zoomObject.addProperty("pixels", zoomPixels);
        root.add("zoom", zoomObject);

        if (fit != null) root.addProperty("fit", name(FITS, fit));
        if (look != null) root.addProperty("look", name(LOOKS, look));
        if (frame != null) root.addProperty("frame", name(FRAMES, frame));
        if (moldingWidth != null) root.addProperty("moldingWidth", moldingWidth);
        if (canvas != null) root.addProperty("canvas", name(CANVASES, canvas));
        if (style != null) root.addProperty("style", name(STYLES, style));
        if (sharpPixels != null) root.addProperty("sharpPixels", sharpPixels);
        if (mapPalette != null) root.addProperty("mapPalette", mapPalette);
        if (colors != null) root.addProperty("colors", colors);
        if (dither != null) root.addProperty("dither", dither);
        if (ditherMode != null) root.addProperty("ditherMode", name(DITHER_MODES, ditherMode));
        if (pixelsPerBlock != null) root.addProperty("pixelsPerBlock", pixelsPerBlock);

        JsonObject mats = new JsonObject();
        for (int i = 0; i < MATERIALS.length; i++) {
            if (!hasMaterial[i]) continue;
            Item item = materials[i];
            if (item == null) {
                mats.add(MATERIALS[i], JsonNull.INSTANCE);
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("item", item.id);
            if (item.meta > 0) o.addProperty("meta", item.meta);
            mats.add(MATERIALS[i], o);
        }
        root.add("materials", mats);
        return new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create().toJson(root);
    }

    private static String name(String[] names, int value) {
        return names[value];
    }

    /** Целые — без ".0", чтобы конфиг было удобно читать. */
    private static Number number(float v) {
        return v == Math.round(v) ? (Number) Math.round(v) : (Number) v;
    }

    // ---------- Из JSON ----------

    /** Разбирает конфиг; не JSON-объект — IllegalArgumentException. */
    @SuppressWarnings("deprecation")
    public static PictureConfig fromJson(String text) {
        PictureConfig c = new PictureConfig();
        try {
            JsonObject root = new JsonParser().parse(text).getAsJsonObject();
            c.minecraft = string(root, "minecraft");
            c.title = string(root, "title");
            c.description = string(root, "description");
            c.nsfw = bool(root, "nsfw");
            JsonObject src = object(root, "source");
            if (src != null) {
                c.source = value(src, "type", SOURCES);
                c.path = string(src, "path");
                c.interval = integer(src, "interval");
            }
            JsonObject size = object(root, "size");
            if (size != null) {
                c.width = decimal(size, "width");
                c.height = decimal(size, "height");
                c.sizePixels = bool(size, "pixels");
            }
            JsonObject offset = object(root, "offset");
            if (offset != null) {
                c.offsetRight = decimal(offset, "right");
                c.offsetUp = decimal(offset, "up");
                c.offsetPixels = bool(offset, "pixels");
            }
            JsonObject pad = object(root, "padding");
            if (pad != null) {
                c.padding = decimal(pad, "size");
                c.paddingPixels = bool(pad, "pixels");
            }
            JsonObject zoomObject = object(root, "zoom");
            if (zoomObject != null) {
                c.zoom = decimal(zoomObject, "size");
                c.zoomPixels = bool(zoomObject, "pixels");
            }
            c.fit = value(root, "fit", FITS);
            c.look = value(root, "look", LOOKS);
            c.frame = value(root, "frame", FRAMES);
            c.moldingWidth = integer(root, "moldingWidth");
            c.canvas = value(root, "canvas", CANVASES);
            c.style = value(root, "style", STYLES);
            c.sharpPixels = bool(root, "sharpPixels");
            c.mapPalette = bool(root, "mapPalette");
            c.colors = integer(root, "colors");
            c.dither = integer(root, "dither");
            c.ditherMode = value(root, "ditherMode", DITHER_MODES);
            c.pixelsPerBlock = integer(root, "pixelsPerBlock");

            JsonObject mats = object(root, "materials");
            if (mats != null) {
                for (int i = 0; i < MATERIALS.length; i++) {
                    if (!mats.has(MATERIALS[i])) continue;
                    c.hasMaterial[i] = true;
                    JsonElement e = mats.get(MATERIALS[i]);
                    if (e.isJsonPrimitive()) {
                        String id = e.getAsString().trim();
                        c.materials[i] = id.isEmpty() ? null : new Item(id, -1); // можно и короче: "frame": "minecraft:gold_block"
                    } else if (e.isJsonObject()) {
                        String id = string(e.getAsJsonObject(), "item");
                        Integer meta = integer(e.getAsJsonObject(), "meta");
                        c.materials[i] = id == null || id.trim().isEmpty() ? null : new Item(id.trim(), meta != null ? meta : -1);
                    }
                }
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e);
        }
        return c;
    }

    // ---------- Предметы между версиями ----------

    /** "Уплощение" 1.13: старое имя + метаданные -> имя в новых версиях и обратно (assets/minepapers/flattening.txt — из ванильного DataFixer). */
    private static Map<String, String> oldToNew, newToOld;

    private static synchronized void loadFlattening() {
        if (oldToNew != null) return;
        oldToNew = new HashMap<String, String>();
        newToOld = new HashMap<String, String>();
        InputStream in = PictureConfig.class.getResourceAsStream("/assets/minepapers/flattening.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] p = line.split(" ");
                if (p.length != 3) continue; // комментарии
                oldToNew.put(p[0] + "." + p[1], p[2]);
                if (!newToOld.containsKey(p[2])) newToOld.put(p[2], p[0] + "." + p[1]);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Названия предметов "после уплощения" — в версии 1.13 и новее. */
    public static boolean isFlattened(String version) {
        return Integer.parseInt(version.split("\\.")[1]) >= 13;
    }

    /** Предмет материала под эту версию игры (flattened — она 1.13 или новее). Конфиг из другой эпохи переводим по таблице; если версия конфига не указана (написан руками) — сначала имя как есть, не нашлось (exists) — по таблице. */
    public Item translate(Item item, boolean flattened, Predicate<String> exists) {
        if (item == null) return null;
        Boolean from = minecraft != null ? isFlattened(minecraft) : null;
        if (from != null && from == flattened || from == null && exists.test(item.id)) return item;
        loadFlattening();
        if (flattened) {
            String id = oldToNew.get(item.id + "." + Math.max(item.meta, 0));
            return id != null ? new Item(id, -1) : item;
        }
        String old = newToOld.get(item.id);
        if (old == null) return item;
        int dot = old.lastIndexOf('.');
        return new Item(old.substring(0, dot), Integer.parseInt(old.substring(dot + 1)));
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonElement present(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() ? e : null;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = present(o, key);
        return e != null ? e.getAsString() : null;
    }

    private static Integer integer(JsonObject o, String key) {
        JsonElement e = present(o, key);
        return e != null ? Math.round(e.getAsFloat()) : null;
    }

    private static Float decimal(JsonObject o, String key) {
        JsonElement e = present(o, key);
        return e != null ? e.getAsFloat() : null;
    }

    private static Boolean bool(JsonObject o, String key) {
        JsonElement e = present(o, key);
        return e != null ? e.getAsBoolean() : null;
    }

    /** Значение словом из names (или номером). */
    private static Integer value(JsonObject o, String key, String[] names) {
        JsonElement e = present(o, key);
        if (e == null) return null;
        String s = e.getAsString().trim();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(s)) return i;
        }
        int n = Math.round(Float.parseFloat(s));
        if (n < 0 || n >= names.length) throw new IllegalArgumentException(key + ": " + s);
        return n;
    }
}
