package com.grihail.minepapers.fabric;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.client.FrameSkin;
import com.grihail.minepapers.client.GuiPictureFrame;
import com.grihail.minepapers.client.ModClient;
import com.grihail.minepapers.client.RenderPicture;
import com.grihail.minepapers.client.RenderPictureItem;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Клиентская часть мода на Fabric: передаёт события в общий ModClient. Клики мышью и обводку блока Fabric API не даёт отменять — их перехватывает mixin.MinecraftMixin и WorldRenderEvents. */
public class MinePapersFabricClient implements ClientModInitializer {

    private static RenderPictureItem itemRenderer;

    @Override
    public void onInitializeClient() {
        ModClient.init();
        ModClient.itemColors = () -> Minecraft.getInstance().itemColors; // открыто в minepapers.accesswidener
        MinePapersFabric.clientSender = packet -> ClientPlayNetworking.send(packet.id(), MinePapersFabric.bytes(packet));

        // В Fabric 1.20.1 обработчик приходит в сетевом потоке — пакет применяется в клиентском.
        ClientPlayNetworking.registerGlobalReceiver(PacketHashes.Need.ID, (client, handler, buf, sender) -> {
            PacketHashes.Need msg = PacketHashes.Need.read(buf);
            client.execute(() -> MinePapers.proxy.onFilesNeeded(msg.hashes()));
        });
        ClientPlayNetworking.registerGlobalReceiver(PacketFileChunk.ID, (client, handler, buf, sender) -> {
            PacketFileChunk msg = PacketFileChunk.read(buf);
            client.execute(() -> MinePapers.proxy.onFileChunk(msg.hash(), msg.total(), msg.offset(), msg.data()));
        });

        EntityRendererRegistry.register(MinePapers.PICTURE.get(), RenderPicture::new);
        MenuScreens.register(MinePapers.FRAME_MENU.get(), GuiPictureFrame::new);
        // Настроенная рамка рисуется своим рендерером (модель picture_frame_configured — builtin/entity).
        BuiltinItemRendererRegistry.INSTANCE.register(MinePapers.FRAME.get(), (stack, context, poseStack, buffers, light, overlay) -> {
            if (itemRenderer == null) itemRenderer = new RenderPictureItem();
            itemRenderer.renderByItem(stack, context, poseStack, buffers, light, overlay);
        });
        FabricModelPredicateProviderRegistry.register(MinePapers.FRAME.get(), MinePapers.id("configured"), (stack, level, entity, seed) -> ModClient.configured(stack));
        // Атлас пересобирается при перезагрузке ресурсов — спрайты рамок становятся другими.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return MinePapers.id("frame_skins");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                FrameSkin.clearCache();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> ModClient.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ModClient.onDisconnect());
        WorldRenderEvents.START.register(ctx -> ModClient.CONTROLS.onRenderLevel(ctx.tickDelta()));
        // Fabric зовёт это и при взгляде в воздух (промах — BlockHitResult типа MISS на краю дальности), а контур нужен только на блоке.
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, hit) -> !(hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK)
            || !ModClient.CONTROLS.onHighlightBlock(ctx.matrixStack(), ctx.consumers(), ctx.camera().getPosition(), block));
    }
}
