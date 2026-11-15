package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.block.BlockPictureFrame;
import com.grihail.minepapers.client.PictureManager;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketOpenFrame;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Картина — сущность, как ванильные картины и рамки: не занимает клеток, поэтому блоки и другие картины могут стоять вплотную и в той же клетке. Хитбокс — весь холст, сквозь него можно пройти. Опора не нужна: картина держится, даже если убрать блок за ней. Настройки синхронизируются с клиентами через DataWatcher (предмет с настройками в NBT). */
public class EntityPicture extends Entity implements IEntityAdditionalSpawnData {

    private static final int WATCH_SETTINGS = 10;
    /** Номер слайда в режиме редстоуна (PictureSettings.redstoneStep) — отдельно от настроек: меняется с каждым сигналом. */
    private static final int WATCH_STEP = 11;
    /** Прочность как у деревянного блока. */
    public static final float HARDNESS = 0.5f;

    public final PictureFrame frame = new PictureFrame();
    /** Кто выложил на сервер картинки этой картины с компьютера (ServerImages.shareFiles) и его ник: его клиент просят выложить их заново при перезагрузке (server.PictureReload). Только на сервере; null — неизвестно (картина прежних версий). */
    public UUID uploader;
    public String uploaderName = "";
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
        dataWatcher.addObject(WATCH_STEP, Integer.valueOf(0));
    }

    // ---------- Настройки и хитбокс ----------

    /** Настройки изменились: пересчитать хитбокс и (на сервере) разослать игрокам и обновить свет. */
    public void settingsChanged() {
        refresh();
        centerTick = -1;
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

    // ---------- Редстоун ----------

    /** Был ли сигнал при прошлой проверке слева и справа от центра картины (LEFT, RIGHT — биты); -1 — ещё не проверяли (режим только включили или картину только поставили): первая проверка только запоминает сигнал, слайд не листает. */
    private int powered = -1;
    private static final int LEFT = 1, RIGHT = 2;
    /** Тик мира, в который картина последний раз листала слайд: сигнал, который дошёл до нескольких кусков пазла, листает группу один раз. */
    private long lastStep = -1;
    /** Центр картины (у пазла — всей группы) по горизонтали, в координатах стены (CanvasGeometry: u — вправо, как видит смотрящий), и тик, когда он посчитан (-1 — пересчитать). */
    private double centerU;
    private int centerTick = -1;

    /** Режим редстоуна (PictureSettings.redstone): новый сигнал справа от центра картины (или в самом центре) — следующий слайд, слева — предыдущий. Сигнал ловится как у лампы, но по всей площади картины: в клетках её холста и в блоках стены за ним; сторону задаёт то, откуда он пришёл. Поэтому подходит всё, что рядом: кнопка или рычаг на стене возле картины, нажимная плита под ней, провод, повторитель или факел, подведённые к стене сзади. Большие картины проверяются реже, чтобы не нагружать сервер. Только на сервере. */
    private void tickRedstone() {
        if (!frame.redstone) {
            powered = -1;
            return;
        }
        int[] r = redstoneArea();
        int cells = (r[3] - r[0] + 1) * (r[4] - r[1] + 1) * (r[5] - r[2] + 1);
        int every = Math.min(8, 1 + cells / 128);
        if (powered >= 0 && ticksExisted % every != 0) return;
        // Чанки вокруг картины не загружаем: не загружены — ждём.
        if (!worldObj.checkChunksExist(r[0] - 2, r[1] - 2, r[2] - 2, r[3] + 2, r[4] + 2, r[5] + 2)) return;
        int now = poweredSides(r);
        if (powered >= 0) {
            int rising = now & ~powered;
            // Сигнал сразу с обеих сторон (например, провод через всю стену) — вперёд.
            if ((rising & RIGHT) != 0) {
                stepSlide(1);
            } else if ((rising & LEFT) != 0) {
                stepSlide(-1);
            }
        }
        powered = now;
    }

    /** Клетки, где ловится сигнал, {minX, minY, minZ, maxX, maxY, maxZ}: клетки холста и слой за ними, в стене. */
    private int[] redstoneArea() {
        double e = 1e-4;
        AxisAlignedBB box = FrameGeometry.canvasBox(frame).contract(e, e, e);
        ForgeDirection back = ForgeDirection.getOrientation(frame.getFacing()).getOpposite();
        int minX = MathHelper.floor_double(box.minX), minY = MathHelper.floor_double(box.minY), minZ = MathHelper.floor_double(box.minZ);
        int maxX = MathHelper.floor_double(box.maxX), maxY = MathHelper.floor_double(box.maxY), maxZ = MathHelper.floor_double(box.maxZ);
        return new int[] { Math.min(minX, minX + back.offsetX), Math.min(minY, minY + back.offsetY), Math.min(minZ, minZ + back.offsetZ),
            Math.max(maxX, maxX + back.offsetX), Math.max(maxY, maxY + back.offsetY), Math.max(maxZ, maxZ + back.offsetZ) };
    }

    /** Откуда в клетки области r приходит сигнал (как World.isBlockIndirectlyGettingPowered, но с каждой из шести сторон по отдельности): LEFT, RIGHT или оба — по тому, левее центра картины блок, который его даёт, или нет. */
    private int poweredSides(int[] r) {
        double center = centerU();
        double[] o = FrameGeometry.origin(frame);
        int facing = frame.getFacing(), rotation = frame.getRotation(), sides = 0;
        int y0 = Math.max(0, r[1]), y1 = Math.min(worldObj.getHeight() - 1, r[4]);
        for (int x = r[0]; x <= r[3]; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = r[2]; z <= r[5]; z++) {
                    for (int side = 0; side < 6; side++) {
                        ForgeDirection d = ForgeDirection.getOrientation(side);
                        int sx = x + d.offsetX, sy = y + d.offsetY, sz = z + d.offsetZ;
                        // Сторона у getIndirectPowerLevelTo — та, с которой источник смотрит на клетку (как в isBlockIndirectlyGettingPowered).
                        if (sy < 0 || sy >= worldObj.getHeight() || worldObj.getIndirectPowerLevelTo(sx, sy, sz, side) <= 0) continue;
                        double u = FrameGeometry.toWall(facing, rotation, new double[] { sx + 0.5 - o[0], sy + 0.5 - o[1], sz + 0.5 - o[2] })[0];
                        sides |= u < center - 1e-3 ? LEFT : RIGHT;
                        if (sides == (LEFT | RIGHT)) return sides;
                    }
                }
            }
        }
        return sides;
    }

    /** Центр картины по горизонтали (у пазла — всей группы) в координатах стены; группа пересобирается раз в две секунды и после смены настроек. */
    private double centerU() {
        if (centerTick < 0 || ticksExisted - centerTick >= 40) {
            PuzzleGroup group = frame.puzzle != 0 ? FrameGeometry.puzzle(worldObj, frame) : null;
            centerU = group != null ? (group.u0 + group.u1) / 2 : frame.getOffsetXBlocks() + frame.getWidthBlocks() / 2;
            centerTick = ticksExisted;
        }
        return centerU;
    }

    /** Следующий (dir = 1) или предыдущий (-1) слайд — у всей группы пазла сразу (слайдшоу у неё общее): номера кусков совпадают, от самого большого из них. */
    private void stepSlide(int dir) {
        long tick = worldObj.getTotalWorldTime();
        if (lastStep == tick) return;
        List<EntityPicture> group = FrameGeometry.puzzleMembers(worldObj, this);
        int step = frame.redstoneStep;
        for (EntityPicture p : group) step = Math.max(step, p.frame.redstoneStep);
        step += dir;
        setRedstoneStep(step);
        lastStep = tick;
        for (EntityPicture p : group) {
            if (!p.frame.redstone) continue;
            p.setRedstoneStep(step);
            p.lastStep = tick;
        }
    }

    /** Номер слайда в режиме редстоуна — на сервере; клиентам уходит через DataWatcher. */
    public void setRedstoneStep(int step) {
        frame.redstoneStep = step;
        dataWatcher.updateObject(WATCH_STEP, Integer.valueOf(step));
    }

    /** Картина не движется и не горит; сервер обновляет свет после смены настроек, клиент подхватывает новые настройки с сервера и держит картинку в памяти, пока сама сущность есть на клиенте - пока загружен её чанк (PictureManager.keepAlive). */
    @Override
    public void onUpdate() {
        if (!worldObj.isRemote) {
            lights.tick(frame, frame.glowLevel());
            tickRedstone();
            return;
        }
        PictureManager.keepAlive(frame);
        frame.redstoneStep = dataWatcher.getWatchableObjectInt(WATCH_STEP);
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
        setRedstoneStep(tag.getInteger("RedstoneStep"));
        powered = tag.hasKey("PoweredSides") ? tag.getInteger("PoweredSides") & (LEFT | RIGHT) : -1;
        uploader = PictureSettings.parseUuid(tag.getString("Uploader"));
        uploaderName = tag.getString("UploaderName");
        settingsChanged();
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        frame.writePlacement(tag);
        NBTTagCompound settings = new NBTTagCompound();
        frame.writeSettings(settings);
        tag.setTag("Settings", settings);
        tag.setIntArray("Lights", lights.save());
        if (frame.redstoneStep != 0) tag.setInteger("RedstoneStep", frame.redstoneStep);
        if (powered >= 0) tag.setInteger("PoweredSides", powered);
        if (uploader != null) {
            tag.setString("Uploader", uploader.toString());
            tag.setString("UploaderName", uploaderName);
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        frame.writePlacement(tag);
        frame.writeSettings(tag);
        tag.setInteger("RedstoneStep", frame.redstoneStep);
        ByteBufUtils.writeTag(buf, tag);
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        frame.readPlacement(tag);
        frame.readSettings(tag);
        frame.redstoneStep = tag.getInteger("RedstoneStep");
        refresh();
    }
}
