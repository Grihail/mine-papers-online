package com.grihail.minepapers.client;

import java.io.File;

/** Всё, что общему клиентскому коду (PictureManager, ImageSync, ImageStylizer) нужно от версии игры. Версия ставит свою реализацию через install() при запуске клиента. */
public abstract class ClientHost {

    private static volatile ClientHost current;

    public static void install(ClientHost host) {
        current = host;
    }

    static ClientHost get() {
        return current;
    }

    /** Папка игры (.minecraft): в ней кэш картинок с сервера. */
    public abstract File gameDir();

    /** Папка с локальными картинками: config/minepapers. */
    public abstract File picturesDir();

    /** "MinePapersOnline/версия" — для запросов в интернет. */
    public abstract String userAgent();

    /** Базовые цвета карт (RGB) без "прозрачного" цвета воздуха. В разных версиях игры их разное число. */
    public abstract int[] mapColors();

    /** Игрок в мире и соединение с сервером есть. */
    public abstract boolean isConnected();

    /** Сервер запущен в этом клиенте (одиночная игра или открытая в LAN). */
    public abstract boolean isSingleplayerHost();

    /** Загрузить кадр в видеопамять. @param argb пиксели ARGB, строки сверху вниз @return ссылка на текстуру — у каждой версии своя (её понимает только рендер версии) */
    public abstract Object uploadTexture(int[] argb, int width, int height);

    public abstract void releaseTexture(Object texture);

    /** Пакет "у меня есть эти картинки для рамки entityId". */
    public abstract void sendOffer(int entityId, String origin, byte[] hashes);

    /** Пакет "пришли мне эти картинки". */
    public abstract void sendRequest(byte[] hashes);

    /** Кусок выкладываемой картинки: data — байты с offset по offset + data.length из total. */
    public abstract void sendUpload(byte[] hash, int total, int offset, byte[] data);
}
