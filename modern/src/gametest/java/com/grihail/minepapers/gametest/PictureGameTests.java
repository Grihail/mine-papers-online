package com.grihail.minepapers.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.item.RecipePictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketPasteMaterials;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
//? if >=1.20.2 {
import net.minecraft.server.network.CommonListenerCookie;
//?}
//? if >=1.20.5 {
import net.minecraft.world.item.crafting.CraftingInput;
//?}

/** Игровые тесты картины на сервере: установка, ломание, свет, пакеты окна, права, рецепты, сохранение — одни для всех движков версии. Тест — открытый статический метод с GameTestHelper; движок регистрирует их все через all() (ванильный @GameTestGenerator в своём классе тестов). Запуск — build.bat test: сервер каждого движка прогоняет тесты и выходит с ошибкой, если какой-то упал. */
public final class PictureGameTests {

    /** Пустая площадка 6x6x6 (data/minepapers/structure/empty.nbt). */
    private static final String EMPTY = MinePapers.MODID + ":empty";
    private static final int TIMEOUT_TICKS = 100;

    private PictureGameTests() {}

    /** Все тесты класса — для @GameTestGenerator движка. */
    public static Collection<TestFunction> all() {
        List<TestFunction> tests = new ArrayList<TestFunction>();
        for (Method m : PictureGameTests.class.getDeclaredMethods()) {
            int mod = m.getModifiers();
            if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod) || m.getReturnType() != void.class
                || m.getParameterCount() != 1 || m.getParameterTypes()[0] != GameTestHelper.class) continue;
            //? if >=1.20.5 {
            tests.add(new TestFunction("minepapers", "minepapers." + m.getName().toLowerCase(Locale.ROOT), EMPTY, Rotation.NONE,
                TIMEOUT_TICKS, 0, true, false, 1, 1, false, helper -> {
            //?} else {
            /*tests.add(new TestFunction("minepapers", "minepapers." + m.getName().toLowerCase(Locale.ROOT), EMPTY, Rotation.NONE,
                TIMEOUT_TICKS, 0, true, 1, 1, helper -> {
            *///?}
                    try {
                        m.invoke(null, helper);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof RuntimeException r) throw r;
                        throw new RuntimeException(e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }));
        }
        return tests;
    }
    /** Стена (камень) в площадке; картина вешается на её южную грань — в клетку WALL.south(). */
    private static final BlockPos WALL = new BlockPos(2, 1, 2);

    // ---------- Помощники ----------

    /** Игрок на сервере с поддельным соединением (как GameTestHelper.makeMockServerPlayerInLevel, но и в выживании), стоит перед стеной. */
    private static ServerPlayer player(GameTestHelper h, boolean creative) {
        //? if >=1.20.2 {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "tester"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
        //?} else {
        /*ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "tester")) {
        *///?}
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return creative;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        //? if >=1.20.2 {
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        //?} else {
        /*h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player);
        *///?}
        // Тестовый сервер по умолчанию даёт творческий режим, а в нём предметы не тратятся.
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(WALL.south(2)));
        player.moveTo(at.x, at.y, at.z, 180f, 0f); // лицом на север, к стене
        return player;
    }

    private static void leave(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
    }

    /** Повесить картину из stack на южную грань стены рукой игрока. */
    private static InteractionResult use(GameTestHelper h, ServerPlayer player, ItemStack stack) {
        BlockPos wall = h.absolutePos(WALL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(wall).relative(Direction.SOUTH, 0.5), Direction.SOUTH, wall, false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static EntityPicture hang(GameTestHelper h, ServerPlayer player) {
        h.setBlock(WALL, Blocks.STONE);
        h.assertTrue(use(h, player, new ItemStack(MinePapers.FRAME.get())).consumesAction(), "picture is placed");
        List<EntityPicture> pictures = pictures(h);
        eq(h, pictures.size(), 1, "pictures");
        return pictures.get(0);
    }

    /** Проверка равенства (в 1.20.1 у GameTestHelper её нет). */
    private static void eq(GameTestHelper h, Object actual, Object expected, String what) {
        h.assertTrue(java.util.Objects.equals(actual, expected), what + ": expected " + expected + ", got " + actual);
    }

    /** Площадка теста в мире (GameTestHelper.getBounds в 1.20.1 закрыт). */
    private static AABB area(GameTestHelper h) {
        return new AABB(h.absoluteVec(Vec3.ZERO), h.absoluteVec(new Vec3(6, 6, 6)));
    }

    private static List<EntityPicture> pictures(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(EntityPicture.class, area(h), e -> !e.isRemoved());
    }

    private static List<ItemEntity> drops(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h).inflate(2),
            e -> !e.isRemoved() && e.getItem().is(MinePapers.FRAME.get()));
    }

    private static CompoundTag settings(PictureFrame frame) {
        CompoundTag tag = new CompoundTag();
        frame.writeSettings(tag);
        return tag;
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    // ---------- Установка ----------

    public static void hangsOnWallFace(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        ItemStack stack = new ItemStack(MinePapers.FRAME.get(), 2);
        h.setBlock(WALL, Blocks.STONE);
        h.assertTrue(use(h, player, stack).consumesAction(), "placed");
        EntityPicture picture = pictures(h).get(0);
        BlockPos cell = h.absolutePos(WALL.south());
        eq(h, new BlockPos(picture.frame.x, picture.frame.y, picture.frame.z), cell, "frame cell");
        eq(h, picture.frame.getFacing(), Direction.SOUTH.get3DDataValue(), "facing");
        eq(h, stack.getCount(), 1, "one item spent in survival");
        AABB box = picture.getBoundingBox();
        h.assertTrue(Math.abs(box.minZ - cell.getZ()) < 1e-6 && box.getZsize() < 0.2, "hitbox lies on the wall: " + box);

        // На то же место вторую не повесить: холсты пересеклись бы.
        h.assertTrue(!use(h, player, stack).consumesAction(), "second picture is blocked");
        eq(h, pictures(h).size(), 1, "pictures");
        leave(h, player);
        h.succeed();
    }

    public static void canvasCannotGoThroughBlocks(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        PictureFrame big = new PictureFrame();
        big.width = 3;
        h.setBlock(WALL, Blocks.STONE);
        h.setBlock(WALL.south().east(), Blocks.STONE); // справа от картины, на пути холста
        h.assertTrue(!use(h, player, ItemPictureFrame.makeStack(big)).consumesAction(), "3 blocks wide canvas hits the block");
        eq(h, pictures(h).size(), 0, "pictures");
        leave(h, player);
        h.succeed();
    }

    // ---------- Ломание ----------

    public static void survivalBreakingTakesTime(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        EntityPicture picture = hang(h, player);
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getId(), PacketBreakPicture.START));
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getId(), PacketBreakPicture.FINISH));
        h.assertTrue(!picture.isRemoved(), "instant break is ignored");
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getId(), PacketBreakPicture.START));
        h.runAfterDelay(60, () -> {
            PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getId(), PacketBreakPicture.FINISH));
            h.assertTrue(picture.isRemoved(), "broken after digging");
            eq(h, drops(h).size(), 1, "picture item dropped");
            leave(h, player);
            h.succeed();
        });
    }

    public static void creativeBreaksWithoutDrop(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        EntityPicture picture = hang(h, player);
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getId(), PacketBreakPicture.FINISH));
        h.assertTrue(picture.isRemoved(), "broken at once");
        eq(h, drops(h).size(), 0, "no drop in creative");
        leave(h, player);
        h.succeed();
    }

    public static void explosionDropsConfiguredItem(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        EntityPicture picture = hang(h, player);
        picture.frame.title = "Boom";
        picture.settingsChanged();
        h.assertTrue(!picture.hurt(h.getLevel().damageSources().generic(), 5f), "ordinary damage does nothing");
        picture.hurt(h.getLevel().damageSources().explosion(null, null), 5f);
        h.assertTrue(picture.isRemoved(), "explosion breaks");
        List<ItemEntity> drops = drops(h);
        eq(h, drops.size(), 1, "drops");
        eq(h, ItemPictureFrame.read(drops.get(0).getItem()).title, "Boom", "settings kept in the item");
        leave(h, player);
        h.succeed();
    }

    // ---------- Свет ----------

    public static void glowingPicturePlacesAndRemovesLight(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        EntityPicture picture = hang(h, player);
        picture.frame.glow.set(new ItemStack(Items.GLOWSTONE));
        picture.settingsChanged();
        BlockPos cell = WALL.south();
        h.runAfterDelay(2, () -> {
            h.assertBlockPresent(Blocks.LIGHT, cell);
            eq(h, h.getBlockState(cell).getValue(net.minecraft.world.level.block.LightBlock.LEVEL), 15, "light level");
            picture.discard();
            h.assertBlockNotPresent(Blocks.LIGHT, cell);
            leave(h, player);
            h.succeed();
        });
    }

    // ---------- Окно настройки ----------

    public static void lockKeepsOthersOut(GameTestHelper h) {
        ServerPlayer alice = player(h, false), bob = player(h, false);
        EntityPicture picture = hang(h, alice);

        PictureFrame wanted = new PictureFrame();
        wanted.readSettings(settings(picture.frame));
        wanted.title = "Mine";
        wanted.owner = bob.getUUID(); // клиент может прислать любое имя — владельцем станет отправитель
        PacketSaveFrame.apply(alice, new PacketSaveFrame(picture.getId(), settings(wanted)));
        eq(h, picture.frame.title, "Mine", "title saved");
        eq(h, picture.frame.owner, alice.getUUID(), "owner is the sender");

        wanted.title = "Hacked";
        wanted.owner = null;
        PacketSaveFrame.apply(bob, new PacketSaveFrame(picture.getId(), settings(wanted)));
        eq(h, picture.frame.title, "Mine", "stranger cannot change a locked picture");
        eq(h, picture.frame.owner, alice.getUUID(), "stranger cannot unlock");

        PacketSaveFrame.apply(alice, new PacketSaveFrame(picture.getId(), settings(wanted)));
        h.assertTrue(picture.frame.owner == null, "owner unlocks");
        leave(h, alice, bob);
        h.succeed();
    }

    public static void tooFarCannotEdit(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        EntityPicture picture = hang(h, player);
        player.moveTo(player.getX(), player.getY() + 40, player.getZ());
        PictureFrame wanted = new PictureFrame();
        wanted.title = "Far";
        PacketSaveFrame.apply(player, new PacketSaveFrame(picture.getId(), settings(wanted)));
        eq(h, picture.frame.title, "", "out of reach");
        leave(h, player);
        h.succeed();
    }

    public static void materialSlotsTakeAndGiveBack(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        EntityPicture picture = hang(h, player);
        ContainerPictureFrame menu = new ContainerPictureFrame(1, player.getInventory(), FrameHolder.of(picture, player), true);

        // Блок из руки мыши — в слот рамки.
        menu.setCarried(new ItemStack(Items.OAK_PLANKS, 5));
        menu.clicked(ContainerPictureFrame.FIRST_MATERIAL_SLOT, 0, ClickType.PICKUP, player);
        h.assertTrue(picture.frame.border.stack().is(Items.OAK_PLANKS), "frame material set");
        eq(h, menu.getCarried().getCount(), 4, "one block per picture");
        // Картину саму в себя не положить.
        menu.setCarried(new ItemStack(MinePapers.FRAME.get()));
        menu.clicked(ContainerPictureFrame.FIRST_MATERIAL_SLOT + 1, 0, ClickType.PICKUP, player);
        h.assertTrue(picture.frame.canvas.stack().isEmpty(), "picture is not a material");
        menu.setCarried(ItemStack.EMPTY);

        // Вид без рамки — блок рамки возвращается игроку при сохранении.
        PictureFrame wanted = new PictureFrame();
        wanted.readSettings(settings(picture.frame));
        wanted.mode = PictureFrame.MODE_DECAL;
        PacketSaveFrame.apply(player, new PacketSaveFrame(picture.getId(), settings(wanted)));
        h.assertTrue(picture.frame.border.stack().isEmpty(), "unused frame block taken out");
        eq(h, count(player, Items.OAK_PLANKS), 1, "and given back");
        leave(h, player);
        h.succeed();
    }

    public static void pasteTakesMaterialsFromInventory(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        EntityPicture picture = hang(h, player);
        player.getInventory().add(new ItemStack(Items.GLOWSTONE, 3));
        PictureConfig config = PictureConfig.fromJson("{\"materials\": {\"glow\": \"minecraft:glowstone\", \"canvas\": \"minecraft:diamond_block\"}}");
        PacketPasteMaterials.apply(player, PacketPasteMaterials.of(picture.getId(), config));
        h.assertTrue(picture.frame.glow.stack().is(Items.GLOWSTONE), "glow taken from the inventory");
        eq(h, count(player, Items.GLOWSTONE), 2, "one glowstone spent");
        h.assertTrue(picture.frame.canvas.stack().isEmpty(), "missing diamond block is not created in survival");
        leave(h, player);
        h.succeed();
    }

    // ---------- Предмет, рецепты, сохранение ----------

    public static void itemKeepsSettings(GameTestHelper h) {
        PictureFrame frame = new PictureFrame();
        frame.title = "Kept";
        frame.width = 4;
        frame.glow.set(new ItemStack(Items.SEA_LANTERN));
        ItemStack stack = ItemPictureFrame.makeStack(frame);
        PictureFrame read = ItemPictureFrame.read(stack);
        eq(h, settings(read), settings(frame), "settings");
        h.assertTrue(read.glow.stack().is(Items.SEA_LANTERN), "material item");
        h.assertTrue(ItemPictureFrame.settings(ItemPictureFrame.makeStack(new PictureFrame())) == null,
            "unconfigured picture has no settings and stacks with new ones");
        h.succeed();
    }

    public static void copyRecipe(GameTestHelper h) {
        //? if >=1.20.5 {
        RecipePictureFrame recipe = new RecipePictureFrame(CraftingBookCategory.MISC);
        //?} else {
        /*RecipePictureFrame recipe = new RecipePictureFrame(MinePapers.id("frame_copy"), CraftingBookCategory.MISC);
        *///?}
        PictureFrame frame = new PictureFrame();
        frame.title = "Copy me";
        frame.border.set(new ItemStack(Items.GOLD_BLOCK));
        ItemStack configured = ItemPictureFrame.makeStack(frame);
        ItemStack empty = new ItemStack(MinePapers.FRAME.get());
        List<ItemStack> grid = new ArrayList<ItemStack>();
        grid.add(configured);
        grid.add(empty.copy());
        grid.add(empty.copy());
        for (int i = 0; i < 6; i++) grid.add(ItemStack.EMPTY);
        ItemStack copies = recipe.assemble(grid(grid), h.getLevel().registryAccess());
        eq(h, copies.getCount(), 3, "configured + 2 empty = 3 copies");
        PictureFrame copy = ItemPictureFrame.read(copies);
        eq(h, copy.title, "Copy me", "settings copied");
        h.assertTrue(copy.border.stack().isEmpty(), "materials are not duplicated");

        // Заприваченную не копируют.
        frame.owner = UUID.randomUUID();
        grid.set(0, ItemPictureFrame.makeStack(frame));
        h.assertTrue(recipe.assemble(grid(grid), h.getLevel().registryAccess()).isEmpty(), "locked is not copied");

        // Подписанную тоже: ни копировать, ни разобрать. И она блестит, как заприваченная.
        frame.owner = null;
        frame.signer = UUID.randomUUID();
        frame.signerName = "Signer";
        ItemStack signed = ItemPictureFrame.makeStack(frame);
        h.assertTrue(signed.hasFoil(), "signed picture glints");
        grid.set(0, signed);
        h.assertTrue(recipe.assemble(grid(grid), h.getLevel().registryAccess()).isEmpty(), "signed is not copied");
        grid.set(1, ItemStack.EMPTY);
        grid.set(2, ItemStack.EMPTY);
        h.assertTrue(recipe.assemble(grid(grid), h.getLevel().registryAccess()).isEmpty(), "signed is not taken apart");

        // Разбор подписанной разрешён настройкой: сначала вынимаются материалы, затем сброс до пустой картины.
        com.grihail.minepapers.ModConfig.disassembleSigned = true;
        try {
            ItemStack graffiti = recipe.assemble(grid(grid), h.getLevel().registryAccess());
            h.assertTrue(ItemPictureFrame.settings(graffiti) != null && ItemPictureFrame.read(graffiti).border.stack().isEmpty(),
                "allowed: materials are taken out of the signed picture");
            grid.set(0, graffiti);
            ItemStack reset = recipe.assemble(grid(grid), h.getLevel().registryAccess());
            h.assertTrue(reset.is(MinePapers.FRAME.get()) && ItemPictureFrame.settings(reset) == null, "allowed: signed picture is reset");
        } finally {
            com.grihail.minepapers.ModConfig.disassembleSigned = false;
        }
        h.succeed();
    }

    //? if >=1.20.5 {
    private static CraftingInput grid(List<ItemStack> items) {
        return CraftingInput.of(3, 3, items);
    }
    //?} else {
    /*private static net.minecraft.world.inventory.TransientCraftingContainer grid(List<ItemStack> items) {
        return new net.minecraft.world.inventory.TransientCraftingContainer(null, 3, 3,
            net.minecraft.core.NonNullList.of(ItemStack.EMPTY, items.toArray(new ItemStack[0])));
    }
    *///?}

    public static void pictureSurvivesSaveAndLoad(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        EntityPicture picture = hang(h, player);
        picture.frame.title = "Saved";
        picture.frame.width = 2;
        picture.settingsChanged();
        CompoundTag saved = picture.saveWithoutId(new CompoundTag());
        EntityPicture loaded = new EntityPicture(MinePapers.PICTURE.get(), h.getLevel());
        loaded.load(saved);
        eq(h, settings(loaded.frame), settings(picture.frame), "settings");
        eq(h, loaded.getBoundingBox(), picture.getBoundingBox(), "hitbox");
        leave(h, player);
        h.succeed();
    }
}
