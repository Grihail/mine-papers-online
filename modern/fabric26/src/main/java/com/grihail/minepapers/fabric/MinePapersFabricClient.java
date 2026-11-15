package com.grihail.minepapers.fabric;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.client.FabricClientEvents;
import com.grihail.minepapers.client.FrameSkin;
import com.grihail.minepapers.client.GuiPictureFrame;
import com.grihail.minepapers.client.ModClient;
import com.grihail.minepapers.client.RenderPicture;
import com.grihail.minepapers.client.RenderPictureItem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Клиентская часть мода на Fabric 26.x: передаёт события в общий ModClient. Клики мышью Fabric API не даёт отменять - их перехватывает mixin.MinecraftMixin; обводка блока - в общем client/FabricClientEvents (её вид в разных 26.x разный). */
public class MinePapersFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModClient.init();
        MinePapersFabric.clientSender = ClientPlayNetworking::send;

        ClientPlayNetworking.registerGlobalReceiver(MinePapersFabric.NEED, (msg, ctx) -> MinePapers.proxy.onFilesNeeded(msg.hashes()));
        ClientPlayNetworking.registerGlobalReceiver(MinePapersFabric.FILE_CHUNK,
            (msg, ctx) -> MinePapers.proxy.onFileChunk(msg.hash(), msg.total(), msg.offset(), msg.data()));
        ClientPlayNetworking.registerGlobalReceiver(MinePapersFabric.RELOAD, (msg, ctx) -> MinePapers.proxy.onReload(msg.key(), msg.entity()));

        EntityRendererRegistry.register(MinePapers.PICTURE.get(), RenderPicture::new);
        MenuScreens.register(MinePapers.FRAME_MENU.get(), GuiPictureFrame::new);
        // Настроенную рамку рисует особый рендерер модели (items/picture_frame.json). Своего API для него у Fabric нет - кладём тип
        // в ванильный список (открыт в minepapers.accesswidener), пока модели ещё не загружены.
        SpecialModelRenderers.ID_MAPPER.put(RenderPictureItem.ID, RenderPictureItem.Unbaked.MAP_CODEC);
        // Атлас пересобирается при перезагрузке ресурсов - спрайты рамок становятся другими.
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(MinePapers.id("frame_skins"),
            (ResourceManagerReloadListener) manager -> FrameSkin.clearCache());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> ModClient.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ModClient.onDisconnect());
        FabricClientEvents.register();
    }
}
