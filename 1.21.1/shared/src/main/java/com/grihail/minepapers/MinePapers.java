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

import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/** Мод для 1.21.1 без движка: что регистрируется и как это создать. Регистрирует движок (MinePapersNeoForge, MinePapersFabric) и ставит сюда то, что получилось. */
public final class MinePapers {

    public static final String MODID = "minepapers";
    public static final String NAME = "Mine Papers Online";
    /** Версия мода — из описания мода у движка (туда её при сборке подставляет mod_version), ставится при запуске. */
    public static String VERSION = "";

    private static final Logger LOG = LogUtils.getLogger();

    /** На клиенте заменяется на client.ClientProxy. */
    public static CommonProxy proxy = new CommonProxy();
    public static Platform platform;

    /** Настройки картины в предмете. */
    public static Supplier<DataComponentType<CompoundTag>> FRAME_SETTINGS;
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
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    // ---------- Что регистрировать (имена — константы рядом) ----------

    public static final String FRAME_ID = "picture_frame", PICTURE_ID = "picture", FRAME_COPY_ID = "frame_copy",
        FRAME_MENU_ID = "picture_frame", FRAME_SETTINGS_ID = "frame";

    public static DataComponentType<CompoundTag> makeFrameSettings() {
        return DataComponentType.<CompoundTag>builder().persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG).build();
    }

    public static ItemPictureFrame makeFrame() {
        return new ItemPictureFrame(new Item.Properties());
    }

    public static EntityType<EntityPicture> makePicture() {
        return EntityType.Builder.<EntityPicture>of(EntityPicture::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .clientTrackingRange(10)
            .updateInterval(Integer.MAX_VALUE)
            .build(MODID + ":" + PICTURE_ID);
    }

    public static RecipeSerializer<RecipePictureFrame> makeFrameCopy() {
        return new SimpleCraftingRecipeSerializer<>(RecipePictureFrame::new);
    }
}
