package com.grihail.minepapers.server;

import java.io.File;

import com.grihail.minepapers.picture.PictureSettings;

/** Всё, что общему серверному коду (ImageStore) нужно от версии игры. Игрок и мир передаются как Object: общий код только хранит их и отдаёт обратно версии. */
public abstract class ServerHost {

    /** Папка текущего мира (null, если мир не загружен). */
    public abstract File worldDir();

    /** Настройки живой картины entityId в мире world (null, если её нет или она сломана). */
    public abstract PictureSettings frame(Object world, int entityId);

    /** Настройки картины поменялись (выложены картинки) — разослать игрокам. */
    public abstract void settingsChanged(Object world, int entityId);

    /** Пакет "пришли эти картинки" игроку player. */
    public abstract void sendNeed(Object player, byte[] hashes);

    /** Кусок картинки игроку player: total < 0 — картинки на сервере нет. */
    public abstract void sendChunk(Object player, byte[] hash, int total, int offset, byte[] data);
}
