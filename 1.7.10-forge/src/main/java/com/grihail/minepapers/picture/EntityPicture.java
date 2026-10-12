package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.block.BlockPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketOpenFrame;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
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
            lightsDirty = true; // блоки ставим в onUpdate: при загрузке из сохранения мир ещё не готов
        }
    }

    // ---------- Свет ----------

    /** Где картина поставила свет (смещения от клетки рамки, см. FrameGeometry.lightCells). Только на сервере. */
    private final List<int[]> lights = new ArrayList<int[]>();
    private boolean lightsDirty;

    /** Светящаяся картина ставит перед холстом блоки света силы своего материала свечения; ненужные убирает. Чужие блоки не трогает: занятая клетка просто остаётся без света. */
    private void updateLights() {
        lightsDirty = false;
        int level = frame.glowLevel();
        List<int[]> wanted = level > 0 ? FrameGeometry.lightCells(frame) : new ArrayList<int[]>();
        for (int[] c : lights) {
            if (!contains(wanted, c)) removeLight(c);
        }
        lights.clear();
        for (int[] c : wanted) {
            if (placeLight(c, level)) lights.add(c);
        }
    }

    private boolean placeLight(int[] c, int level) {
        int x = frame.x + c[0], y = frame.y + c[1], z = frame.z + c[2];
        if (y < 0 || y >= worldObj.getHeight()) return false;
        Block block = worldObj.getBlock(x, y, z);
        if (block == MinePapers.light) {
            if (worldObj.getBlockMetadata(x, y, z) != level) worldObj.setBlock(x, y, z, MinePapers.light, level, 3);
            return true;
        }
        if (!worldObj.isAirBlock(x, y, z)) return false;
        return worldObj.setBlock(x, y, z, MinePapers.light, level, 3);
    }

    private void removeLight(int[] c) {
        int x = frame.x + c[0], y = frame.y + c[1], z = frame.z + c[2];
        if (worldObj.getBlock(x, y, z) == MinePapers.light) worldObj.setBlockToAir(x, y, z);
    }

    private void clearLights() {
        for (int[] c : lights) removeLight(c);
        lights.clear();
    }

    private static boolean contains(List<int[]> cells, int[] cell) {
        for (int[] c : cells) {
            if (c[0] == cell[0] && c[1] == cell[1] && c[2] == cell[2]) return true;
        }
        return false;
    }

    /** Этот блок света поставила эта картина (иначе он лишний и исчезает, см. BlockPictureLight). */
    public boolean ownsLight(int x, int y, int z) {
        return !isDead && contains(lights, new int[] { x - frame.x, y - frame.y, z - frame.z });
    }

    /** Картину убрали — убираем и её свет. Выгрузка чанка сюда не приходит: свет остаётся вместе с картиной. */
    @Override
    public void setDead() {
        if (worldObj != null && !worldObj.isRemote && !isDead) clearLights();
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

    /** Картина не движется и не горит; сервер обновляет свет после смены настроек, клиент подхватывает новые настройки с сервера. */
    @Override
    public void onUpdate() {
        if (!worldObj.isRemote) {
            if (lightsDirty) updateLights();
            return;
        }
        ItemStack stack = dataWatcher.getWatchableObjectItemStack(WATCH_SETTINGS);
        if (stack != shownSettings) {
            shownSettings = stack;
            if (stack != null && stack.hasTagCompound()) {
                frame.readSettings(stack.getTagCompound());
                refresh();
            }
        }
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

    /** Сломать: звук как у блока, дроп — предмет с настройками. Только на сервере. */
    public void breakPicture(boolean drop) {
        if (isDead) return;
        setDead();
        Block.SoundType sound = MinePapers.frame.stepSound;
        worldObj.playSoundEffect(posX, posY, posZ, sound.getBreakSound(),
            (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        if (drop && worldObj.getGameRules().getGameRuleBooleanValue("doTileDrops")) {
            entityDropItem(BlockPictureFrame.makeStack(frame), 0f);
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
        lights.clear();
        int[] flat = tag.getIntArray("Lights");
        for (int i = 0; i + 2 < flat.length; i += 3) lights.add(new int[] { flat[i], flat[i + 1], flat[i + 2] });
        settingsChanged();
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        frame.writePlacement(tag);
        NBTTagCompound settings = new NBTTagCompound();
        frame.writeSettings(settings);
        tag.setTag("Settings", settings);
        int[] flat = new int[lights.size() * 3];
        for (int i = 0; i < lights.size(); i++) System.arraycopy(lights.get(i), 0, flat, i * 3, 3);
        tag.setIntArray("Lights", flat);
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
        if (tag == null) return;
        frame.readPlacement(tag);
        frame.readSettings(tag);
        refresh();
    }
}
