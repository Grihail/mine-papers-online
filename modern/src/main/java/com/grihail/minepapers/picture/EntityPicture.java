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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Картина — сущность, как ванильные картины и рамки: не занимает клеток, поэтому блоки и другие картины могут стоять вплотную и в той же клетке. Хитбокс — весь холст, сквозь него можно пройти. Опора не нужна: картина держится, даже если убрать блок за ней. Место и настройки синхронизируются с клиентами через SynchedEntityData — ванильно, без данных спавна движка. */
public class EntityPicture extends Entity {

    private static final EntityDataAccessor<CompoundTag> SETTINGS =
        SynchedEntityData.defineId(EntityPicture.class, EntityDataSerializers.COMPOUND_TAG);
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
        builder.define(SETTINGS, new CompoundTag());
    }
    //?} else {
    /*protected void defineSynchedData() {
        entityData.define(SETTINGS, new CompoundTag());
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
        if (!level().isClientSide) {
            CompoundTag tag = new CompoundTag();
            frame.writePlacement(tag);
            frame.writeSettings(tag);
            entityData.set(SETTINGS, tag);
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
        if (!level().isClientSide && reason.shouldDestroy()) lights.clear(frame);
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

    @Override
    protected AABB makeBoundingBox() {
        return frame == null ? super.makeBoundingBox() : FrameGeometry.canvasBox(frame);
    }

    /** Клиент подхватывает новые настройки с сервера. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SETTINGS.equals(key) && level().isClientSide) {
            CompoundTag tag = entityData.get(SETTINGS);
            if (!tag.isEmpty()) {
                frame.readPlacement(tag);
                frame.readSettings(tag);
                refresh();
            }
        }
    }

    /** Картина не движется, не горит и не падает; сервер обновляет свет после смены настроек, клиент держит в памяти картину рядом с игроком (PictureManager.keepAlive). */
    @Override
    public void tick() {
        if (!level().isClientSide) {
            lights.tick(frame, frame.glowLevel());
        } else if (level().getNearestPlayer(this, PictureManager.keepAliveRange(frame)) != null) {
            PictureManager.keepAlive(frame);
        }
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
        return PushReaction.IGNORE;
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

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // Окно с инвентарём открывает сервер, см. PacketOpenFrame.
        if (level().isClientSide) MinePapers.platform.sendToServer(new PacketOpenFrame(getId()));
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Удар игрока. В выживании картину "копают" (см. client.PictureControls), поэтому удар ничего не делает; в творческом режиме ломается сразу, как блок. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (level().isClientSide && attacker instanceof Player player && player.isCreative()) {
            MinePapers.platform.sendToServer(new PacketBreakPicture(getId(), PacketBreakPicture.FINISH));
        }
        return true;
    }

    /** Взрывы ломают картину с дропом, как ломали блок; остальной урон не действует. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        breakPicture(true);
        return true;
    }

    /** Сломать: звук как у деревянного блока, дроп — предмет с настройками. Только на сервере. */
    public void breakPicture(boolean drop) {
        if (isRemoved() || !(level() instanceof ServerLevel server)) return;
        discard();
        SoundType sound = SoundType.WOOD;
        server.playSound(null, getX(), getY(), getZ(), sound.getBreakSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        if (drop && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            spawnAtLocation(ItemPictureFrame.makeStack(frame));
        }
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

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        frame.readPlacement(tag);
        frame.readSettings(tag.getCompound("Settings"));
        lights.load(tag.getIntArray("Lights"));
        settingsChanged();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        frame.writePlacement(tag);
        CompoundTag settings = new CompoundTag();
        frame.writeSettings(settings);
        tag.put("Settings", settings);
        tag.putIntArray("Lights", lights.save());
    }
}
