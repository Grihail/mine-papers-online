package com.grihail.minepapers.gametest;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Игровые тесты картины на сервере: установка, ломание, свет, пакеты окна, права, рецепты, сохранение. Запуск — gradlew runGameTestServer (или build.bat test): сервер прогоняет все тесты и выходит с ошибкой, если какой-то упал. */
@GameTestHolder(MinePapers.MODID)
@PrefixGameTestTemplate(false)
public class PictureGameTests {

    /** Пустая площадка 6x6x6 (data/minepapers/structure/empty.nbt). */
    private static final String EMPTY = "empty";
    /** Стена (камень) в площадке; картина вешается на её южную грань — в клетку WALL.south(). */
    private static final BlockPos WALL = new BlockPos(2, 1, 2);

    // ---------- Помощники ----------

    /** Игрок на сервере с поддельным соединением (как GameTestHelper.makeMockServerPlayerInLevel, но и в выживании), стоит перед стеной. */
    private static ServerPlayer player(GameTestHelper h, boolean creative) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "tester"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
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
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
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
        h.assertValueEqual(pictures.size(), 1, "pictures");
        return pictures.get(0);
    }

    private static List<EntityPicture> pictures(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(EntityPicture.class, h.getBounds(), e -> !e.isRemoved());
    }

    private static List<ItemEntity> drops(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(2),
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

    @GameTest(template = EMPTY)
    public static void hangsOnWallFace(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        ItemStack stack = new ItemStack(MinePapers.FRAME.get(), 2);
        h.setBlock(WALL, Blocks.STONE);
        h.assertTrue(use(h, player, stack).consumesAction(), "placed");
        EntityPicture picture = pictures(h).get(0);
        BlockPos cell = h.absolutePos(WALL.south());
        h.assertValueEqual(new BlockPos(picture.frame.x, picture.frame.y, picture.frame.z), cell, "frame cell");
        h.assertValueEqual(picture.frame.getFacing(), Direction.SOUTH.get3DDataValue(), "facing");
        h.assertValueEqual(stack.getCount(), 1, "one item spent in survival");
        AABB box = picture.getBoundingBox();
        h.assertTrue(Math.abs(box.minZ - cell.getZ()) < 1e-6 && box.getZsize() < 0.1, "hitbox lies on the wall: " + box);

        // На то же место вторую не повесить: холсты пересеклись бы.
        h.assertTrue(!use(h, player, stack).consumesAction(), "second picture is blocked");
        h.assertValueEqual(pictures(h).size(), 1, "pictures");
        leave(h, player);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void canvasCannotGoThroughBlocks(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        PictureFrame big = new PictureFrame();
        big.width = 3;
        h.setBlock(WALL, Blocks.STONE);
        h.setBlock(WALL.south().east(), Blocks.STONE); // справа от картины, на пути холста
        h.assertTrue(!use(h, player, ItemPictureFrame.makeStack(big)).consumesAction(), "3 blocks wide canvas hits the block");
        h.assertValueEqual(pictures(h).size(), 0, "pictures");
        leave(h, player);
        h.succeed();
    }

    // ---------- Ломание ----------

    @GameTest(template = EMPTY)
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
            h.assertValueEqual(drops(h).size(), 1, "picture item dropped");
            leave(h, player);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void creativeBreaksWithoutDrop(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        EntityPicture picture = hang(h, player);
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getId(), PacketBreakPicture.FINISH));
        h.assertTrue(picture.isRemoved(), "broken at once");
        h.assertValueEqual(drops(h).size(), 0, "no drop in creative");
        leave(h, player);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void explosionDropsConfiguredItem(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        EntityPicture picture = hang(h, player);
        picture.frame.title = "Boom";
        picture.settingsChanged();
        h.assertTrue(!picture.hurt(h.getLevel().damageSources().generic(), 5f), "ordinary damage does nothing");
        picture.hurt(h.getLevel().damageSources().explosion(null, null), 5f);
        h.assertTrue(picture.isRemoved(), "explosion breaks");
        List<ItemEntity> drops = drops(h);
        h.assertValueEqual(drops.size(), 1, "drops");
        h.assertValueEqual(ItemPictureFrame.read(drops.get(0).getItem()).title, "Boom", "settings kept in the item");
        leave(h, player);
        h.succeed();
    }

    // ---------- Свет ----------

    @GameTest(template = EMPTY)
    public static void glowingPicturePlacesAndRemovesLight(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        EntityPicture picture = hang(h, player);
        picture.frame.glow.set(new ItemStack(Items.GLOWSTONE));
        picture.settingsChanged();
        BlockPos cell = WALL.south();
        h.runAfterDelay(2, () -> {
            h.assertBlockPresent(Blocks.LIGHT, cell);
            h.assertValueEqual(h.getBlockState(cell).getValue(net.minecraft.world.level.block.LightBlock.LEVEL), 15, "light level");
            picture.discard();
            h.assertBlockNotPresent(Blocks.LIGHT, cell);
            leave(h, player);
            h.succeed();
        });
    }

    // ---------- Окно настройки ----------

    @GameTest(template = EMPTY)
    public static void lockKeepsOthersOut(GameTestHelper h) {
        ServerPlayer alice = player(h, false), bob = player(h, false);
        EntityPicture picture = hang(h, alice);

        PictureFrame wanted = new PictureFrame();
        wanted.readSettings(settings(picture.frame));
        wanted.title = "Mine";
        wanted.owner = bob.getUUID(); // клиент может прислать любое имя — владельцем станет отправитель
        PacketSaveFrame.apply(alice, new PacketSaveFrame(picture.getId(), settings(wanted)));
        h.assertValueEqual(picture.frame.title, "Mine", "title saved");
        h.assertValueEqual(picture.frame.owner, alice.getUUID(), "owner is the sender");

        wanted.title = "Hacked";
        wanted.owner = null;
        PacketSaveFrame.apply(bob, new PacketSaveFrame(picture.getId(), settings(wanted)));
        h.assertValueEqual(picture.frame.title, "Mine", "stranger cannot change a locked picture");
        h.assertValueEqual(picture.frame.owner, alice.getUUID(), "stranger cannot unlock");

        PacketSaveFrame.apply(alice, new PacketSaveFrame(picture.getId(), settings(wanted)));
        h.assertTrue(picture.frame.owner == null, "owner unlocks");
        leave(h, alice, bob);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void tooFarCannotEdit(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        EntityPicture picture = hang(h, player);
        player.moveTo(player.getX(), player.getY() + 40, player.getZ());
        PictureFrame wanted = new PictureFrame();
        wanted.title = "Far";
        PacketSaveFrame.apply(player, new PacketSaveFrame(picture.getId(), settings(wanted)));
        h.assertValueEqual(picture.frame.title, "", "out of reach");
        leave(h, player);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void materialSlotsTakeAndGiveBack(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        EntityPicture picture = hang(h, player);
        ContainerPictureFrame menu = new ContainerPictureFrame(1, player.getInventory(), FrameHolder.of(picture, player), true);

        // Блок из руки мыши — в слот рамки.
        menu.setCarried(new ItemStack(Items.OAK_PLANKS, 5));
        menu.clicked(ContainerPictureFrame.FIRST_MATERIAL_SLOT, 0, ClickType.PICKUP, player);
        h.assertTrue(picture.frame.border.stack().is(Items.OAK_PLANKS), "frame material set");
        h.assertValueEqual(menu.getCarried().getCount(), 4, "one block per picture");
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
        h.assertValueEqual(count(player, Items.OAK_PLANKS), 1, "and given back");
        leave(h, player);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pasteTakesMaterialsFromInventory(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        EntityPicture picture = hang(h, player);
        player.getInventory().add(new ItemStack(Items.GLOWSTONE, 3));
        PictureConfig config = PictureConfig.fromJson("{\"materials\": {\"glow\": \"minecraft:glowstone\", \"canvas\": \"minecraft:diamond_block\"}}");
        PacketPasteMaterials.apply(player, PacketPasteMaterials.of(picture.getId(), config));
        h.assertTrue(picture.frame.glow.stack().is(Items.GLOWSTONE), "glow taken from the inventory");
        h.assertValueEqual(count(player, Items.GLOWSTONE), 2, "one glowstone spent");
        h.assertTrue(picture.frame.canvas.stack().isEmpty(), "missing diamond block is not created in survival");
        leave(h, player);
        h.succeed();
    }

    // ---------- Предмет, рецепты, сохранение ----------

    @GameTest(template = EMPTY)
    public static void itemKeepsSettings(GameTestHelper h) {
        PictureFrame frame = new PictureFrame();
        frame.title = "Kept";
        frame.width = 4;
        frame.glow.set(new ItemStack(Items.SEA_LANTERN));
        ItemStack stack = ItemPictureFrame.makeStack(frame);
        PictureFrame read = ItemPictureFrame.read(stack);
        h.assertValueEqual(settings(read), settings(frame), "settings");
        h.assertTrue(read.glow.stack().is(Items.SEA_LANTERN), "material item");
        h.assertTrue(ItemPictureFrame.settings(ItemPictureFrame.makeStack(new PictureFrame())) == null,
            "unconfigured picture has no settings and stacks with new ones");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void copyRecipe(GameTestHelper h) {
        RecipePictureFrame recipe = new RecipePictureFrame(CraftingBookCategory.MISC);
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
        ItemStack copies = recipe.assemble(CraftingInput.of(3, 3, grid), h.getLevel().registryAccess());
        h.assertValueEqual(copies.getCount(), 3, "configured + 2 empty = 3 copies");
        PictureFrame copy = ItemPictureFrame.read(copies);
        h.assertValueEqual(copy.title, "Copy me", "settings copied");
        h.assertTrue(copy.border.stack().isEmpty(), "materials are not duplicated");

        // Заприваченную не копируют.
        frame.owner = UUID.randomUUID();
        grid.set(0, ItemPictureFrame.makeStack(frame));
        h.assertTrue(recipe.assemble(CraftingInput.of(3, 3, grid), h.getLevel().registryAccess()).isEmpty(), "locked is not copied");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pictureSurvivesSaveAndLoad(GameTestHelper h) {
        ServerPlayer player = player(h, true);
        EntityPicture picture = hang(h, player);
        picture.frame.title = "Saved";
        picture.frame.width = 2;
        picture.settingsChanged();
        CompoundTag saved = picture.saveWithoutId(new CompoundTag());
        EntityPicture loaded = new EntityPicture(MinePapers.PICTURE.get(), h.getLevel());
        loaded.load(saved);
        h.assertValueEqual(settings(loaded.frame), settings(picture.frame), "settings");
        h.assertValueEqual(loaded.getBoundingBox(), picture.getBoundingBox(), "hitbox");
        leave(h, player);
        h.succeed();
    }
}
