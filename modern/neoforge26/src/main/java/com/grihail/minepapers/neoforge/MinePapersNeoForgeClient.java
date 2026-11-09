package com.grihail.minepapers.neoforge;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.client.FrameSkin;
import com.grihail.minepapers.client.GuiPictureFrame;
import com.grihail.minepapers.client.ModClient;
import com.grihail.minepapers.client.NeoForgeClientEvents;
import com.grihail.minepapers.client.RenderPicture;
import com.grihail.minepapers.client.RenderPictureItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Клиентская часть мода на NeoForge 26.x: создаётся только на клиенте и передаёт события в общий ModClient. */
@Mod(value = MinePapers.MODID, dist = Dist.CLIENT)
public class MinePapersNeoForgeClient {

    public MinePapersNeoForgeClient(IEventBus modBus) {
        ModClient.init();

        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(MinePapers.PICTURE.get(), RenderPicture::new));
        modBus.addListener((RegisterSpecialModelRendererEvent e) -> e.register(RenderPictureItem.ID, RenderPictureItem.Unbaked.MAP_CODEC));
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(MinePapers.FRAME_MENU.get(), GuiPictureFrame::new));
        modBus.addListener((TextureAtlasStitchedEvent e) -> {
            if (e.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS) || e.getAtlas().location().equals(TextureAtlas.LOCATION_ITEMS)) {
                FrameSkin.clearCache();
            }
        });

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ModClient.onClientTick());
        // Картину под прицелом ищем раз за кадр, до сбора кадра: по ней рисуется обводка (RenderPicture).
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> {
            if (Minecraft.getInstance().level != null) ModClient.CONTROLS.onRenderLevel(e.getPartialTick().getGameTimeDeltaPartialTick(true));
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ModClient.onDisconnect());
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            if (ModClient.CONTROLS.onInteraction(e.isAttack())) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
        });
        NeoForgeClientEvents.register(); // обводка блока: её вид в разных 26.x разный
    }
}
