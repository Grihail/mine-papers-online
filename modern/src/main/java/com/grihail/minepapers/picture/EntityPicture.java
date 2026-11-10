package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.client.PictureManager;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketOpenFrame;

import net.minecraft.core.BlockPos;
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
    /** Прочность как у деревянного блока. */
    public static final float HARDNESS = 0.5f;

    public final PictureFrame frame = new PictureFrame();

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
    }
    //?} else {
    /*protected void defineSynchedData() {
        entityData.define(SETTINGS, NO_SETTINGS);
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
        } else {
            PictureManager.keepAlive(frame);
            // Группа пазла — заново раз в секунду (куски могли повесить, снять или перенастроить) и сразу после смены своих настроек.
            if (puzzleChanged || tickCount % 20 == 0) frame.clientPuzzle = frame.puzzle != 0 ? FrameGeometry.puzzle(level(), frame) : null;
            puzzleChanged = false;
        }
    }

    /** Клиент: настройки только что пришли — группу пазла пересобрать сразу. */
    private boolean puzzleChanged = true;

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
        settingsChanged();
    }

    private void writeSaved(CompoundTag tag) {
        frame.writePlacement(tag);
        CompoundTag settings = new CompoundTag();
        frame.writeSettings(settings);
        tag.put("Settings", settings);
        tag.putIntArray("Lights", lights.save());
    }
}
