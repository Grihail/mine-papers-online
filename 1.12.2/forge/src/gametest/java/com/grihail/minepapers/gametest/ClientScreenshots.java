package com.grihail.minepapers.gametest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.client.GuiPictureFrame;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Снимки окна настройки на клиенте 1.12.2 (задача runClientScreenshots): клиент сам создаёт плоский мир, кладёт в хотбар картины (обычную, настроенную, подписанную), открывает окно картины в руке, снимает каждую вкладку в screenshots/ и выходит. Окно можно посмотреть без ручного запуска игры. */
final class ClientScreenshots {

    private static final int TABS = 5, SETTLE_FRAMES = 5;

    private boolean worldStarted;
    private int ticksInWorld;
    private GuiPictureFrame gui;
    private int tab = -1, frames;

    @SubscribeEvent
    public void onGui(GuiOpenEvent event) {
        if (worldStarted || !(event.getGui() instanceof GuiMainMenu)) return;
        worldStarted = true;
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.launchIntegratedServer("mp-screenshots", "mp-screenshots",
            new WorldSettings(1L, GameType.CREATIVE, false, false, WorldType.FLAT)));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.player == null || gui != null) return;
        if (++ticksInWorld < 60) return;
        mc.player.inventory.currentItem = 0;
        PictureFrame configured = new PictureFrame();
        configured.title = "Configured";
        configured.width = 2;
        mc.player.inventory.setInventorySlotContents(0, ItemPictureFrame.makeStack(configured));
        mc.player.inventory.setInventorySlotContents(1, new ItemStack(MinePapers.frame, 16));
        PictureFrame signed = new PictureFrame();
        signed.signer = UUID.randomUUID();
        signed.signerName = "Signer";
        mc.player.inventory.setInventorySlotContents(2, ItemPictureFrame.makeStack(signed));
        FrameHolder holder = FrameHolder.of(mc.player, FrameHolder.targetOf(mc.player, EnumHand.MAIN_HAND));
        gui = new GuiPictureFrame(new ContainerPictureFrame(mc.player.inventory, holder, true));
        mc.displayGuiScreen(gui);
        tab = 0;
        frames = 0;
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || gui == null || tab < 0 || mc.currentScreen != gui) return;
        if (++frames < SETTLE_FRAMES) return;
        ScreenShotHelper.saveScreenshot(mc.gameDir, "minepapers-tab" + tab + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        if (++tab >= TABS) {
            tab = -1;
            mc.shutdown();
            return;
        }
        click(gui, tab);
        frames = 0;
    }

    /** Нажать вкладку id (id кнопок вкладок — 0..4, см. GuiPictureFrame). */
    @SuppressWarnings("unchecked")
    private static void click(GuiScreen screen, int id) {
        try {
            java.lang.reflect.Field field = GuiScreen.class.getDeclaredField("buttonList");
            field.setAccessible(true);
            Method action = screen.getClass().getDeclaredMethod("actionPerformed", GuiButton.class);
            action.setAccessible(true);
            for (GuiButton b : (List<GuiButton>) field.get(screen)) {
                if (b.id == id) {
                    action.invoke(screen, b);
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
