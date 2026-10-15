package com.grihail.minepapers.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
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
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

/**
 * Игровые тесты 1.12.2 (своего фреймворка игровых тестов в этой версии нет): отдельный мод, который запускается только на тестовом сервере (задача runGameTestServer, build.bat test). Когда сервер запустился, прогоняет все тесты по очереди, каждый на своём участке мира, и выключает сервер; упал хоть один — выход с кодом 1, и сборка с ошибкой.
 * Тесты те же, что у новых версий (modern/src/gametest PictureGameTests): тест — открытый статический метод без параметров с именем на test; отложенные проверки (свет, долгое ломание) — сменой времени мира и тиком картины.
 */
@Mod(modid = ServerGameTests.MODID, name = "Mine Papers Online game tests", version = "1.0", dependencies = "required-after:minepapers",
    acceptableRemoteVersions = "*")
public final class ServerGameTests {

    public static final String MODID = "minepapers_gametest";
    private static final Logger LOG = LogManager.getLogger(MODID);

    /** Мир и угол участка текущего теста; стена (камень) — в WALL от угла, картина вешается на её южную грань. */
    private static WorldServer world;
    private static BlockPos origin;
    private static final BlockPos WALL = new BlockPos(2, 1, 2);

    /** Клиент со свойством minepapers.screenshots (задача runClientScreenshots) снимает окно настройки, см. ClientScreenshots. */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide().isClient() && Boolean.getBoolean("minepapers.screenshots")) MinecraftForge.EVENT_BUS.register(new ClientScreenshots());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (!server.isDedicatedServer()) return; // тесты — только на тестовом выделенном сервере
        world = server.getWorld(0);
        List<String> failed = new ArrayList<String>();
        int index = 0, total = 0;
        for (Method m : ServerGameTests.class.getDeclaredMethods()) {
            int mod = m.getModifiers();
            if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod) || m.getParameterCount() != 0 || !m.getName().startsWith("test")) continue;
            total++;
            origin = world.getSpawnPoint().add(16 + 10 * index++, 0, 16);
            clearArea();
            try {
                m.invoke(null);
                LOG.info("[PASS] {}", m.getName());
            } catch (InvocationTargetException e) {
                failed.add(m.getName());
                LOG.error("[FAIL] " + m.getName(), e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        LOG.info("========= {} GAME TESTS COMPLETE: {} passed, {} failed {}", total, total - failed.size(), failed.size(), failed);
        if (!failed.isEmpty()) FMLCommonHandler.instance().exitJava(1, false);
        server.initiateShutdown();
    }

    // ---------- Помощники ----------

    /** Пустой участок 6x6x6 на каменном полу. */
    private static void clearArea() {
        for (BlockPos p : BlockPos.getAllInBox(origin, origin.add(5, 5, 5))) world.setBlockState(p, Blocks.AIR.getDefaultState());
        for (BlockPos p : BlockPos.getAllInBox(origin.down(), origin.add(5, -1, 5))) world.setBlockState(p, Blocks.STONE.getDefaultState());
        for (EntityPicture e : world.getEntitiesWithinAABB(EntityPicture.class, area().grow(2))) e.setDead();
        for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, area().grow(2))) e.setDead();
    }

    private static BlockPos at(BlockPos relative) {
        return origin.add(relative);
    }

    private static void setBlock(BlockPos relative, IBlockState state) {
        world.setBlockState(at(relative), state);
    }

    private static AxisAlignedBB area() {
        return new AxisAlignedBB(origin, origin.add(6, 6, 6));
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static void eq(Object actual, Object expected, String what) {
        check(Objects.equals(actual, expected), what + ": expected " + expected + ", got " + actual);
    }

    /** Игрок на сервере с поддельным соединением, стоит перед стеной лицом на север. */
    private static EntityPlayerMP player(boolean creative) {
        EntityPlayerMP player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "tester"));
        NetworkManager network = new NetworkManager(EnumPacketDirection.SERVERBOUND);
        new EmbeddedChannel(network);
        new NetHandlerPlayServer(world.getMinecraftServer(), network, player);
        player.capabilities.isCreativeMode = creative;
        BlockPos stand = at(WALL.south(2));
        player.setLocationAndAngles(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 180f, 0f);
        return player;
    }

    /** Повесить картину из stack на южную грань стены рукой игрока. */
    private static EnumActionResult use(EntityPlayerMP player, ItemStack stack) {
        player.setHeldItem(EnumHand.MAIN_HAND, stack);
        return stack.onItemUse(player, world, at(WALL), EnumHand.MAIN_HAND, EnumFacing.SOUTH, 0.5f, 0.5f, 1f);
    }

    private static EntityPicture hang(EntityPlayerMP player) {
        setBlock(WALL, Blocks.STONE.getDefaultState());
        eq(use(player, new ItemStack(MinePapers.frame)), EnumActionResult.SUCCESS, "picture is placed");
        List<EntityPicture> pictures = pictures();
        eq(pictures.size(), 1, "pictures");
        return pictures.get(0);
    }

    private static List<EntityPicture> pictures() {
        List<EntityPicture> alive = new ArrayList<EntityPicture>();
        for (EntityPicture e : world.getEntitiesWithinAABB(EntityPicture.class, area())) {
            if (!e.isDead) alive.add(e);
        }
        return alive;
    }

    private static List<EntityItem> drops() {
        List<EntityItem> found = new ArrayList<EntityItem>();
        for (EntityItem e : world.getEntitiesWithinAABB(EntityItem.class, area().grow(2))) {
            if (!e.isDead && e.getItem().getItem() == MinePapers.frame) found.add(e);
        }
        return found;
    }

    private static NBTTagCompound settings(PictureFrame frame) {
        NBTTagCompound tag = new NBTTagCompound();
        frame.writeSettings(tag);
        return tag;
    }

    private static int count(EntityPlayer player, Item item) {
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory) {
            if (s.getItem() == item) n += s.getCount();
        }
        return n;
    }

    private static PacketSaveFrame save(EntityPicture picture, PictureFrame wanted) {
        PictureFrame copy = new PictureFrame();
        copy.readSettings(settings(wanted));
        return new PacketSaveFrame(FrameHolder.of(picture, null).target, settings(copy));
    }

    // ---------- Установка ----------

    public static void testHangsOnWallFace() {
        EntityPlayerMP player = player(false);
        ItemStack stack = new ItemStack(MinePapers.frame, 2);
        setBlock(WALL, Blocks.STONE.getDefaultState());
        eq(use(player, stack), EnumActionResult.SUCCESS, "placed");
        EntityPicture picture = pictures().get(0);
        BlockPos cell = at(WALL.south());
        eq(new BlockPos(picture.frame.x, picture.frame.y, picture.frame.z), cell, "frame cell");
        eq(picture.frame.getFacing(), EnumFacing.SOUTH.getIndex(), "facing");
        eq(stack.getCount(), 1, "one item spent in survival");
        AxisAlignedBB box = picture.getEntityBoundingBox();
        check(Math.abs(box.minZ - cell.getZ()) < 1e-6 && box.maxZ - box.minZ < 0.1, "hitbox lies on the wall: " + box);

        // На то же место вторую не повесить: холсты пересеклись бы.
        check(use(player, stack) != EnumActionResult.SUCCESS, "second picture is blocked");
        eq(pictures().size(), 1, "pictures");
    }

    public static void testCanvasCannotGoThroughBlocks() {
        EntityPlayerMP player = player(true);
        PictureFrame big = new PictureFrame();
        big.width = 3;
        setBlock(WALL, Blocks.STONE.getDefaultState());
        setBlock(WALL.south().east(), Blocks.STONE.getDefaultState()); // справа от картины, на пути холста
        check(use(player, ItemPictureFrame.makeStack(big)) != EnumActionResult.SUCCESS, "3 blocks wide canvas hits the block");
        eq(pictures().size(), 0, "pictures");
    }

    // ---------- Ломание ----------

    public static void testSurvivalBreakingTakesTime() {
        EntityPlayerMP player = player(false);
        EntityPicture picture = hang(player);
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getEntityId(), PacketBreakPicture.START));
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getEntityId(), PacketBreakPicture.FINISH));
        check(!picture.isDead, "instant break is ignored");
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getEntityId(), PacketBreakPicture.START));
        world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime() + 60); // World.setTotalWorldTime — только на клиенте
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getEntityId(), PacketBreakPicture.FINISH));
        check(picture.isDead, "broken after digging");
        eq(drops().size(), 1, "picture item dropped");
    }

    public static void testCreativeBreaksWithoutDrop() {
        EntityPlayerMP player = player(true);
        EntityPicture picture = hang(player);
        PacketBreakPicture.apply(player, new PacketBreakPicture(picture.getEntityId(), PacketBreakPicture.FINISH));
        check(picture.isDead, "broken at once");
        eq(drops().size(), 0, "no drop in creative");
    }

    public static void testExplosionDropsConfiguredItem() {
        EntityPlayerMP player = player(true);
        EntityPicture picture = hang(player);
        picture.frame.title = "Boom";
        picture.settingsChanged();
        check(!picture.attackEntityFrom(DamageSource.GENERIC, 5f), "ordinary damage does nothing");
        picture.attackEntityFrom(DamageSource.causeExplosionDamage((Explosion) null), 5f);
        check(picture.isDead, "explosion breaks");
        List<EntityItem> drops = drops();
        eq(drops.size(), 1, "drops");
        eq(FrameHolder.read(drops.get(0).getItem()).title, "Boom", "settings kept in the item");
    }

    // ---------- Свет ----------

    public static void testGlowingPicturePlacesAndRemovesLight() {
        EntityPlayerMP player = player(true);
        EntityPicture picture = hang(player);
        picture.frame.glow.set(new ItemStack(Blocks.GLOWSTONE));
        picture.settingsChanged();
        picture.onUpdate();
        picture.onUpdate();
        BlockPos cell = at(WALL.south());
        IBlockState state = world.getBlockState(cell);
        eq(state.getBlock(), MinePapers.light, "light block");
        eq(state.getValue(com.grihail.minepapers.block.BlockPictureLight.LEVEL), 15, "light level");
        picture.setDead();
        check(world.getBlockState(cell).getBlock() != MinePapers.light, "light removed with the picture");
    }

    // ---------- Окно настройки ----------

    public static void testLockKeepsOthersOut() {
        EntityPlayerMP alice = player(false), bob = player(false);
        EntityPicture picture = hang(alice);

        PictureFrame wanted = new PictureFrame();
        wanted.readSettings(settings(picture.frame));
        wanted.title = "Mine";
        wanted.owner = bob.getUniqueID(); // клиент может прислать любое имя — владельцем станет отправитель
        PacketSaveFrame.apply(alice, save(picture, wanted));
        eq(picture.frame.title, "Mine", "title saved");
        eq(picture.frame.owner, alice.getUniqueID(), "owner is the sender");

        wanted.title = "Hacked";
        wanted.owner = null;
        PacketSaveFrame.apply(bob, save(picture, wanted));
        eq(picture.frame.title, "Mine", "stranger cannot change a locked picture");
        eq(picture.frame.owner, alice.getUniqueID(), "stranger cannot unlock");

        PacketSaveFrame.apply(alice, save(picture, wanted));
        check(picture.frame.owner == null, "owner unlocks");
    }

    public static void testTooFarCannotEdit() {
        EntityPlayerMP player = player(false);
        EntityPicture picture = hang(player);
        player.setLocationAndAngles(player.posX, player.posY + 40, player.posZ, 0f, 0f);
        PictureFrame wanted = new PictureFrame();
        wanted.title = "Far";
        PacketSaveFrame.apply(player, save(picture, wanted));
        eq(picture.frame.title, "", "out of reach");
    }

    public static void testMaterialSlotsTakeAndGiveBack() {
        EntityPlayerMP player = player(false);
        EntityPicture picture = hang(player);
        ContainerPictureFrame menu = new ContainerPictureFrame(player.inventory, FrameHolder.of(picture, player), true);

        // Блок из руки мыши — в слот рамки.
        player.inventory.setItemStack(new ItemStack(Blocks.PLANKS, 5));
        menu.slotClick(ContainerPictureFrame.FIRST_MATERIAL_SLOT, 0, ClickType.PICKUP, player);
        eq(picture.frame.border.stack.getItem(), Item.getItemFromBlock(Blocks.PLANKS), "frame material set");
        eq(player.inventory.getItemStack().getCount(), 4, "one block per picture");
        // Картину саму в себя не положить.
        player.inventory.setItemStack(new ItemStack(MinePapers.frame));
        menu.slotClick(ContainerPictureFrame.FIRST_MATERIAL_SLOT + 1, 0, ClickType.PICKUP, player);
        check(picture.frame.canvas.stack.isEmpty(), "picture is not a material");
        player.inventory.setItemStack(ItemStack.EMPTY);

        // Вид без рамки — блок рамки возвращается игроку при сохранении.
        PictureFrame wanted = new PictureFrame();
        wanted.readSettings(settings(picture.frame));
        wanted.mode = PictureFrame.MODE_DECAL;
        PacketSaveFrame.apply(player, save(picture, wanted));
        check(picture.frame.border.stack.isEmpty(), "unused frame block taken out");
        eq(count(player, Item.getItemFromBlock(Blocks.PLANKS)), 1, "and given back");
    }

    public static void testPasteTakesMaterialsFromInventory() {
        EntityPlayerMP player = player(false);
        EntityPicture picture = hang(player);
        player.inventory.addItemStackToInventory(new ItemStack(Blocks.GLOWSTONE, 3));
        PictureConfig config = PictureConfig.fromJson("{\"materials\": {\"glow\": \"minecraft:glowstone\", \"canvas\": \"minecraft:diamond_block\"}}");
        PacketPasteMaterials.apply(player, new PacketPasteMaterials(picture.getEntityId(), config));
        eq(picture.frame.glow.stack.getItem(), Item.getItemFromBlock(Blocks.GLOWSTONE), "glow taken from the inventory");
        eq(count(player, Item.getItemFromBlock(Blocks.GLOWSTONE)), 2, "one glowstone spent");
        check(picture.frame.canvas.stack.isEmpty(), "missing diamond block is not created in survival");
    }

    // ---------- Предмет, рецепты, сохранение ----------

    public static void testItemKeepsSettings() {
        PictureFrame frame = new PictureFrame();
        frame.title = "Kept";
        frame.width = 4;
        frame.glow.set(new ItemStack(Blocks.SEA_LANTERN));
        ItemStack stack = ItemPictureFrame.makeStack(frame);
        PictureFrame read = FrameHolder.read(stack);
        eq(settings(read), settings(frame), "settings");
        eq(read.glow.stack.getItem(), Item.getItemFromBlock(Blocks.SEA_LANTERN), "material item");
        check(ItemPictureFrame.settings(ItemPictureFrame.makeStack(new PictureFrame())) == null,
            "unconfigured picture has no settings and stacks with new ones");
        // Свет блоков, у которых своего предмета нет, — по включённому варианту.
        eq(PictureFrame.lightOf(new ItemStack(Blocks.REDSTONE_LAMP)), 15, "redstone lamp glows when lit");
    }

    public static void testCopyRecipe() {
        RecipePictureFrame recipe = new RecipePictureFrame();
        PictureFrame frame = new PictureFrame();
        frame.title = "Copy me";
        frame.border.set(new ItemStack(Blocks.GOLD_BLOCK));
        ItemStack configured = ItemPictureFrame.makeStack(frame);
        ItemStack empty = new ItemStack(MinePapers.frame);
        ItemStack[] grid = { configured, empty.copy(), empty.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
        ItemStack copies = recipe.getCraftingResult(grid(grid));
        eq(copies.getCount(), 3, "configured + 2 empty = 3 copies");
        PictureFrame copy = FrameHolder.read(copies);
        eq(copy.title, "Copy me", "settings copied");
        check(copy.border.stack.isEmpty(), "materials are not duplicated");

        // Заприваченную не копируют.
        frame.owner = UUID.randomUUID();
        grid[0] = ItemPictureFrame.makeStack(frame);
        check(recipe.getCraftingResult(grid(grid)).isEmpty(), "locked is not copied");

        // Подписанную тоже: ни копировать, ни разобрать. И она блестит, как заприваченная.
        frame.owner = null;
        frame.signer = UUID.randomUUID();
        frame.signerName = "Signer";
        ItemStack signed = ItemPictureFrame.makeStack(frame);
        check(ItemPictureFrame.isSigned(signed), "signed picture glints");
        grid[0] = signed;
        check(recipe.getCraftingResult(grid(grid)).isEmpty(), "signed is not copied");
        grid[1] = ItemStack.EMPTY;
        grid[2] = ItemStack.EMPTY;
        check(recipe.getCraftingResult(grid(grid)).isEmpty(), "signed is not taken apart");

        // Разбор подписанной разрешён настройкой: сначала вынимаются материалы, затем сброс до пустой картины.
        ModConfig.disassembleSigned = true;
        try {
            ItemStack graffiti = recipe.getCraftingResult(grid(grid));
            check(ItemPictureFrame.settings(graffiti) != null && FrameHolder.read(graffiti).border.stack.isEmpty(),
                "allowed: materials are taken out of the signed picture");
            grid[0] = graffiti;
            ItemStack reset = recipe.getCraftingResult(grid(grid));
            check(reset.getItem() == MinePapers.frame && ItemPictureFrame.settings(reset) == null, "allowed: signed picture is reset");
        } finally {
            ModConfig.disassembleSigned = false;
        }
    }

    private static InventoryCrafting grid(ItemStack[] items) {
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        }, 3, 3);
        for (int i = 0; i < items.length; i++) grid.setInventorySlotContents(i, items[i].copy());
        return grid;
    }

    public static void testPictureSurvivesSaveAndLoad() {
        EntityPlayerMP player = player(true);
        EntityPicture picture = hang(player);
        picture.frame.title = "Saved";
        picture.frame.width = 2;
        picture.settingsChanged();
        NBTTagCompound saved = picture.writeToNBT(new NBTTagCompound());
        EntityPicture loaded = new EntityPicture(world);
        loaded.readFromNBT(saved);
        eq(settings(loaded.frame), settings(picture.frame), "settings");
        eq(loaded.getEntityBoundingBox(), picture.getEntityBoundingBox(), "hitbox");
    }
}
