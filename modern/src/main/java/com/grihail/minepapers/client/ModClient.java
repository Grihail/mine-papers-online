package com.grihail.minepapers.client;

import java.util.function.Supplier;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
//? if <26.1 {
import net.minecraft.client.color.item.ItemColors;
//?}

/** Клиентская часть мода без движка: движок (MinePapersNeoForgeClient, MinePapersFabricClient) вызывает init при запуске клиента и передаёт сюда события. */
public final class ModClient {

    /** Управление картинами: ломание, клики, обводка. */
    public static final PictureControls CONTROLS = new PictureControls();

    //? if <26.1 {
    /** Цвета предметов (трава, листва, кожа): в ванили у Minecraft нет для них метода — его даёт движок. С 1.21.4 цвета задаёт модель предмета (см. FrameSkin). */
    public static Supplier<ItemColors> itemColors;
    //?}

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

    /** Как далеко игрок дотягивается до блоков. */
    public static double reach() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=1.20.5 {
        return mc.player.blockInteractionRange();
        //?} else {
        /*return mc.gameMode.getPickRange();
        *///?}
    }

    /** Открытое окно (null - никакого). С 26.2 окна - у Gui. */
    public static net.minecraft.client.gui.screens.Screen screen() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else {
        return Minecraft.getInstance().screen;
        //?}
    }

    public static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else {
        Minecraft.getInstance().setScreen(screen);
        //?}
    }

    /** Интерфейс скрыт (F1). */
    public static boolean hudHidden() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.hud.isHidden();
        *///?} else {
        return Minecraft.getInstance().options.hideGui;
        //?}
    }

    /** Открыть папку в проводнике системы. */
    public static void openFolder(java.io.File dir) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openPath(dir.toPath());
        *///?} else if >=26.1 {
        /*net.minecraft.util.Util.getPlatform().openFile(dir);
        *///?} else {
        net.minecraft.Util.getPlatform().openFile(dir);
        //?}
    }

    /** Взмах рукой игрока, как при ударе. */
    public static void swing() {
        //? if >=26.3 {
        /*Minecraft.getInstance().player.swing(net.minecraft.world.InteractionHand.MAIN_HAND,
            net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        *///?} else {
        Minecraft.getInstance().player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        //?}
    }

    /** Зажат ли Shift (с 1.21.9 у Screen нет hasShiftDown, с 26.3 клавиши - не GLFW). */
    public static boolean shiftDown() {
        //? if >=26.3 {
        /*return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        *///?} else if >=26.1 {
        /*var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
        *///?} else {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
        //?}
    }

    /** Свойство модели предмета minepapers:configured: 1 — картину настраивали (рисует RenderPictureItem), 0 — обычная иконка. */
    public static float configured(ItemStack stack) {
        return ItemPictureFrame.settings(stack) != null ? 1f : 0f;
    }
}
