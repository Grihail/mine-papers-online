package com.grihail.minepapers.client;

import java.util.function.Supplier;

import com.grihail.minepapers.MinePapers;

import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.ItemStack;

/** Клиентская часть мода без движка: движок (MinePapersNeoForgeClient, MinePapersFabricClient) вызывает init при запуске клиента и передаёт сюда события. */
public final class ModClient {

    /** Управление картинами: ломание, клики, обводка. */
    public static final PictureControls CONTROLS = new PictureControls();

    /** Цвета предметов (трава, листва, кожа): в ванили у Minecraft нет для них метода — его даёт движок. */
    public static Supplier<ItemColors> itemColors;

    private static int tickCounter;

    private ModClient() {}

    public static void init() {
        MinePapers.proxy = new ClientProxy();
        ClientHost.install(new MinePapersClientHost());
    }

    /** Конец каждого клиентского тика. */
    public static void onClientTick() {
        ImageSync.tick();
        if (++tickCounter % 40 == 0) PictureManager.tick();
        CONTROLS.onClientTick();
    }

    /** Игрок вышел из мира или отключился от сервера. */
    public static void onDisconnect() {
        ImageSync.onDisconnect();
    }

    /** Свойство модели предмета minepapers:configured: 1 — картину настраивали (рисует RenderPictureItem), 0 — обычная иконка. */
    public static float configured(ItemStack stack) {
        return stack.has(MinePapers.FRAME_SETTINGS.get()) ? 1f : 0f;
    }
}
