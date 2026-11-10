package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Рецепт новой (пустой) рамки из config/minepapers.json (ключ "recipe"), один формат для всех версий. Без формы — "ingredients": список предметов в любом порядке. С формой — "pattern": от 1 до 3 строк по 1-3 символа (пробел — пустая клетка) и "key": символ -> предмет; форму можно сдвигать по сетке, как у ванильных рецептов.
 * Предмет — id из реестра, в том числе из других модов ("minecraft:painting", "modid:item"); в 1.7.10 и 1.12.2 к нему можно дописать ":meta" (без него — любые метаданные).
 * Версия либо регистрирует по нему обычный рецепт (1.7.10, 1.12.2 — виден в NEI и JEI), либо проверяет им сетку сама (matches).
 */
public final class FrameRecipe {

    /** Предметы без формы (null — рецепт с формой). */
    public final List<String> ingredients;
    /** Строки формы одинаковой ширины (короткие дополнены пробелами) и предметы символов; null — рецепт без формы. */
    public final String[] pattern;
    public final Map<Character, String> key;

    /** По умолчанию: рамка, картина, книга с пером и стеклянная панель — без формы. */
    public static final FrameRecipe DEFAULT = new FrameRecipe(Arrays.asList("minecraft:item_frame", "minecraft:painting",
        "minecraft:writable_book", "minecraft:glass_pane"), null, null);

    private FrameRecipe(List<String> ingredients, String[] pattern, Map<Character, String> key) {
        this.ingredients = ingredients;
        this.pattern = pattern;
        this.key = key;
    }

    public boolean shaped() {
        return pattern != null;
    }

    public int width() {
        return pattern[0].length();
    }

    public int height() {
        return pattern.length;
    }

    /** Предмет клетки формы (x, y) или null — пусто. */
    public String at(int x, int y) {
        char c = pattern[y].charAt(x);
        return c == ' ' ? null : key.get(c);
    }

    /** Рецепт из JSON конфига; неверный — исключение с понятным текстом (тогда остаётся рецепт по умолчанию). */
    public static FrameRecipe fromJson(JsonElement json) {
        JsonObject o = json.getAsJsonObject();
        if (o.has("ingredients")) {
            List<String> items = new ArrayList<String>();
            for (JsonElement e : o.getAsJsonArray("ingredients")) items.add(e.getAsString().trim());
            if (items.isEmpty() || items.size() > 9) throw new IllegalArgumentException("recipe: 1 to 9 ingredients");
            return new FrameRecipe(items, null, null);
        }
        if (!o.has("pattern") || !o.has("key")) throw new IllegalArgumentException("recipe: needs \"ingredients\" or \"pattern\" and \"key\"");
        JsonArray rows = o.getAsJsonArray("pattern");
        if (rows.size() < 1 || rows.size() > 3) throw new IllegalArgumentException("recipe: pattern needs 1 to 3 rows");
        int width = 0;
        for (JsonElement r : rows) width = Math.max(width, r.getAsString().length());
        if (width < 1 || width > 3) throw new IllegalArgumentException("recipe: pattern rows need 1 to 3 characters");
        String[] pattern = new String[rows.size()];
        for (int i = 0; i < pattern.length; i++) {
            StringBuilder row = new StringBuilder(rows.get(i).getAsString());
            while (row.length() < width) row.append(' ');
            pattern[i] = row.toString();
        }
        Map<Character, String> key = new LinkedHashMap<Character, String>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("key").entrySet()) {
            if (e.getKey().length() != 1 || e.getKey().equals(" ")) throw new IllegalArgumentException("recipe: key must be single characters");
            key.put(e.getKey().charAt(0), e.getValue().getAsString().trim());
        }
        for (String row : pattern) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && !key.containsKey(c)) throw new IllegalArgumentException("recipe: no key for '" + c + "'");
            }
        }
        return new FrameRecipe(null, pattern, key);
    }

    /** Рецепт строками JSON для файла конфига (отступ — indent). */
    public String toJson(String indent) {
        StringBuilder b = new StringBuilder("{\n");
        if (!shaped()) {
            b.append(indent).append("  \"ingredients\": [");
            for (int i = 0; i < ingredients.size(); i++) b.append(i > 0 ? ", " : "").append('"').append(ingredients.get(i)).append('"');
            b.append("]\n");
        } else {
            b.append(indent).append("  \"pattern\": [");
            for (int i = 0; i < pattern.length; i++) b.append(i > 0 ? ", " : "").append('"').append(pattern[i]).append('"');
            b.append("],\n").append(indent).append("  \"key\": {");
            int i = 0;
            for (Map.Entry<Character, String> e : key.entrySet()) {
                b.append(i++ > 0 ? ", " : " ").append('"').append(e.getKey()).append("\": \"").append(e.getValue()).append('"');
            }
            b.append(" }\n");
        }
        return b.append(indent).append('}').toString();
    }

    /** Подходит ли сетка крафта: ids — предметы сетки по строкам (null — пусто), width x height — её размер. Предмет рецепта "id:meta" сравнивается без метаданных (их проверяет только регистрация в старых версиях). */
    public boolean matches(String[] ids, int width, int height) {
        if (!shaped()) {
            List<String> left = new ArrayList<String>(ingredients);
            for (String id : ids) {
                if (id == null) continue;
                int found = -1;
                for (int i = 0; i < left.size() && found < 0; i++) {
                    if (same(left.get(i), id)) found = i;
                }
                if (found < 0) return false;
                left.remove(found);
            }
            return left.isEmpty();
        }
        // С формой: пробуем каждый сдвиг формы по сетке; всё вне формы — пусто.
        for (int dx = 0; dx + width() <= width; dx++) {
            for (int dy = 0; dy + height() <= height; dy++) {
                if (fits(ids, width, height, dx, dy)) return true;
            }
        }
        return false;
    }

    private boolean fits(String[] ids, int width, int height, int dx, int dy) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean inside = x >= dx && x < dx + width() && y >= dy && y < dy + height();
                String need = inside ? at(x - dx, y - dy) : null;
                String has = ids[y * width + x];
                if (need == null ? has != null : has == null || !same(need, has)) return false;
            }
        }
        return true;
    }

    /** Предмет рецепта (возможно, с ":meta") — тот же, что id из сетки. */
    private static boolean same(String need, String id) {
        return need.equals(id) || need.startsWith(id + ":") && need.indexOf(':', id.length() + 1) < 0;
    }

    /** Id и метаданные предмета рецепта: "mod:item:3" -> {"mod:item", "3"}; без метаданных — {"mod:item", null}. */
    public static String[] split(String item) {
        int first = item.indexOf(':'), last = item.lastIndexOf(':');
        if (last > first && first >= 0) {
            String meta = item.substring(last + 1);
            if (meta.matches("\\d+")) return new String[] { item.substring(0, last), meta };
        }
        return new String[] { item, null };
    }
}
