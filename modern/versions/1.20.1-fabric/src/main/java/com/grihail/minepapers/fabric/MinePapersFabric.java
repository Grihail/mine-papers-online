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
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

/** Мод на Fabric 1.20.1: регистрация, пакеты и события сервера; сам мод — в общем коде (MinePapers). */
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

        ItemPictureFrame frame = register(BuiltInRegistries.ITEM, MinePapers.FRAME_ID, MinePapers.makeFrame());
        EntityType<EntityPicture> picture = register(BuiltInRegistries.ENTITY_TYPE, MinePapers.PICTURE_ID, MinePapers.makePicture());
        RecipeSerializer<RecipePictureFrame> frameCopy = register(BuiltInRegistries.RECIPE_SERIALIZER, MinePapers.FRAME_COPY_ID, MinePapers.makeFrameCopy());
        MenuType<ContainerPictureFrame> menu = register(BuiltInRegistries.MENU, MinePapers.FRAME_MENU_ID, new ExtendedScreenHandlerType<>(
            (id, inventory, buf) -> ContainerPictureFrame.fromData(id, inventory, ContainerPictureFrame.OpenData.read(buf))));
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

    /** Пакеты мода с клиента. В Fabric 1.20.1 обработчик приходит в сетевом потоке — пакет применяется в серверном. */
    private static void registerPackets() {
        toServer(PacketSaveFrame.ID, PacketSaveFrame::read, PacketSaveFrame::apply);
        toServer(PacketOfferFiles.ID, PacketOfferFiles::read, PacketOfferFiles::apply);
        toServer(PacketFileChunk.Upload.ID, PacketFileChunk.Upload::read, PacketFileChunk.Upload::apply);
        toServer(PacketHashes.Request.ID, PacketHashes.Request::read, PacketHashes.Request::apply);
        toServer(PacketBreakPicture.ID, PacketBreakPicture::read, PacketBreakPicture::apply);
        toServer(PacketOpenFrame.ID, PacketOpenFrame::read, PacketOpenFrame::apply);
        toServer(PacketPasteMaterials.ID, PacketPasteMaterials::read, PacketPasteMaterials::apply);
    }

    private static <T extends ModPacket> void toServer(ResourceLocation id, Function<FriendlyByteBuf, T> read, BiConsumer<ServerPlayer, T> apply) {
        ServerPlayNetworking.registerGlobalReceiver(id, (s, player, handler, buf, sender) -> {
            T msg = read.apply(buf);
            s.execute(() -> apply.accept(player, msg));
        });
    }

    /** Байты пакета для отправки. */
    static FriendlyByteBuf bytes(ModPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        return buf;
    }

    // ---------- Platform ----------

    @Override
    public void sendToServer(ModPacket packet) {
        clientSender.accept(packet);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ModPacket packet) {
        ServerPlayNetworking.send(player, packet.id(), bytes(packet));
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider provider, ContainerPictureFrame.OpenData data) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer p, FriendlyByteBuf buf) {
                data.write(buf);
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
