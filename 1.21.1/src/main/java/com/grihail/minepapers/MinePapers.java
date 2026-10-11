package com.grihail.minepapers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.item.RecipePictureFrame;
import com.grihail.minepapers.network.Network;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;
import com.mojang.logging.LogUtils;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MinePapers.MODID)
public class MinePapers {

    public static final String MODID = "minepapers";
    public static final String NAME = "Mine Papers Online";
    public static String VERSION = "1.2.0";

    private static final Logger LOG = LogUtils.getLogger();

    /** На клиенте заменяется на client.ClientProxy (см. client.MinePapersClient). */
    public static CommonProxy proxy = new CommonProxy();

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE, MODID);

    /** Настройки картины в предмете. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> FRAME_SETTINGS =
        COMPONENTS.registerComponentType("frame",
            b -> b.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    public static final DeferredItem<ItemPictureFrame> FRAME = ITEMS.registerItem("picture_frame", ItemPictureFrame::new);

    public static final DeferredHolder<EntityType<?>, EntityType<EntityPicture>> PICTURE = ENTITIES.register("picture",
        () -> EntityType.Builder.<EntityPicture>of(EntityPicture::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .clientTrackingRange(10)
            .updateInterval(Integer.MAX_VALUE)
            .build(MODID + ":picture"));

    /** Копирование настроенной рамки и сброс настроек. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RecipePictureFrame>> FRAME_COPY =
        RECIPES.register("frame_copy", () -> new SimpleCraftingRecipeSerializer<>(RecipePictureFrame::new));

    /** Папка с локальными картинками: config/minepapers */
    public static Path picturesDir;

    public MinePapers(IEventBus modBus, ModContainer container) {
        VERSION = container.getModInfo().getVersion().toString();
        picturesDir = FMLPaths.CONFIGDIR.get().resolve(MODID);
        try {
            Files.createDirectories(picturesDir);
        } catch (IOException e) {
            LOG.warn("Не удалось создать папку " + picturesDir, e);
        }

        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        RECIPES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(Network::register);
        modBus.addListener(MinePapers::fillCreativeTab);

        NeoForge.EVENT_BUS.register(new ServerImages.Events());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** Рядом с ванильными картинами и рамками — во вкладке "Функциональные блоки". */
    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(FRAME.get());
    }
}
