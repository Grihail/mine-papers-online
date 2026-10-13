package com.grihail.minepapers.client;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Текстуры скина игрока прямо от Mojang — когда ванильный запрос профиля их не дал. Лаунчеры с офлайн-аккаунтами (Prism с authlib-injector) пускают запросы к Mojang через свой прокси, который чужие профили не отдаёт; на офлайн-сервере к тому же UUID выдуман из ника. Поэтому: сначала по UUID, не нашлось — настоящий UUID по нику и уже по нему. Только из фонового потока. */
public final class MojangProfiles {

    /** Адреса собираются при запуске: authlib-injector переписывает в загружаемых классах все строки с адресами Mojang на свой прокси — и в этом классе тоже, если адрес записан целиком. */
    private static final String MOJANG = String.join(".", "mojang", "com");
    private static final String PROFILE = "https://" + String.join(".", "sessionserver", MOJANG) + "/session/minecraft/profile/";
    private static final String BY_NAME = "https://" + String.join(".", "api", MOJANG) + "/users/profiles/minecraft/";

    private MojangProfiles() {}

    /** Свойство "textures" профиля: {значение, подпись}; null — не нашлось. */
    public static String[] textures(UUID id, String name) {
        String[] found = texturesOf(id.toString().replace("-", ""));
        if (found != null || name == null || name.isEmpty()) return found;
        String real = idByName(name);
        return real != null ? texturesOf(real) : null;
    }

    private static String[] texturesOf(String id) {
        JsonElement json = get(PROFILE + id + "?unsigned=false");
        if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("properties")) return null;
        JsonArray properties = json.getAsJsonObject().getAsJsonArray("properties");
        for (JsonElement e : properties) {
            JsonObject p = e.getAsJsonObject();
            if (!"textures".equals(p.get("name").getAsString())) continue;
            return new String[] { p.get("value").getAsString(), p.has("signature") ? p.get("signature").getAsString() : null };
        }
        return null;
    }

    private static String idByName(String name) {
        try {
            JsonElement json = get(BY_NAME + URLEncoder.encode(name, "UTF-8"));
            return json != null && json.isJsonObject() && json.getAsJsonObject().has("id") ? json.getAsJsonObject().get("id").getAsString() : null;
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
    }

    /** JSON по адресу; null — нет ответа, ошибка или пусто (204 — такого игрока нет). */
    @SuppressWarnings("deprecation")
    private static JsonElement get(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() != 200) return null;
            try (Reader in = new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) {
                return new JsonParser().parse(in);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
