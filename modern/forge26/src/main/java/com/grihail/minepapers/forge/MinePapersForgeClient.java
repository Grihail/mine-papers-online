package com.grihail.minepapers.forge;

import java.lang.reflect.Field;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.client.ForgeClientEvents;
import com.grihail.minepapers.client.FrameSkin;
import com.grihail.minepapers.client.GuiPictureFrame;
import com.grihail.minepapers.client.ModClient;
import com.grihail.minepapers.client.RenderPicture;
import com.grihail.minepapers.client.RenderPictureItem;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Клиентская часть мода на Forge 26.x: вызывается только на клиенте и передаёт события в общий ModClient. */
final class MinePapersForgeClient {

    private MinePapersForgeClient() {}

    static void init(BusGroup modBus) {
        ModClient.init();
        registerItemRenderer();

        EntityRenderersEvent.RegisterRenderers.BUS.addListener(e -> e.registerEntityRenderer(MinePapers.PICTURE.get(), RenderPicture::new));
        FMLClientSetupEvent.getBus(modBus).addListener(e -> e.enqueueWork(() -> MenuScreens.register(MinePapers.FRAME_MENU.get(), GuiPictureFrame::new)));
        TextureStitchEvent.Post.BUS.addListener(e -> {
            if (e.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS) || e.getAtlas().location().equals(TextureAtlas.LOCATION_ITEMS)) {
                FrameSkin.clearCache();
            }
        });

        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> ModClient.onClientTick());
        // Картину под прицелом ищем раз за кадр, до сбора кадра: по ней рисуется обводка (RenderPicture).
        TickEvent.RenderTickEvent.Pre.BUS.addListener(e -> {
            if (net.minecraft.client.Minecraft.getInstance().level != null) {
                ModClient.CONTROLS.onRenderLevel(e.timer().getGameTimeDeltaPartialTick(true));
            }
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> ModClient.onDisconnect());
        ForgeClientEvents.register(); // клики и обводка блока — их вид в разных 26.x разный
    }

    /** Настроенную рамку рисует особый рендерер модели (items/picture_frame.json). Своего события для него у Forge нет — кладём тип в ванильный список, пока модели ещё не загружены. */
    @SuppressWarnings("unchecked")
    private static void registerItemRenderer() {
        try {
            Field f = SpecialModelRenderers.class.getDeclaredField("ID_MAPPER");
            f.setAccessible(true);
            ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> mapper =
                (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>>) f.get(null);
            mapper.put(RenderPictureItem.ID, RenderPictureItem.Unbaked.MAP_CODEC);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось зарегистрировать рендерер предмета картины", e);
        }
    }
}
