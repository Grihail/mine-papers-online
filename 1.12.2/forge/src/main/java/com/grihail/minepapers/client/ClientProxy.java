package com.grihail.minepapers.client;

import java.util.Collections;

import com.grihail.minepapers.CommonProxy;
import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameHolder;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public class ClientProxy extends CommonProxy {

    /** Модель обычной рамки (models/item/picture_frame.json) и настроенной: её подменяет RenderPictureItem.Model. */
    private static final ModelResourceLocation PLAIN = new ModelResourceLocation(MinePapers.id("picture_frame"), "inventory");
    private static final ModelResourceLocation CONFIGURED = new ModelResourceLocation(MinePapers.id("picture_frame_configured"), "inventory");

    private int tickCounter;

    @Override
    public void preInit() {
        ClientHost.install(new MinePapersClientHost());
        RenderingRegistry.registerEntityRenderingHandler(EntityPicture.class, RenderPicture::new);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new PictureControls());
        MinecraftForge.EVENT_BUS.register(new FrameSkin.Events());
    }

    /** Предмет картины: настроенной — своя модель со встроенным рендером (иконка с миниатюрой), обычной — плоская иконка. У блока света моделей нет. */
    @SubscribeEvent
    public void onModels(ModelRegistryEvent event) {
        ModelBakery.registerItemVariants(MinePapers.frame, PLAIN, CONFIGURED);
        ModelLoader.setCustomMeshDefinition(MinePapers.frame, stack -> ItemPictureFrame.settings(stack) != null ? CONFIGURED : PLAIN);
        MinePapers.frame.setTileEntityItemStackRenderer(new RenderPictureItem());
        ModelLoader.setCustomStateMapper(MinePapers.light, block -> Collections.emptyMap());
    }

    @SubscribeEvent
    public void onBake(ModelBakeEvent event) {
        event.getModelRegistry().putObject(CONFIGURED, new RenderPictureItem.Model(event.getModelRegistry().getObject(PLAIN)));
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        FrameHolder holder = id == GUI_FRAME ? FrameHolder.of(player, x) : null;
        if (holder != null) ImageSync.setServerImageMb(z);
        return holder != null ? new GuiPictureFrame(new ContainerPictureFrame(player.inventory, holder, y != 0)) : null;
    }

    @Override
    public void onFilesNeeded(byte[] hashes) {
        ImageSync.onFilesNeeded(hashes);
    }

    @Override
    public void onFileChunk(byte[] hash, int total, int offset, byte[] data) {
        ImageSync.onFileChunk(hash, total, offset, data);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ImageSync.tick();
        if (++tickCounter % 40 == 0) {
            PictureManager.tick();
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ImageSync.onDisconnect();
    }
}
