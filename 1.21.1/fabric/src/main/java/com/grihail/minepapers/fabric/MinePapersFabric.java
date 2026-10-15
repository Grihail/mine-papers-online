package com.grihail.minepapers.fabric;

import java.util.function.Consumer;

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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
    static Consumer<CustomPacketPayload> clientSender = payload -> {
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
            ContainerPictureFrame::fromData, ContainerPictureFrame.OpenData.CODEC));
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

    /** Пакеты мода. Обработчики Fabric выполняются в главном потоке сервера. */
    private static void registerPackets() {
        PayloadTypeRegistry.playC2S().register(PacketSaveFrame.TYPE, PacketSaveFrame.CODEC);
        PayloadTypeRegistry.playC2S().register(PacketOfferFiles.TYPE, PacketOfferFiles.CODEC);
        PayloadTypeRegistry.playC2S().register(PacketFileChunk.Upload.TYPE, PacketFileChunk.Upload.CODEC);
        PayloadTypeRegistry.playC2S().register(PacketHashes.Request.TYPE, PacketHashes.Request.CODEC);
        PayloadTypeRegistry.playC2S().register(PacketBreakPicture.TYPE, PacketBreakPicture.CODEC);
        PayloadTypeRegistry.playC2S().register(PacketOpenFrame.TYPE, PacketOpenFrame.CODEC);
        PayloadTypeRegistry.playC2S().register(PacketPasteMaterials.TYPE, PacketPasteMaterials.CODEC);
        PayloadTypeRegistry.playS2C().register(PacketHashes.Need.TYPE, PacketHashes.Need.CODEC);
        PayloadTypeRegistry.playS2C().register(PacketFileChunk.TYPE, PacketFileChunk.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PacketSaveFrame.TYPE, (msg, ctx) -> PacketSaveFrame.apply(ctx.player(), msg));
        ServerPlayNetworking.registerGlobalReceiver(PacketOfferFiles.TYPE, (msg, ctx) -> PacketOfferFiles.apply(ctx.player(), msg));
        ServerPlayNetworking.registerGlobalReceiver(PacketFileChunk.Upload.TYPE, (msg, ctx) -> PacketFileChunk.Upload.apply(ctx.player(), msg));
        ServerPlayNetworking.registerGlobalReceiver(PacketHashes.Request.TYPE, (msg, ctx) -> PacketHashes.Request.apply(ctx.player(), msg));
        ServerPlayNetworking.registerGlobalReceiver(PacketBreakPicture.TYPE, (msg, ctx) -> PacketBreakPicture.apply(ctx.player(), msg));
        ServerPlayNetworking.registerGlobalReceiver(PacketOpenFrame.TYPE, (msg, ctx) -> PacketOpenFrame.apply(ctx.player(), msg));
        ServerPlayNetworking.registerGlobalReceiver(PacketPasteMaterials.TYPE, (msg, ctx) -> PacketPasteMaterials.apply(ctx.player(), msg));
    }

    // ---------- Platform ----------

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        clientSender.accept(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
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
