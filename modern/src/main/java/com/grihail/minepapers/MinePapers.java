package com.grihail.minepapers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.item.RecipePictureFrame;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.server.ServerImages;
import com.mojang.logging.LogUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
//? if >=26.1 {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
*///?} else {
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
//?}
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
//?}

/** Мод для современных версий без движка: что регистрируется и как это создать. Регистрирует движок (MinePapersNeoForge, MinePapersFabric) и ставит сюда то, что получилось. */
public final class MinePapers {

    public static final String MODID = "minepapers";
    public static final String NAME = "Mine Papers Online";
    /** Версия мода — из описания мода у движка (туда её при сборке подставляет mod_version), ставится при запуске. */
    public static String VERSION = "";

    private static final Logger LOG = LogUtils.getLogger();

    /** На клиенте заменяется на client.ClientProxy. */
    public static CommonProxy proxy = new CommonProxy();
    public static Platform platform;

    //? if >=1.20.5 {
    /** Настройки картины в предмете (до 1.20.5 — тег предмета ItemPictureFrame.TAG). */
    public static Supplier<DataComponentType<CompoundTag>> FRAME_SETTINGS;
    //?}
    public static Supplier<? extends ItemPictureFrame> FRAME;
    public static Supplier<EntityType<EntityPicture>> PICTURE;
    /** Копирование настроенной рамки и сброс настроек. */
    public static Supplier<RecipeSerializer<RecipePictureFrame>> FRAME_COPY;
    /** Окно настройки картины (инвентарь игрока + слоты материалов). */
    public static Supplier<MenuType<ContainerPictureFrame>> FRAME_MENU;

    /** Папка с локальными картинками: config/minepapers */
    public static Path picturesDir;

    private MinePapers() {}

    /** Запуск мода (до регистрации): configDir — папка config игры или сервера. */
    public static void init(Path configDir, String version, Platform platform) {
        VERSION = version;
        MinePapers.platform = platform;
        picturesDir = configDir.resolve(MODID);
        ModConfig.load(configDir.toFile());
        try {
            Files.createDirectories(picturesDir);
        } catch (IOException e) {
            LOG.warn("Не удалось создать папку " + picturesDir, e);
        }
        ServerImages.install();
    }

    public static ResourceLocation id(String path) {
        return location(MODID, path);
    }

    /** Ванильный ресурс minecraft:path. */
    public static ResourceLocation vanilla(String path) {
        return location("minecraft", path);
    }

    public static ResourceLocation location(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }

    /** Ресурс из строки "namespace:path" (без namespace — minecraft); не ресурс — исключение. */
    public static ResourceLocation parse(String id) {
        //? if >=1.21 {
        return ResourceLocation.parse(id);
        //?} else {
        /*return new ResourceLocation(id);
        *///?}
    }

    // ---------- Что регистрировать (имена — константы рядом) ----------

    public static final String FRAME_ID = "picture_frame", PICTURE_ID = "picture", FRAME_COPY_ID = "frame_copy",
        FRAME_MENU_ID = "picture_frame", FRAME_SETTINGS_ID = "frame";

    //? if >=1.20.5 {
    public static DataComponentType<CompoundTag> makeFrameSettings() {
        return DataComponentType.<CompoundTag>builder().persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG).build();
    }
    //?}

    public static ItemPictureFrame makeFrame() {
        //? if >=26.1 {
        /*// С 1.21.2 предмет при создании должен знать свой id.
        return new ItemPictureFrame(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(FRAME_ID))));
        *///?} else {
        return new ItemPictureFrame(new Item.Properties());
        //?}
    }

    public static EntityType<EntityPicture> makePicture() {
        return EntityType.Builder.<EntityPicture>of(EntityPicture::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .clientTrackingRange(10)
            .updateInterval(Integer.MAX_VALUE)
            //? if >=26.1 {
            /*.build(ResourceKey.create(Registries.ENTITY_TYPE, id(PICTURE_ID)));
            *///?} else {
            .build(MODID + ":" + PICTURE_ID);
            //?}
    }

    public static RecipeSerializer<RecipePictureFrame> makeFrameCopy() {
        //? if >=26.1 {
        /*return RecipePictureFrame.SERIALIZER;
        *///?} else {
        return new SimpleCraftingRecipeSerializer<>(RecipePictureFrame::new);
        //?}
    }
}
