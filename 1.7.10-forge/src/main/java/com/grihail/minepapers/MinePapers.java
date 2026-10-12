package com.grihail.minepapers;

import java.io.File;

import com.grihail.minepapers.block.BlockPictureFiller;
import com.grihail.minepapers.block.BlockPictureFrame;
import com.grihail.minepapers.block.BlockPictureLight;
import com.grihail.minepapers.block.ItemPictureFrame;
import com.grihail.minepapers.block.RecipePictureFrame;
import com.grihail.minepapers.block.TileLegacyPicture;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.network.PacketOfferFiles;
import com.grihail.minepapers.network.PacketOpenFrame;
import com.grihail.minepapers.network.PacketPasteMaterials;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.RecipeSorter;

@Mod(modid = MinePapers.MODID, name = MinePapers.NAME, version = MinePapers.VERSION)
public class MinePapers {

    public static final String MODID = "minepapers";
    public static final String NAME = "Mine Papers Online";
    public static final String VERSION = "1.4.1";

    @Mod.Instance(MODID)
    public static MinePapers instance;

    @SidedProxy(clientSide = "com.grihail.minepapers.client.ClientProxy", serverSide = "com.grihail.minepapers.CommonProxy")
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper network;
    public static BlockPictureFrame frame;
    public static BlockPictureFiller filler;
    /** Свет светящихся картин. */
    public static BlockPictureLight light;

    /** Папка с локальными картинками: config/minepapers */
    public static File picturesDir;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        picturesDir = new File(event.getModConfigurationDirectory(), MODID);
        if (!picturesDir.exists()) {
            picturesDir.mkdirs();
        }

        // Картина — сущность. Блок нужен предмету картины и старым сохранениям, где картины были блоками.
        frame = new BlockPictureFrame();
        GameRegistry.registerBlock(frame, ItemPictureFrame.class, "picture_frame");
        GameRegistry.registerTileEntity(TileLegacyPicture.class, MODID + ":picture_frame");
        filler = new BlockPictureFiller();
        GameRegistry.registerBlock(filler, null, "picture_frame_part");
        light = new BlockPictureLight();
        GameRegistry.registerBlock(light, null, "picture_light");
        EntityRegistry.registerModEntity(EntityPicture.class, "picture", 0, this, 160, Integer.MAX_VALUE, false);

        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(PacketSaveFrame.Handler.class, PacketSaveFrame.class, 0, Side.SERVER);
        network.registerMessage(PacketOfferFiles.Handler.class, PacketOfferFiles.class, 1, Side.SERVER);
        network.registerMessage(PacketHashes.Need.Handler.class, PacketHashes.Need.class, 2, Side.CLIENT);
        network.registerMessage(PacketFileChunk.Upload.Handler.class, PacketFileChunk.Upload.class, 3, Side.SERVER);
        network.registerMessage(PacketHashes.Request.Handler.class, PacketHashes.Request.class, 4, Side.SERVER);
        network.registerMessage(PacketFileChunk.Handler.class, PacketFileChunk.class, 5, Side.CLIENT);
        network.registerMessage(PacketBreakPicture.Handler.class, PacketBreakPicture.class, 6, Side.SERVER);
        network.registerMessage(PacketOpenFrame.Handler.class, PacketOpenFrame.class, 7, Side.SERVER);
        network.registerMessage(PacketPasteMaterials.Handler.class, PacketPasteMaterials.class, 8, Side.SERVER);
        NetworkRegistry.INSTANCE.registerGuiHandler(this, proxy);
        FMLCommonHandler.instance().bus().register(new ServerImages.Events());
        FMLCommonHandler.instance().bus().register(new RecipePictureFrame.Events());

        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        GameRegistry.addShapelessRecipe(new ItemStack(frame), Items.item_frame, Items.painting);
        // Копирование настроенной рамки и сброс настроек
        RecipeSorter.register(MODID + ":frame_copy", RecipePictureFrame.class, RecipeSorter.Category.SHAPELESS,
            "after:minecraft:shapeless");
        GameRegistry.addRecipe(new RecipePictureFrame());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ServerImages.reset();
    }
}
