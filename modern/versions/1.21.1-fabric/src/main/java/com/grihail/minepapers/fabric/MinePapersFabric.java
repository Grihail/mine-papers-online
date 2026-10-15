package com.grihail.minepapers.fabric;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
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

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Мод на Fabric: регистрация, пакеты и события сервера; сам мод — в общем коде версии (MinePapers). */
public class MinePapersFabric implements ModInitializer, Platform {

    /** Отправка пакетов на сервер — только на клиенте, её ставит MinePapersFabricClient (классы клиентской сети на сервере не загружаются). */
    static Consumer<ModPacket> clientSender = packet -> {
        throw new IllegalStateException("not a client");
    };

    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(MinePapers.MODID).orElseThrow().getMetadata().getVersion().getFriendlyString();
        MinePapers.init(FabricLoader.getInstance().getConfigDir(), version, this);

        DataComponentType<CompoundTag> settings = register(BuiltInRegistries.DATA_COMPONENT_TYPE, MinePapers.FRAME_SETTINGS_ID, MinePapers.makeFrameSettings());
        ItemPictureFrame frame = register(BuiltInRegistries.ITEM, MinePapers.FRAME_ID, MinePapers.makeFrame());
        EntityType<EntityPicture> picture = register(BuiltInRegistries.ENTITY_TYPE, MinePapers.PICTURE_ID, MinePapers.makePicture());
        RecipeSerializer<RecipePictureFrame> frameCopy = register(BuiltInRegistries.RECIPE_SERIALIZER, MinePapers.FRAME_COPY_ID, MinePapers.makeFrameCopy());
        MenuType<ContainerPictureFrame> menu = register(BuiltInRegistries.MENU, MinePapers.FRAME_MENU_ID, new ExtendedScreenHandlerType<>(
            ContainerPictureFrame::fromData, StreamCodec.ofMember(ContainerPictureFrame.OpenData::write, ContainerPictureFrame.OpenData::read)));
        MinePapers.FRAME_SETTINGS = () -> settings;
        MinePapers.FRAME = () -> frame;
        MinePapers.PICTURE = () -> picture;
        MinePapers.FRAME_COPY = () -> frameCopy;
        MinePapers.FRAME_MENU = () -> menu;

        // Рядом с ванильными картинами и рамками — во вкладке "Функциональные блоки".
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(frame));

        registerPackets();

        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            ServerImages.onServerStopped();
            server = null;
        });
        ServerTickEvents.START_SERVER_TICK.register(s -> ServerImages.onServerTick());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> ServerImages.onLogout(handler.getPlayer().getUUID()));
    }

    private static <T, V extends T> V register(Registry<T> registry, String name, V value) {
        return Registry.register(registry, MinePapers.id(name), value);
    }

    /** Типы пакетов сервер -> клиент: на них клиент вешает обработчики (MinePapersFabricClient). */
    static final CustomPacketPayload.Type<PacketHashes.Need> NEED = new CustomPacketPayload.Type<>(PacketHashes.Need.ID);
    static final CustomPacketPayload.Type<PacketFileChunk> FILE_CHUNK = new CustomPacketPayload.Type<>(PacketFileChunk.ID);

    /** Пакеты мода. Обработчики Fabric выполняются в главном потоке сервера. */
    private static void registerPackets() {
        toServer(PacketSaveFrame.ID, PacketSaveFrame::read, PacketSaveFrame::apply);
        toServer(PacketOfferFiles.ID, PacketOfferFiles::read, PacketOfferFiles::apply);
        toServer(PacketFileChunk.Upload.ID, PacketFileChunk.Upload::read, PacketFileChunk.Upload::apply);
        toServer(PacketHashes.Request.ID, PacketHashes.Request::read, PacketHashes.Request::apply);
        toServer(PacketBreakPicture.ID, PacketBreakPicture::read, PacketBreakPicture::apply);
        toServer(PacketOpenFrame.ID, PacketOpenFrame::read, PacketOpenFrame::apply);
        toServer(PacketPasteMaterials.ID, PacketPasteMaterials::read, PacketPasteMaterials::apply);
        PayloadTypeRegistry.playS2C().register(NEED, codec(PacketHashes.Need::read));
        PayloadTypeRegistry.playS2C().register(FILE_CHUNK, codec(PacketFileChunk::read));
    }

    private static <T extends ModPacket> void toServer(ResourceLocation id, Function<FriendlyByteBuf, T> read, BiConsumer<ServerPlayer, T> apply) {
        CustomPacketPayload.Type<T> type = new CustomPacketPayload.Type<>(id);
        PayloadTypeRegistry.playC2S().register(type, codec(read));
        ServerPlayNetworking.registerGlobalReceiver(type, (msg, ctx) -> apply.accept(ctx.player(), msg));
    }

    /** Кодек пакета из его write и read. */
    private static <T extends ModPacket> StreamCodec<FriendlyByteBuf, T> codec(Function<FriendlyByteBuf, T> read) {
        return StreamCodec.ofMember(ModPacket::write, read::apply);
    }

    // ---------- Platform ----------

    @Override
    public void sendToServer(ModPacket packet) {
        clientSender.accept(packet);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ModPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, ContainerPictureFrame.OpenData data) {
        player.openMenu(new ExtendedScreenHandlerFactory<ContainerPictureFrame.OpenData>() {
            @Override
            public ContainerPictureFrame.OpenData getScreenOpeningData(ServerPlayer p) {
                return data;
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return provider.createMenu(id, inventory, p);
            }
        });
    }

    @Override
    public MinecraftServer server() {
        return server;
    }
}
