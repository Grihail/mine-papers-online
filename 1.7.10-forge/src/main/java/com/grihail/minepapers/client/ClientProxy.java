package com.grihail.minepapers.client;

import com.grihail.minepapers.CommonProxy;
import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    private int tickCounter;

    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityPicture.class, new RenderPicture());
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(MinePapers.frame), new RenderPictureItem());
        FMLCommonHandler.instance().bus().register(this);
        PictureControls controls = new PictureControls();
        FMLCommonHandler.instance().bus().register(controls);
        MinecraftForge.EVENT_BUS.register(controls);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        EntityPicture picture = ServerImages.pictureById(world, x);
        if (id != GUI_FRAME || picture == null) return null;
        return new GuiPictureFrame(new ContainerPictureFrame(player.inventory, picture));
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
