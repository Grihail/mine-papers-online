package com.grihail.minepapers;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

/** Настройки мода: config/minepapers.json — один файл и формат для всех версий игры. Читается при запуске мода; нет файла — он создаётся с настройками по умолчанию и пояснениями (JSON читается с комментариями //). Действуют на сервере (клиент узнаёт нужное от сервера), кроме hideNsfw и titleNeedsSneak — они только для своего клиента. */
public final class ModConfig {

    private static final Logger LOG = LogManager.getLogger("minepapers");
    public static final String FILE_NAME = "minepapers.json";

    /** Стопка картин в руке меняет материалы целиком, по блоку на картину (см. picture.MaterialStack). false — материалы меняются только у одной картины в руке: так игроки не наделают много настроенных картин разом. */
    public static boolean stackMaterials = true;

    /** Только на этом клиенте: картины с пометкой NSFW не показываются — ни картинка в мире, ни миниатюра на иконке, и их картинки не скачиваются. Остаётся пустой холст. Действует с запуска игры. */
    public static boolean hideNsfw = false;

    /** Только на этом клиенте: заголовок и ник подписавшего над картиной видны, только если смотреть на неё с зажатым Shift (присев). false — сразу, как только навёлся. */
    public static boolean titleNeedsSneak = true;

    private ModConfig() {}

    /** Скрыть ли картинку картины с такой пометкой на этом клиенте. */
    public static boolean hides(boolean nsfw) {
        return nsfw && hideNsfw;
    }

    /** @param configDir папка config игры или сервера */
    public static void load(File configDir) {
        File file = new File(configDir, FILE_NAME);
        if (!file.isFile()) {
            save(file);
            return;
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(true); // комментарии // в файле
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) throw new IllegalStateException("not a JSON object");
            JsonObject json = root.getAsJsonObject();
            if (json.has("stackMaterials")) stackMaterials = json.get("stackMaterials").getAsBoolean();
            if (json.has("hideNsfw")) hideNsfw = json.get("hideNsfw").getAsBoolean();
            if (json.has("titleNeedsSneak")) titleNeedsSneak = json.get("titleNeedsSneak").getAsBoolean();
            // Файл прежней версии — дописываем новые настройки (со значениями по умолчанию).
            if (!json.has("hideNsfw") || !json.has("titleNeedsSneak")) save(file);
        } catch (Exception e) {
            LOG.warn("Не удалось прочитать " + file + ", действуют настройки по умолчанию: " + e);
        }
    }

    /** Файл с настройками по умолчанию. Пояснения — по-английски: файл читают админы серверов со всего мира. */
    private static void save(File file) {
        String text = "{\n"
            + "  // true: a stack of pictures held in hand changes its materials (frame, canvas, glow) all at once,\n"
            + "  // one block per picture. false: materials can be changed only for a single picture in hand.\n"
            + "  \"stackMaterials\": " + stackMaterials + ",\n"
            + "\n"
            + "  // Client only: true hides pictures marked as NSFW (18+). Instead of the picture you see an empty canvas,\n"
            + "  // and its images are not downloaded. Takes effect after restarting the game.\n"
            + "  \"hideNsfw\": " + hideNsfw + ",\n"
            + "\n"
            + "  // Client only: true shows the picture title and signer only while looking at it with Shift held (sneaking).\n"
            + "  // false shows them as soon as you look at the picture.\n"
            + "  \"titleNeedsSneak\": " + titleNeedsSneak + "\n"
            + "}\n";
        try {
            File dir = file.getParentFile();
            if (dir != null) dir.mkdirs();
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("Не удалось создать " + file, e);
        }
    }
}
