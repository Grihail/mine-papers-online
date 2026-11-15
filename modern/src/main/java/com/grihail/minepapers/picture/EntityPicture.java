package com.grihail.minepapers.picture;

import java.util.List;
import java.util.UUID;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.client.PictureManager;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketOpenFrame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
//? if >=26.1 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?} else {
import net.minecraft.world.level.GameRules;
//?}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Картина — сущность, как ванильные картины и рамки: не занимает клеток, поэтому блоки и другие картины могут стоять вплотную и в той же клетке. Хитбокс — весь холст, сквозь него можно пройти. Опора не нужна: картина держится, даже если убрать блок за ней. Место и настройки синхронизируются с клиентами через SynchedEntityData — ванильно, без данных спавна движка. */
public class EntityPicture extends Entity {

    //? if >=26.1 {
    /*// С 26.1 у данных сущности нет тега NBT; свой тип пришлось бы регистрировать у каждого движка, поэтому тег идёт строкой SNBT.
    private static final EntityDataAccessor<String> SETTINGS =
        SynchedEntityData.defineId(EntityPicture.class, EntityDataSerializers.STRING);
    private static final String NO_SETTINGS = "";

    private void setSynced(CompoundTag tag) {
        entityData.set(SETTINGS, tag.toString());
    }

    private CompoundTag synced() {
        String s = entityData.get(SETTINGS);
        if (s.isEmpty()) return new CompoundTag();
        try {
            return TagParser.parseCompoundFully(s);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return new CompoundTag();
        }
    }
    *///?} else {
    private static final EntityDataAccessor<CompoundTag> SETTINGS =
        SynchedEntityData.defineId(EntityPicture.class, EntityDataSerializers.COMPOUND_TAG);
    private static final CompoundTag NO_SETTINGS = new CompoundTag();

    private void setSynced(CompoundTag tag) {
        entityData.set(SETTINGS, tag);
    }

    private CompoundTag synced() {
        return entityData.get(SETTINGS);
    }
    //?}
    /** Номер слайда в режиме редстоуна (PictureSettings.redstoneStep) — отдельно от настроек: меняется с каждым сигналом. */
    private static final EntityDataAccessor<Integer> STEP = SynchedEntityData.defineId(EntityPicture.class, EntityDataSerializers.INT);
    /** Прочность как у деревянного блока. */
    public static final float HARDNESS = 0.5f;

    public final PictureFrame frame = new PictureFrame();
    /** Кто выложил на сервер картинки этой картины с компьютера (ServerImages.shareFiles) и его ник: его клиент просят выложить их заново при перезагрузке (server.PictureReload). Только на сервере; null — неизвестно (картина прежних версий). */
    public UUID uploader;
    public String uploaderName = "";

    public EntityPicture(EntityType<? extends EntityPicture> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Новая картина на месте и с настройками призрака. */
    public EntityPicture(Level level, PictureFrame placed) {
        this(MinePapers.PICTURE.get(), level);
        CompoundTag tag = new CompoundTag();
        placed.writePlacement(tag);
        placed.writeSettings(tag);
        frame.readPlacement(tag);
        frame.readSettings(tag);
        settingsChanged();
    }

    @Override
    //? if >=1.20.5 {
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SETTINGS, NO_SETTINGS);
        builder.define(STEP, 0);
    }
    //?} else {
    /*protected void defineSynchedData() {
        entityData.define(SETTINGS, NO_SETTINGS);
        entityData.define(STEP, 0);
    }
    *///?}

    //? if <1.20.2 {
    /*// До 1.20.2 пакет появления сущности у клиента задаёт сама сущность: обычный ванильный, место и настройки придут данными сущности.
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this);
    }
    *///?}

    // ---------- Настройки и хитбокс ----------

    /** Настройки изменились: пересчитать хитбокс и (на сервере) разослать игрокам вместе с местом картины и обновить свет. */
    public void settingsChanged() {
        refresh();
        centerTick = -1;
        if (!level().isClientSide()) {
            CompoundTag tag = new CompoundTag();
            frame.writePlacement(tag);
            frame.writeSettings(tag);
            setSynced(tag);
            lights.markDirty();
        }
    }

    // ---------- Свет ----------

    /** Светящаяся картина ставит перед холстом ванильные minecraft:light силы своего материала свечения (см. PictureLights). Чужие блоки не трогает: занятая клетка просто остаётся без света. */
    private final PictureLights lights = new PictureLights() {
        @Override
        protected boolean place(int x, int y, int z, int glow) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level().getBlockState(pos);
            BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, glow);
            if (state.is(Blocks.LIGHT)) {
                if (state.getValue(LightBlock.LEVEL) != glow) level().setBlock(pos, light, Block.UPDATE_ALL);
                return true;
            }
            return state.isAir() && level().setBlock(pos, light, Block.UPDATE_ALL);
        }

        @Override
        protected void remove(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level().getBlockState(pos).is(Blocks.LIGHT)) level().removeBlock(pos, false);
        }
    };

    /** Картину сломали или убрали — убираем и её свет. Выгрузка чанка и переход в другое измерение сюда не относятся. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()) lights.clear(frame);
        super.remove(reason);
    }

    /** Хитбокс = холст, позиция сущности — его центр. */
    public void refresh() {
        AABB box = FrameGeometry.canvasBox(frame);
        Vec3 center = box.getCenter();
        setPosRaw(center.x, center.y, center.z);
        setBoundingBox(box);
    }

    /** Позицию задаёт место картины, а не то, что пришло извне (сохранение, пакеты трекера). */
    @Override
    public void setPos(double x, double y, double z) {
        if (frame == null) { // ещё в конструкторе Entity
            super.setPos(x, y, z);
            return;
        }
        refresh();
    }

    //? if >=26.1 {
    /*@Override
    protected AABB makeBoundingBox(Vec3 position) {
        return frame == null ? super.makeBoundingBox(position) : FrameGeometry.canvasBox(frame);
    }
    *///?} else {
    @Override
    protected AABB makeBoundingBox() {
        return frame == null ? super.makeBoundingBox() : FrameGeometry.canvasBox(frame);
    }
    //?}

    /** Клиент подхватывает новые настройки с сервера. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SETTINGS.equals(key) && level().isClientSide()) {
            CompoundTag tag = synced();
            if (!tag.isEmpty()) {
                frame.readPlacement(tag);
                frame.readSettings(tag);
                refresh();
                puzzleChanged = true;
            }
        }
    }

    /** Картина не движется, не горит и не падает; сервер обновляет свет после смены настроек, клиент держит её картинку в памяти, пока сама сущность есть на клиенте - пока загружен её чанк (PictureManager.keepAlive). */
    @Override
    public void tick() {
        if (!level().isClientSide()) {
            lights.tick(frame, frame.glowLevel());
            tickRedstone();
        } else {
            PictureManager.keepAlive(frame);
            frame.redstoneStep = entityData.get(STEP);
            // Группа пазла — заново раз в секунду (куски могли повесить, снять или перенастроить) и сразу после смены своих настроек.
            if (puzzleChanged || tickCount % 20 == 0) frame.clientPuzzle = frame.puzzle != 0 ? FrameGeometry.puzzle(level(), frame) : null;
            puzzleChanged = false;
        }
    }

    /** Клиент: настройки только что пришли — группу пазла пересобрать сразу. */
    private boolean puzzleChanged = true;

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
        if (powered >= 0 && tickCount % every != 0) return;
        // Чанки вокруг картины не загружаем: не загружены — ждём.
        if (!level().hasChunksAt(r[0] - 2, r[2] - 2, r[3] + 2, r[5] + 2)) return;
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
        AABB box = FrameGeometry.canvasBox(frame).deflate(1e-4);
        int[] n = FrameGeometry.normal(frame);
        int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY), minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX), maxY = (int) Math.floor(box.maxY), maxZ = (int) Math.floor(box.maxZ);
        // Стена — против нормали картины.
        return new int[] { Math.min(minX, minX - n[0]), Math.min(minY, minY - n[1]), Math.min(minZ, minZ - n[2]),
            Math.max(maxX, maxX - n[0]), Math.max(maxY, maxY - n[1]), Math.max(maxZ, maxZ - n[2]) };
    }

    /** Откуда в клетки области r приходит сигнал (как Level.hasNeighborSignal, но с каждой из шести сторон по отдельности): LEFT, RIGHT или оба — по тому, левее центра картины блок, который его даёт, или нет. */
    private int poweredSides(int[] r) {
        double center = centerU();
        double[] o = FrameGeometry.origin(frame);
        int facing = frame.getFacing(), rotation = frame.getRotation(), sides = 0;
        Level level = level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = r[0]; x <= r[3]; x++) {
            for (int y = r[1]; y <= r[4]; y++) {
                if (level.isOutsideBuildHeight(y)) continue;
                for (int z = r[2]; z <= r[5]; z++) {
                    for (Direction d : Direction.values()) {
                        int sx = x + d.getStepX(), sy = y + d.getStepY(), sz = z + d.getStepZ();
                        // Как в hasNeighborSignal: соседний блок и сторона, с которой он смотрит на клетку.
                        if (level.isOutsideBuildHeight(sy) || level.getSignal(pos.set(sx, sy, sz), d) <= 0) continue;
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
        if (centerTick < 0 || tickCount - centerTick >= 40) {
            PuzzleGroup group = frame.puzzle != 0 ? FrameGeometry.puzzle(level(), frame) : null;
            centerU = group != null ? (group.u0 + group.u1) / 2 : frame.getOffsetXBlocks() + frame.getWidthBlocks() / 2;
            centerTick = tickCount;
        }
        return centerU;
    }

    /** Следующий (dir = 1) или предыдущий (-1) слайд — у всей группы пазла сразу (слайдшоу у неё общее): номера кусков совпадают, от самого большого из них. */
    private void stepSlide(int dir) {
        long tick = level().getGameTime();
        if (lastStep == tick) return;
        List<EntityPicture> group = FrameGeometry.puzzleMembers(level(), this);
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

    /** Номер слайда в режиме редстоуна — на сервере; клиентам уходит через SynchedEntityData. */
    public void setRedstoneStep(int step) {
        frame.redstoneStep = step;
        entityData.set(STEP, step);
    }

    @Override
    public void move(MoverType type, Vec3 movement) {}

    @Override
    public void push(double x, double y, double z) {}

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        //? if >=26.3 {
        /*return PushReaction.IGNORE_ENTITY;
        *///?} else {
        return PushReaction.IGNORE;
        //?}
    }

    /** По картине можно кликнуть, но коллизии у неё нет: сквозь неё проходят. */
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        double r = 128 + Math.max(frame.getWidthBlocks(), frame.getHeightBlocks());
        return distanceSq < r * r;
    }

    // ---------- Взаимодействие ----------

    //? if >=26.1 {
    /*@Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        // Окно с инвентарём открывает сервер, см. PacketOpenFrame.
        if (level().isClientSide()) MinePapers.platform.sendToServer(new PacketOpenFrame(getId()));
        return InteractionResult.SUCCESS;
    }
    *///?} else {
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // Окно с инвентарём открывает сервер, см. PacketOpenFrame.
        if (level().isClientSide()) MinePapers.platform.sendToServer(new PacketOpenFrame(getId()));
        return InteractionResult.sidedSuccess(level().isClientSide());
    }
    //?}

    /** Удар игрока. В выживании картину "копают" (см. client.PictureControls), поэтому удар ничего не делает; в творческом режиме ломается сразу, как блок. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (level().isClientSide() && attacker instanceof Player player && player.isCreative()) {
            MinePapers.platform.sendToServer(new PacketBreakPicture(getId(), PacketBreakPicture.FINISH));
        }
        return true;
    }

    /** Взрывы ломают картину с дропом, как ломали блок; остальной урон не действует. */
    //? if >=26.1 {
    /*@Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    *///?} else {
    @Override
    public boolean hurt(DamageSource source, float amount) {
    //?}
        if (level().isClientSide() || isRemoved() || !source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        breakPicture(true);
        return true;
    }

    /** Сломать: звук как у ванильной рамки для предметов, дроп — предмет с настройками. Только на сервере. */
    public void breakPicture(boolean drop) {
        if (isRemoved() || !(level() instanceof ServerLevel server)) return;
        discard();
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_FRAME_BREAK, SoundSource.BLOCKS, 1f, 1f);
        //? if >=26.1 {
        /*if (drop && server.getGameRules().get(GameRules.BLOCK_DROPS)) dropItem(server);
        *///?} else {
        if (drop && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) dropItem(server);
        //?}
    }

    /** Предмет картины — перед холстом и без разлёта: падает там, где она висела (FrameGeometry.dropPos). */
    private void dropItem(ServerLevel server) {
        double[] p = FrameGeometry.dropPos(frame);
        ItemEntity item = new ItemEntity(server, p[0], p[1], p[2], ItemPictureFrame.makeStack(frame), 0, 0, 0);
        item.setDefaultPickUpDelay();
        server.addFreshEntity(item);
    }

    /** Доля поломки за тик — та же формула, что у деревянного блока прочностью HARDNESS: топор быстрее, в воде и в прыжке медленнее. */
    public float breakRate(Player player) {
        return player.getDestroySpeed(Blocks.OAK_PLANKS.defaultBlockState()) / HARDNESS / 30f;
    }

    /** Колёсико мыши в творческом режиме — копия картины вместе с настройками. */
    @Override
    public ItemStack getPickResult() {
        return ItemPictureFrame.makeStack(frame);
    }

    // ---------- Сохранение и отправка клиентам ----------

    //? if >=26.1 {
    /*// С 26.1 сущность сохраняется через ValueInput/ValueOutput; ключи те же, что раньше, - картины из старых миров читаются.
    private static final MapCodec<CompoundTag> WHOLE_TAG = MapCodec.assumeMapUnsafe(CompoundTag.CODEC);

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        readSaved(input.read(WHOLE_TAG).orElseGet(CompoundTag::new));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        CompoundTag tag = new CompoundTag();
        writeSaved(tag);
        output.store(WHOLE_TAG, tag);
    }
    *///?} else {
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        readSaved(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        writeSaved(tag);
    }
    //?}

    private void readSaved(CompoundTag tag) {
        frame.readPlacement(tag);
        frame.readSettings(NbtTag.compound(tag, "Settings"));
        //? if >=26.1 {
        /*lights.load(tag.getIntArray("Lights").orElse(new int[0]));
        *///?} else {
        lights.load(tag.getIntArray("Lights"));
        //?}
        NbtTag t = new NbtTag(tag);
        setRedstoneStep(t.getInt("RedstoneStep"));
        powered = t.contains("PoweredSides") ? t.getInt("PoweredSides") & (LEFT | RIGHT) : -1;
        uploader = t.getUuid("Uploader");
        uploaderName = t.getString("UploaderName");
        settingsChanged();
    }

    private void writeSaved(CompoundTag tag) {
        frame.writePlacement(tag);
        CompoundTag settings = new CompoundTag();
        frame.writeSettings(settings);
        tag.put("Settings", settings);
        tag.putIntArray("Lights", lights.save());
        if (frame.redstoneStep != 0) tag.putInt("RedstoneStep", frame.redstoneStep);
        if (powered >= 0) tag.putInt("PoweredSides", powered);
        if (uploader != null) {
            new NbtTag(tag).putUuid("Uploader", uploader);
            tag.putString("UploaderName", uploaderName);
        }
    }
}
