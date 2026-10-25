package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.block.BlockPictureLight;
import com.grihail.minepapers.client.PictureManager;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketOpenFrame;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

/** Картина — сущность, как ванильные картины и рамки: не занимает клеток, поэтому блоки и другие картины могут стоять вплотную и в той же клетке. Хитбокс — весь холст, сквозь него можно пройти. Опора не нужна: картина держится, даже если убрать блок за ней. Настройки синхронизируются с клиентами через EntityDataManager (предмет с настройками в NBT). */
public class EntityPicture extends Entity implements IEntityAdditionalSpawnData {

    private static final DataParameter<ItemStack> SETTINGS = EntityDataManager.createKey(EntityPicture.class, DataSerializers.ITEM_STACK);
    /** Прочность как у деревянного блока. */
    public static final float HARDNESS = 0.5f;
    /** Звуки и скорость ломания — как у досок: топор быстрее. */
    private static final IBlockState WOOD = Blocks.PLANKS.getDefaultState();

    public final PictureFrame frame = new PictureFrame();

    public EntityPicture(World world) {
        super(world);
    }

    /** Новая картина на месте и с настройками призрака. */
    public EntityPicture(World world, PictureFrame placed) {
        this(world);
        NBTTagCompound tag = new NBTTagCompound();
        placed.writePlacement(tag);
        placed.writeSettings(tag);
        frame.readPlacement(tag);
        frame.readSettings(tag);
        settingsChanged();
    }

    /** Название по ключу из общих словарей (как в 1.13+), а не по старому entity.*.name. */
    @Override
    @SuppressWarnings("deprecation")
    public String getName() {
        return I18n.translateToLocal("entity." + MinePapers.MODID + ".picture");
    }

    @Override
    protected void entityInit() {
        dataManager.register(SETTINGS, ItemStack.EMPTY);
    }

    // ---------- Настройки и хитбокс ----------

    /** Настройки изменились: пересчитать хитбокс и (на сервере) разослать игрокам и обновить свет. */
    public void settingsChanged() {
        refresh();
        if (world != null && !world.isRemote) {
            ItemStack stack = new ItemStack(MinePapers.frame);
            NBTTagCompound tag = new NBTTagCompound();
            frame.writeSettings(tag);
            stack.setTagCompound(tag);
            dataManager.set(SETTINGS, stack);
            lights.markDirty();
        }
    }

    /** Клиент: пришли новые настройки с сервера. */
    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (!SETTINGS.equals(key) || !world.isRemote) return;
        ItemStack stack = dataManager.get(SETTINGS);
        if (stack.hasTagCompound()) {
            frame.readSettings(stack.getTagCompound());
            refresh();
        }
    }

    // ---------- Свет ----------

    /** Светящаяся картина ставит перед холстом блоки света мода силы своего материала свечения (см. PictureLights). Чужие блоки не трогает: занятая клетка просто остаётся без света. */
    private final PictureLights lights = new PictureLights() {
        @Override
        protected boolean place(int x, int y, int z, int level) {
            if (y < 0 || y >= world.getHeight()) return false;
            BlockPos pos = new BlockPos(x, y, z);
            IBlockState state = world.getBlockState(pos);
            IBlockState light = MinePapers.light.getDefaultState().withProperty(BlockPictureLight.LEVEL, level);
            if (state.getBlock() == MinePapers.light) {
                if (state != light) world.setBlockState(pos, light, 3);
                return true;
            }
            if (!world.isAirBlock(pos)) return false;
            return world.setBlockState(pos, light, 3);
        }

        @Override
        protected void remove(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            if (world.getBlockState(pos).getBlock() == MinePapers.light) world.setBlockToAir(pos);
        }
    };

    /** Этот блок света поставила эта картина (иначе он лишний и исчезает, см. BlockPictureLight). */
    public boolean ownsLight(int x, int y, int z) {
        return !isDead && lights.owns(frame, x, y, z);
    }

    /** Картину убрали — убираем и её свет. Выгрузка чанка сюда не приходит: свет остаётся вместе с картиной. */
    @Override
    public void setDead() {
        if (world != null && !world.isRemote && !isDead) lights.clear(frame);
        super.setDead();
    }

    /** Хитбокс = холст, позиция сущности — его центр. */
    public void refresh() {
        AxisAlignedBB box = FrameGeometry.canvasBox(frame);
        setEntityBoundingBox(box);
        posX = (box.minX + box.maxX) / 2;
        posY = (box.minY + box.maxY) / 2;
        posZ = (box.minZ + box.maxZ) / 2;
        // Поиск сущностей смотрит только на соседние чанки в пределах MAX_ENTITY_RADIUS от их центра — большой картине нужен радиус побольше, иначе по её краю не попасть (так советует сам Forge).
        double radius = Math.max(box.maxX - box.minX, Math.max(box.maxY - box.minY, box.maxZ - box.minZ)) / 2 + 1;
        if (radius > World.MAX_ENTITY_RADIUS) World.MAX_ENTITY_RADIUS = radius;
    }

    /** Позицию задаёт место картины, а не то, что пришло извне (сохранение, пакеты трекера). */
    @Override
    public void setPosition(double x, double y, double z) {
        if (frame == null) { // ещё в конструкторе Entity
            super.setPosition(x, y, z);
            return;
        }
        refresh();
    }

    /** Картина не движется и не горит; сервер обновляет свет после смены настроек, клиент держит её картинку в памяти, пока сама сущность есть на клиенте - пока загружен её чанк (PictureManager.keepAlive). */
    @Override
    public void onUpdate() {
        if (!world.isRemote) {
            lights.tick(frame, frame.glowLevel());
        } else {
            PictureManager.keepAlive(frame);
        }
    }

    @Override
    public void move(MoverType type, double x, double y, double z) {}

    @Override
    public void addVelocity(double x, double y, double z) {}

    @Override
    public boolean canBePushed() {
        return false;
    }

    /** По картине можно кликнуть, но коллизии у неё нет (getCollisionBoundingBox() == null): сквозь неё проходят. */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public float getCollisionBorderSize() {
        return 0f;
    }

    @Override
    public boolean isInRangeToRenderDist(double distanceSq) {
        double r = 128 + Math.max(frame.getWidthBlocks(), frame.getHeightBlocks());
        return distanceSq < r * r;
    }

    // ---------- Взаимодействие ----------

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        // Окно с инвентарём открывает сервер, см. PacketOpenFrame. Клик приходит по разу на каждую руку — окно открывает основная.
        if (world.isRemote && hand == EnumHand.MAIN_HAND) MinePapers.network.sendToServer(new PacketOpenFrame(getEntityId()));
        return true;
    }

    /** Удар игрока. В выживании картину "копают" (см. client.PictureControls), поэтому удар ничего не делает; в творческом режиме ломается сразу, как блок. */
    @Override
    public boolean hitByEntity(Entity attacker) {
        // На клиенте сюда попадает только удар самого игрока (PlayerControllerMP.attackEntity).
        if (world.isRemote && attacker instanceof EntityPlayer && ((EntityPlayer) attacker).capabilities.isCreativeMode) {
            MinePapers.network.sendToServer(new PacketBreakPicture(getEntityId(), PacketBreakPicture.FINISH));
        }
        return true;
    }

    /** Взрывы ломают картину с дропом, как ломали блок; остальной урон не действует. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (world.isRemote || isDead || !source.isExplosion()) return false;
        breakPicture(true);
        return true;
    }

    /** Сломать: звук как у блока, дроп — предмет с настройками. Только на сервере. */
    public void breakPicture(boolean drop) {
        if (isDead) return;
        setDead();
        SoundType sound = SoundType.WOOD;
        world.playSound(null, posX, posY, posZ, sound.getBreakSound(), SoundCategory.BLOCKS,
            (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        if (drop && world.getGameRules().getBoolean("doTileDrops")) {
            entityDropItem(ItemPictureFrame.makeStack(frame), 0f);
        }
    }

    /** Доля поломки за тик — та же формула, что у блока (ForgeHooks.blockStrength): топор быстрее, в воде медленнее. */
    public float breakRate(EntityPlayer player) {
        return player.getDigSpeed(WOOD, new BlockPos(frame.x, frame.y, frame.z)) / HARDNESS / 30f;
    }

    /** Колёсико мыши в творческом режиме — копия картины вместе с настройками. */
    @Override
    public ItemStack getPickedResult(RayTraceResult target) {
        return ItemPictureFrame.makeStack(frame);
    }

    // ---------- Сохранение и отправка клиентам ----------

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        frame.readPlacement(tag);
        frame.readSettings(tag.getCompoundTag("Settings"));
        lights.load(tag.getIntArray("Lights"));
        settingsChanged();
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        frame.writePlacement(tag);
        NBTTagCompound settings = new NBTTagCompound();
        frame.writeSettings(settings);
        tag.setTag("Settings", settings);
        tag.setIntArray("Lights", lights.save());
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        frame.writePlacement(tag);
        frame.writeSettings(tag);
        ByteBufUtils.writeTag(buf, tag);
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        frame.readPlacement(tag);
        frame.readSettings(tag);
        refresh();
    }
}
