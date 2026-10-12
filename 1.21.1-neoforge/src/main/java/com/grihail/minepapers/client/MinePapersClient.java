package com.grihail.minepapers.client;

import com.grihail.minepapers.MinePapers;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Клиентская часть мода: создаётся только на клиенте. */
@Mod(value = MinePapers.MODID, dist = Dist.CLIENT)
public class MinePapersClient {

    private int tickCounter;

    public MinePapersClient(IEventBus modBus) {
        MinePapers.proxy = new ClientProxy();

        modBus.addListener(MinePapersClient::registerRenderers);
        modBus.addListener(MinePapersClient::registerItemRenderer);
        modBus.addListener(MinePapersClient::setup);
        modBus.addListener(MinePapersClient::onAtlasStitched);
        modBus.addListener(MinePapersClient::registerScreens);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new PictureControls());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MinePapers.PICTURE.get(), RenderPicture::new);
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

    /** Окно настройки картины для меню, которое открывает сервер. */
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MinePapers.FRAME_MENU.get(), GuiPictureFrame::new);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(MinePapers.FRAME.get(), MinePapers.id("configured"),
            (stack, level, entity, seed) -> stack.has(MinePapers.FRAME_SETTINGS.get()) ? 1f : 0f));
    }

    private static void onAtlasStitched(TextureAtlasStitchedEvent event) {
        if (event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) FrameSkin.clearCache();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        ImageSync.tick();
        if (++tickCounter % 40 == 0) {
            PictureManager.tick();
        }
    }

    @SubscribeEvent
    public void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ImageSync.onDisconnect();
    }
}
