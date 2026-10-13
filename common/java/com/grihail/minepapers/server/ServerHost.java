package com.grihail.minepapers.server;

import java.io.File;

import com.grihail.minepapers.picture.PictureSettings;

/** Всё, что общему серверному коду (ImageStore) нужно от версии игры. Игрок и мир передаются как Object: общий код только хранит их и отдаёт обратно версии. Картина — номер из PictureTarget; у картины-предмета вместо мира — UUID игрока, в чьём инвентаре она лежит. */
public abstract class ServerHost {

    /** Папка текущего мира (null, если мир не загружен). */
    public abstract File worldDir();

    /** Настройки картины target в мире world (null, если её нет или она сломана). Только для чтения: менять их — через shareFiles. */
    public abstract PictureSettings frame(Object world, int target);

    /** Картинки выложены: записать их список в картину target и разослать игрокам. */
    public abstract void shareFiles(Object world, int target, byte[] files);

    /** Пакет "пришли эти картинки" игроку player. */
    public abstract void sendNeed(Object player, byte[] hashes);

    /** Кусок картинки игроку player: total < 0 — картинки на сервере нет. */
    public abstract void sendChunk(Object player, byte[] hash, int total, int offset, byte[] data);
}
