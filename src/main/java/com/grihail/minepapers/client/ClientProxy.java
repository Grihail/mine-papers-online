package com.grihail.minepapers.client;

import com.grihail.minepapers.CommonProxy;
import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.tile.TilePictureFrame;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    private int tickCounter;

    @Override
    public void preInit() {
        ClientRegistry.bindTileEntitySpecialRenderer(TilePictureFrame.class, new RenderPicture());
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(MinePapers.frame), new RenderPictureItem());
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(new PlacementPreview());
    }

    @Override
    public void openFrameGui(TilePictureFrame tile) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiPictureFrame(tile));
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
