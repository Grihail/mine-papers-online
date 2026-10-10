package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.network.PacketDistributor;

/** Картина — сущность, как ванильные картины и рамки: не занимает клеток, поэтому блоки и другие картины могут стоять вплотную и в той же клетке. Хитбокс — весь холст, сквозь него можно пройти. Опора не нужна: картина держится, даже если убрать блок за ней. Настройки синхронизируются с клиентами через SynchedEntityData. */
public class EntityPicture extends Entity implements IEntityWithComplexSpawn {

    private static final EntityDataAccessor<CompoundTag> SETTINGS =
        SynchedEntityData.defineId(EntityPicture.class, EntityDataSerializers.COMPOUND_TAG);
    /** Прочность как у деревянного блока. */
    public static final float HARDNESS = 0.5f;

    public final PictureFrame frame = new PictureFrame();

    /** Только на клиенте: насколько картину уже "откопали" (0..1) — для трещин. */
    public float breakProgress;

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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SETTINGS, new CompoundTag());
    }

    // ---------- Настройки и хитбокс ----------

    /** Настройки изменились: пересчитать хитбокс и (на сервере) разослать игрокам. */
    public void settingsChanged() {
        refresh();
        if (!level().isClientSide) {
            CompoundTag tag = new CompoundTag();
            frame.writeSettings(tag);
            entityData.set(SETTINGS, tag);
        }
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
                frame.readSettings(tag);
                refresh();
            }
        }
    }

    /** Картина не движется, не горит и не падает. */
    @Override
    public void tick() {}

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
        if (level().isClientSide) MinePapers.proxy.openFrameGui(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Удар игрока. В выживании картину "копают" (см. client.PictureControls), поэтому удар ничего не делает; в творческом режиме ломается сразу, как блок. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (level().isClientSide && attacker instanceof Player player && player.isCreative()) {
            PacketDistributor.sendToServer(new PacketBreakPicture(getId(), PacketBreakPicture.FINISH));
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

    /** Сломать: звук и частицы как у деревянного блока, дроп — предмет с настройками. Только на сервере. */
    public void breakPicture(boolean drop) {
        if (isRemoved() || !(level() instanceof ServerLevel server)) return;
        discard();
        AABB box = getBoundingBox();
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(MinePapers.FRAME.get())),
            getX(), getY(), getZ(), 24, box.getXsize() / 4, box.getYsize() / 4, box.getZsize() / 4, 0.05);
        SoundType sound = SoundType.WOOD;
        server.playSound(null, getX(), getY(), getZ(), sound.getBreakSound(), SoundSource.BLOCKS,
            (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        if (drop && server.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            spawnAtLocation(ItemPictureFrame.makeStack(frame));
        }
    }

    /** Доля поломки за тик — та же формула, что у деревянного блока прочностью HARDNESS: топор быстрее, в воде и в прыжке медленнее. */
    public float breakRate(Player player) {
        return player.getDigSpeed(Blocks.OAK_PLANKS.defaultBlockState(), null) / HARDNESS / 30f;
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
        settingsChanged();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        frame.writePlacement(tag);
        CompoundTag settings = new CompoundTag();
        frame.writeSettings(settings);
        tag.put("Settings", settings);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = new CompoundTag();
        frame.writePlacement(tag);
        frame.writeSettings(tag);
        buf.writeNbt(tag);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) return;
        frame.readPlacement(tag);
        frame.readSettings(tag);
        refresh();
    }
}
