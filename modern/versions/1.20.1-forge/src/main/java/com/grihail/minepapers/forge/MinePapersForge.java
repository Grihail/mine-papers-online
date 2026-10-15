package com.grihail.minepapers.forge;

import java.util.function.BiConsumer;
import java.util.function.Function;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.Platform;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.item.RecipePictureFrame;
import com.grihail.minepapers.network.ModPacket;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketFileChunk;
import com.grihail.minepapers.network.PacketHashes;
import com.grihail.minepapers.network.PacketOfferFiles;
import com.grihail.minepapers.network.PacketOpenFrame;
import com.grihail.minepapers.network.PacketPasteMaterials;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Мод на Forge 1.20.1: регистрация, пакеты и события сервера; сам мод — в общем коде (MinePapers). */
@Mod(MinePapers.MODID)
public class MinePapersForge implements Platform {

    private static final DeferredRegister<net.minecraft.world.item.Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MinePapers.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MinePapers.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MinePapers.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MinePapers.MODID);

    private static final RegistryObject<ItemPictureFrame> FRAME = ITEMS.register(MinePapers.FRAME_ID, MinePapers::makeFrame);
    private static final RegistryObject<EntityType<EntityPicture>> PICTURE = ENTITIES.register(MinePapers.PICTURE_ID, MinePapers::makePicture);
    private static final RegistryObject<RecipeSerializer<RecipePictureFrame>> FRAME_COPY =
        RECIPES.register(MinePapers.FRAME_COPY_ID, MinePapers::makeFrameCopy);
    private static final RegistryObject<MenuType<ContainerPictureFrame>> FRAME_MENU = MENUS.register(MinePapers.FRAME_MENU_ID,
        () -> IForgeMenuType.create((id, inventory, buf) -> ContainerPictureFrame.fromData(id, inventory, ContainerPictureFrame.OpenData.read(buf))));

    /** Канал пакетов мода. Обработчики выполняются в главном потоке (consumerMainThread). */
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(MinePapers.id("main"), () -> "1", "1"::equals, "1"::equals);

    public MinePapersForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinePapers.init(FMLPaths.CONFIGDIR.get(), ModLoadingContext.get().getActiveContainer().getModInfo().getVersion().toString(), this);
        MinePapers.FRAME = FRAME;
        MinePapers.PICTURE = PICTURE;
        MinePapers.FRAME_COPY = FRAME_COPY;
        MinePapers.FRAME_MENU = FRAME_MENU;

        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        RECIPES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(MinePapersForge::fillCreativeTab);
        registerPackets();

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START) ServerImages.onServerTick();
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ServerImages.onLogout(e.getEntity().getUUID()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ServerImages.onServerStopped());
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent e) -> RecipePictureFrame.onCrafted(e.getEntity(), e.getInventory()));

        if (FMLEnvironment.dist == Dist.CLIENT) MinePapersForgeClient.init(modBus);
    }

    /** Рядом с ванильными картинами и рамками — во вкладке "Функциональные блоки". */
    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(FRAME.get());
    }

    private static int nextPacketId;

    private static void registerPackets() {
        toServer(PacketSaveFrame.class, PacketSaveFrame::read, PacketSaveFrame::apply);
        toServer(PacketOfferFiles.class, PacketOfferFiles::read, PacketOfferFiles::apply);
        toServer(PacketFileChunk.Upload.class, PacketFileChunk.Upload::read, PacketFileChunk.Upload::apply);
        toServer(PacketHashes.Request.class, PacketHashes.Request::read, PacketHashes.Request::apply);
        toServer(PacketBreakPicture.class, PacketBreakPicture::read, PacketBreakPicture::apply);
        toServer(PacketOpenFrame.class, PacketOpenFrame::read, PacketOpenFrame::apply);
        toServer(PacketPasteMaterials.class, PacketPasteMaterials::read, PacketPasteMaterials::apply);
        CHANNEL.messageBuilder(PacketHashes.Need.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(PacketHashes.Need::write).decoder(PacketHashes.Need::read)
            .consumerMainThread((msg, ctx) -> MinePapers.proxy.onFilesNeeded(msg.hashes())).add();
        CHANNEL.messageBuilder(PacketFileChunk.class, nextPacketId++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(PacketFileChunk::write).decoder(PacketFileChunk::read)
            .consumerMainThread((msg, ctx) -> MinePapers.proxy.onFileChunk(msg.hash(), msg.total(), msg.offset(), msg.data())).add();
    }

    private static <T extends ModPacket> void toServer(Class<T> type, Function<FriendlyByteBuf, T> read, BiConsumer<ServerPlayer, T> apply) {
        CHANNEL.messageBuilder(type, nextPacketId++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ModPacket::write).decoder(read::apply)
            .consumerMainThread((msg, ctx) -> apply.accept(ctx.get().getSender(), msg)).add();
    }

    // ---------- Platform ----------

    @Override
    public void sendToServer(ModPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ModPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, ContainerPictureFrame.OpenData data) {
        NetworkHooks.openScreen(player, provider, data::write);
    }

    @Override
    public MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }
}
