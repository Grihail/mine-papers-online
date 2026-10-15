package com.grihail.minepapers.neoforge;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.Platform;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.item.RecipePictureFrame;
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

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Мод на NeoForge: регистрация, пакеты и события сервера; сам мод — в общем коде версии (MinePapers). */
@Mod(MinePapers.MODID)
public class MinePapersNeoForge implements Platform {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MinePapers.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MinePapers.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MinePapers.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MinePapers.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE, MinePapers.MODID);

    private static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> FRAME_SETTINGS =
        COMPONENTS.register(MinePapers.FRAME_SETTINGS_ID, MinePapers::makeFrameSettings);
    private static final DeferredItem<ItemPictureFrame> FRAME = ITEMS.register(MinePapers.FRAME_ID, MinePapers::makeFrame);
    private static final DeferredHolder<EntityType<?>, EntityType<EntityPicture>> PICTURE =
        ENTITIES.register(MinePapers.PICTURE_ID, MinePapers::makePicture);
    private static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RecipePictureFrame>> FRAME_COPY =
        RECIPES.register(MinePapers.FRAME_COPY_ID, MinePapers::makeFrameCopy);
    private static final DeferredHolder<MenuType<?>, MenuType<ContainerPictureFrame>> FRAME_MENU =
        MENUS.register(MinePapers.FRAME_MENU_ID, () -> IMenuTypeExtension.create(
            (id, inventory, buf) -> ContainerPictureFrame.fromData(id, inventory, ContainerPictureFrame.OpenData.CODEC.decode(buf))));

    public MinePapersNeoForge(IEventBus modBus, ModContainer container) {
        MinePapers.init(FMLPaths.CONFIGDIR.get(), container.getModInfo().getVersion().toString(), this);
        MinePapers.FRAME_SETTINGS = FRAME_SETTINGS;
        MinePapers.FRAME = FRAME;
        MinePapers.PICTURE = PICTURE;
        MinePapers.FRAME_COPY = FRAME_COPY;
        MinePapers.FRAME_MENU = FRAME_MENU;

        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        RECIPES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(MinePapersNeoForge::registerPackets);
        modBus.addListener(MinePapersNeoForge::fillCreativeTab);

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> ServerImages.onServerTick());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ServerImages.onLogout(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ServerImages.onServerStopped());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent e) -> RecipePictureFrame.onCrafted(e.getEntity(), e.getInventory()));
    }

    /** Рядом с ванильными картинами и рамками — во вкладке "Функциональные блоки". */
    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(FRAME.get());
    }

    /** Пакеты мода. Обработчики выполняются в главном потоке (сервера или клиента). */
    private static void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(PacketSaveFrame.TYPE, PacketSaveFrame.CODEC, (msg, ctx) -> PacketSaveFrame.apply(player(ctx), msg));
        registrar.playToServer(PacketOfferFiles.TYPE, PacketOfferFiles.CODEC, (msg, ctx) -> PacketOfferFiles.apply(player(ctx), msg));
        registrar.playToServer(PacketFileChunk.Upload.TYPE, PacketFileChunk.Upload.CODEC,
            (msg, ctx) -> PacketFileChunk.Upload.apply(player(ctx), msg));
        registrar.playToServer(PacketHashes.Request.TYPE, PacketHashes.Request.CODEC, (msg, ctx) -> PacketHashes.Request.apply(player(ctx), msg));
        registrar.playToServer(PacketBreakPicture.TYPE, PacketBreakPicture.CODEC, (msg, ctx) -> PacketBreakPicture.apply(player(ctx), msg));
        registrar.playToServer(PacketOpenFrame.TYPE, PacketOpenFrame.CODEC, (msg, ctx) -> PacketOpenFrame.apply(player(ctx), msg));
        registrar.playToServer(PacketPasteMaterials.TYPE, PacketPasteMaterials.CODEC,
            (msg, ctx) -> PacketPasteMaterials.apply(player(ctx), msg));
        registrar.playToClient(PacketHashes.Need.TYPE, PacketHashes.Need.CODEC,
            (msg, ctx) -> MinePapers.proxy.onFilesNeeded(msg.hashes()));
        registrar.playToClient(PacketFileChunk.TYPE, PacketFileChunk.CODEC,
            (msg, ctx) -> MinePapers.proxy.onFileChunk(msg.hash(), msg.total(), msg.offset(), msg.data()));
    }

    private static ServerPlayer player(net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        return (ServerPlayer) ctx.player();
    }

    // ---------- Platform ----------

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, ContainerPictureFrame.OpenData data) {
        player.openMenu(provider, buf -> ContainerPictureFrame.OpenData.CODEC.encode(buf, data));
    }

    @Override
    public MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }
}
