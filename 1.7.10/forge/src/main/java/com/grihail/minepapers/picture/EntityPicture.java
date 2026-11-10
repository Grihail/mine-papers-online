package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.block.BlockPictureFrame;
import com.grihail.minepapers.client.PictureManager;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketOpenFrame;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/** Картина — сущность, как ванильные картины и рамки: не занимает клеток, поэтому блоки и другие картины могут стоять вплотную и в той же клетке. Хитбокс — весь холст, сквозь него можно пройти. Опора не нужна: картина держится, даже если убрать блок за ней. Настройки синхронизируются с клиентами через DataWatcher (предмет с настройками в NBT). */
public class EntityPicture extends Entity implements IEntityAdditionalSpawnData {

    private static final int WATCH_SETTINGS = 10;
    /** Прочность как у деревянного блока. */
    public static final float HARDNESS = 0.5f;

    public final PictureFrame frame = new PictureFrame();
    private ItemStack shownSettings;

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
    public String getCommandSenderName() {
        return StatCollector.translateToLocal("entity." + MinePapers.MODID + ".picture");
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObjectByDataType(WATCH_SETTINGS, 5);
    }

    // ---------- Настройки и хитбокс ----------

    /** Настройки изменились: пересчитать хитбокс и (на сервере) разослать игрокам и обновить свет. */
    public void settingsChanged() {
        refresh();
        if (worldObj != null && !worldObj.isRemote) {
            ItemStack stack = new ItemStack(Item.getItemFromBlock(MinePapers.frame));
            NBTTagCompound tag = new NBTTagCompound();
            frame.writeSettings(tag);
            stack.setTagCompound(tag);
            dataWatcher.updateObject(WATCH_SETTINGS, stack);
            lights.markDirty();
        }
    }

    // ---------- Свет ----------

    /** Светящаяся картина ставит перед холстом блоки света мода силы своего материала свечения (см. PictureLights). Чужие блоки не трогает: занятая клетка просто остаётся без света. */
    private final PictureLights lights = new PictureLights() {
        @Override
        protected boolean place(int x, int y, int z, int level) {
            if (y < 0 || y >= worldObj.getHeight()) return false;
            Block block = worldObj.getBlock(x, y, z);
            if (block == MinePapers.light) {
                if (worldObj.getBlockMetadata(x, y, z) != level) worldObj.setBlock(x, y, z, MinePapers.light, level, 3);
                return true;
            }
            if (!worldObj.isAirBlock(x, y, z)) return false;
            return worldObj.setBlock(x, y, z, MinePapers.light, level, 3);
        }

        @Override
        protected void remove(int x, int y, int z) {
            if (worldObj.getBlock(x, y, z) == MinePapers.light) worldObj.setBlockToAir(x, y, z);
        }
    };

    /** Этот блок света поставила эта картина (иначе он лишний и исчезает, см. BlockPictureLight). */
    public boolean ownsLight(int x, int y, int z) {
        return !isDead && lights.owns(frame, x, y, z);
    }

    /** Картину убрали — убираем и её свет. Выгрузка чанка сюда не приходит: свет остаётся вместе с картиной. */
    @Override
    public void setDead() {
        if (worldObj != null && !worldObj.isRemote && !isDead) lights.clear(frame);
        super.setDead();
    }

    /** Хитбокс = холст, позиция сущности — его центр. */
    public void refresh() {
        AxisAlignedBB box = FrameGeometry.canvasBox(frame);
        boundingBox.setBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
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

    /** Картина не движется и не горит; сервер обновляет свет после смены настроек, клиент подхватывает новые настройки с сервера и держит картинку в памяти, пока сама сущность есть на клиенте - пока загружен её чанк (PictureManager.keepAlive). */
    @Override
    public void onUpdate() {
        if (!worldObj.isRemote) {
            lights.tick(frame, frame.glowLevel());
            return;
        }
        PictureManager.keepAlive(frame);
        ItemStack stack = dataWatcher.getWatchableObjectItemStack(WATCH_SETTINGS);
        boolean changed = stack != shownSettings;
        if (changed) {
            shownSettings = stack;
            if (stack != null && stack.hasTagCompound()) {
                frame.readSettings(stack.getTagCompound());
                refresh();
            }
        }
        // Группа пазла — заново раз в секунду (куски могли повесить, снять или перекрасить) и сразу после смены своих настроек.
        if (changed || ticksExisted % 20 == 0) frame.clientPuzzle = frame.puzzle != 0 ? FrameGeometry.puzzle(worldObj, frame) : null;
    }

    @Override
    public void moveEntity(double x, double y, double z) {}

    @Override
    public void addVelocity(double x, double y, double z) {}

    @Override
    public boolean canBePushed() {
        return false;
    }

    /** По картине можно кликнуть, но коллизии у неё нет (getBoundingBox() == null): сквозь неё проходят. */
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
    public boolean interactFirst(EntityPlayer player) {
        // Окно с инвентарём открывает сервер, см. PacketOpenFrame.
        if (worldObj.isRemote) MinePapers.network.sendToServer(new PacketOpenFrame(getEntityId()));
        return true;
    }

    /** Удар игрока. В выживании картину "копают" (см. client.PictureControls), поэтому удар ничего не делает; в творческом режиме ломается сразу, как блок. */
    @Override
    public boolean hitByEntity(Entity attacker) {
        // На клиенте сюда попадает только удар самого игрока (PlayerControllerMP.attackEntity).
        if (worldObj.isRemote && attacker instanceof EntityPlayer && ((EntityPlayer) attacker).capabilities.isCreativeMode) {
            MinePapers.network.sendToServer(new PacketBreakPicture(getEntityId(), PacketBreakPicture.FINISH));
        }
        return true;
    }

    /** Взрывы ломают картину с дропом, как ломали блок; остальной урон не действует. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (worldObj.isRemote || isDead || !source.isExplosion()) return false;
        breakPicture(true);
        return true;
    }

    /** Сломать: звук рамки (PictureSounds), дроп — предмет с настройками. Только на сервере. */
    public void breakPicture(boolean drop) {
        if (isDead) return;
        setDead();
        PictureSounds.destroy(worldObj, posX, posY, posZ);
        if (drop && worldObj.getGameRules().getGameRuleBooleanValue("doTileDrops")) {
            // Перед холстом и без разлёта — предмет падает там, где висела картина.
            double[] p = FrameGeometry.dropPos(frame);
            EntityItem item = new EntityItem(worldObj, p[0], p[1], p[2], BlockPictureFrame.makeStack(frame));
            item.motionX = item.motionY = item.motionZ = 0;
            item.delayBeforeCanPickup = 10;
            worldObj.spawnEntityInWorld(item);
        }
    }

    /** Доля поломки за тик — та же формула, что у блока (ForgeHooks.blockStrength): топор быстрее, в воде медленнее. */
    public float breakRate(EntityPlayer player) {
        return player.getBreakSpeed(MinePapers.frame, false, 0, frame.x, frame.y, frame.z) / HARDNESS / 30f;
    }

    /** Колёсико мыши в творческом режиме — копия картины вместе с настройками. */
    @Override
    public ItemStack getPickedResult(MovingObjectPosition target) {
        return BlockPictureFrame.makeStack(frame);
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
