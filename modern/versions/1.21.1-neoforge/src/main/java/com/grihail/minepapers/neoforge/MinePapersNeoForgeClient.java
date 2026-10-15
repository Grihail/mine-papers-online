package com.grihail.minepapers.neoforge;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.client.FrameSkin;
import com.grihail.minepapers.client.GuiPictureFrame;
import com.grihail.minepapers.client.ModClient;
import com.grihail.minepapers.client.RenderPicture;
import com.grihail.minepapers.client.RenderPictureItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Клиентская часть мода на NeoForge: создаётся только на клиенте и передаёт события в общий ModClient. */
@Mod(value = MinePapers.MODID, dist = Dist.CLIENT)
public class MinePapersNeoForgeClient {

    public MinePapersNeoForgeClient(IEventBus modBus) {
        ModClient.init();
        ModClient.itemColors = () -> Minecraft.getInstance().getItemColors();

        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(MinePapers.PICTURE.get(), RenderPicture::new));
        modBus.addListener(MinePapersNeoForgeClient::registerItemRenderer);
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(MinePapers.FRAME_MENU.get(), GuiPictureFrame::new));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> ItemProperties.register(MinePapers.FRAME.get(),
            MinePapers.id("configured"), (stack, level, entity, seed) -> ModClient.configured(stack))));
        modBus.addListener((TextureAtlasStitchedEvent e) -> {
            if (e.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) FrameSkin.clearCache();
        });

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> ModClient.onClientTick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ModClient.onDisconnect());
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            if (ModClient.CONTROLS.onInteraction(e.isAttack())) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderHighlightEvent.Block e) -> {
            if (ModClient.CONTROLS.onHighlightBlock(e.getPoseStack(), e.getMultiBufferSource(), e.getCamera().getPosition(), e.getTarget())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
                ModClient.CONTROLS.onRenderLevel(e.getPartialTick().getGameTimeDeltaPartialTick(true));
            }
        });
    }

    /** Настроенная рамка рисуется своим рендерером (модель picture_frame_configured — builtin/entity). */
    private static void registerItemRenderer(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private RenderPictureItem renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new RenderPictureItem();
                return renderer;
            }
        }, MinePapers.FRAME.get());
    }
}
