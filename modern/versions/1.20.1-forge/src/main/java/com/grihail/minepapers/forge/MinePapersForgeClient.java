package com.grihail.minepapers.forge;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.client.FrameSkin;
import com.grihail.minepapers.client.GuiPictureFrame;
import com.grihail.minepapers.client.ModClient;
import com.grihail.minepapers.client.RenderPicture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Клиентская часть мода на Forge 1.20.1: вызывается только на клиенте и передаёт события в общий ModClient. Рендерер предмета задаёт сам ItemPictureFrame (initializeClient). */
final class MinePapersForgeClient {

    private MinePapersForgeClient() {}

    static void init(IEventBus modBus) {
        ModClient.init();
        ModClient.itemColors = () -> Minecraft.getInstance().getItemColors();

        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(MinePapers.PICTURE.get(), RenderPicture::new));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            MenuScreens.register(MinePapers.FRAME_MENU.get(), GuiPictureFrame::new);
            ItemProperties.register(MinePapers.FRAME.get(), MinePapers.id("configured"), (stack, level, entity, seed) -> ModClient.configured(stack));
        }));
        modBus.addListener((TextureStitchEvent.Post e) -> {
            if (e.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) FrameSkin.clearCache();
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) ModClient.onClientTick();
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ModClient.onDisconnect());
        MinecraftForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            if (ModClient.CONTROLS.onInteraction(e.isAttack())) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderHighlightEvent.Block e) -> {
            if (ModClient.CONTROLS.onHighlightBlock(e.getPoseStack(), e.getMultiBufferSource(), e.getCamera().getPosition(), e.getTarget())) {
                e.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) ModClient.CONTROLS.onRenderLevel(e.getPartialTick());
        });
    }
}
