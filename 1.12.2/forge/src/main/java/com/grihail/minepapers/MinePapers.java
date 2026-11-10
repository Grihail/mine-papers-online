package com.grihail.minepapers;

import java.io.File;

import com.grihail.minepapers.block.BlockPictureLight;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.item.RecipePictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.network.PacketOfferFiles;
import com.grihail.minepapers.network.PacketOpenFrame;
import com.grihail.minepapers.network.PacketPasteMaterials;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/** Версию FML берёт из mcmod.info (useMetadata), а туда её при сборке подставляет mod_version из gradle.properties. Предметы, блоки, сущность и рецепты регистрируются событиями RegistryEvent (Registration). */
@Mod(modid = MinePapers.MODID, name = MinePapers.NAME, useMetadata = true, acceptedMinecraftVersions = "[1.12.2]")
public class MinePapers {

    public static final String MODID = "minepapers";
    public static final String NAME = "Mine Papers Online";
    /** Версия мода — та, что нашёл FML, ставится в preInit. */
    public static String VERSION = "";

    @Mod.Instance(MODID)
    public static MinePapers instance;

    @SidedProxy(clientSide = "com.grihail.minepapers.client.ClientProxy", serverSide = "com.grihail.minepapers.CommonProxy")
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper network;
    /** Предмет картины. */
    public static ItemPictureFrame frame;
    /** Свет светящихся картин. */
    public static BlockPictureLight light;

    /** Папка с локальными картинками: config/minepapers */
    public static File picturesDir;

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        VERSION = Loader.instance().activeModContainer().getVersion();
        picturesDir = new File(event.getModConfigurationDirectory(), MODID);
        ModConfig.load(event.getModConfigurationDirectory());
        if (!picturesDir.exists()) {
            picturesDir.mkdirs();
        }

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
        ServerImages.install();
        MinecraftForge.EVENT_BUS.register(new ServerImages.Events());
        MinecraftForge.EVENT_BUS.register(new RecipePictureFrame.Events());

        proxy.preInit();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ServerImages.reset();
    }

    /** Регистрация в реестрах игры: события приходят после preInit, по порядку реестров (блоки, предметы, ..., сущности, рецепты). */
    @Mod.EventBusSubscriber(modid = MODID)
    public static final class Registration {

        private Registration() {}

        @SubscribeEvent
        public static void blocks(RegistryEvent.Register<Block> event) {
            light = new BlockPictureLight();
            event.getRegistry().register(light.setRegistryName(id("picture_light")));
        }

        @SubscribeEvent
        public static void items(RegistryEvent.Register<Item> event) {
            frame = new ItemPictureFrame();
            event.getRegistry().register(frame.setRegistryName(id("picture_frame")));
        }

        @SubscribeEvent
        public static void entities(RegistryEvent.Register<EntityEntry> event) {
            event.getRegistry().register(EntityEntryBuilder.create().entity(EntityPicture.class).id(id("picture"), 0)
                .name(MODID + ".picture").tracker(160, Integer.MAX_VALUE, false).build());
        }

        @SubscribeEvent
        public static void recipes(RegistryEvent.Register<IRecipe> event) {
            // Новая рамка — по рецепту из config/minepapers.json (по умолчанию: рамка, картина, книга с пером, стеклянная панель); копирование настроенной рамки и сброс настроек.
            event.getRegistry().register(RecipePictureFrame.frameRecipe().setRegistryName(id("picture_frame")));
            event.getRegistry().register(new RecipePictureFrame().setRegistryName(id("frame_copy")));
        }
    }
}
