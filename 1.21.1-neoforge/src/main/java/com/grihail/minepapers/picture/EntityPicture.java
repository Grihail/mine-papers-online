package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.network.PacketOpenFrame;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
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

    /** Настройки изменились: пересчитать хитбокс и (на сервере) разослать игрокам и обновить свет. */
    public void settingsChanged() {
        refresh();
        if (!level().isClientSide) {
            CompoundTag tag = new CompoundTag();
            frame.writeSettings(tag);
            entityData.set(SETTINGS, tag);
            lightsDirty = true; // блоки ставим в tick: при загрузке из сохранения мир ещё не готов
        }
    }

    // ---------- Свет ----------

    /** Где картина поставила ванильные блоки света (смещения от клетки рамки, см. FrameGeometry.lightCells). Только на сервере. Обычные блоки, а не свет сущности: их освещение считает сам мир — и движущиеся конструкции других модов переносят и освещают их как любые блоки. */
    private final List<int[]> lights = new ArrayList<int[]>();
    private boolean lightsDirty;

    /** Светящаяся картина ставит перед холстом minecraft:light силы своего материала свечения; ненужные убирает. Чужие блоки не трогает: занятая клетка просто остаётся без света. */
    private void updateLights() {
        lightsDirty = false;
        int glow = frame.glowLevel();
        List<int[]> wanted = glow > 0 ? FrameGeometry.lightCells(frame) : new ArrayList<int[]>();
        for (int[] c : lights) {
            if (!contains(wanted, c)) removeLight(c);
        }
        lights.clear();
        for (int[] c : wanted) {
            if (placeLight(c, glow)) lights.add(c);
        }
    }

    private BlockPos lightPos(int[] c) {
        return new BlockPos(frame.x + c[0], frame.y + c[1], frame.z + c[2]);
    }

    private boolean placeLight(int[] c, int glow) {
        BlockPos pos = lightPos(c);
        BlockState state = level().getBlockState(pos);
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, glow);
        if (state.is(Blocks.LIGHT)) {
            if (state.getValue(LightBlock.LEVEL) != glow) level().setBlock(pos, light, Block.UPDATE_ALL);
            return true;
        }
        return state.isAir() && level().setBlock(pos, light, Block.UPDATE_ALL);
    }

    private void removeLight(int[] c) {
        BlockPos pos = lightPos(c);
        if (level().getBlockState(pos).is(Blocks.LIGHT)) level().removeBlock(pos, false);
    }

    private static boolean contains(List<int[]> cells, int[] cell) {
        for (int[] c : cells) {
            if (c[0] == cell[0] && c[1] == cell[1] && c[2] == cell[2]) return true;
        }
        return false;
    }

    /** Картину сломали или убрали — убираем и её свет. Выгрузка чанка и переход в другое измерение сюда не относятся. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            for (int[] c : lights) removeLight(c);
            lights.clear();
        }
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
                frame.readSettings(tag);
                refresh();
            }
        }
    }

    /** Картина не движется, не горит и не падает; сервер только обновляет свет после смены настроек. */
    @Override
    public void tick() {
        if (lightsDirty && !level().isClientSide) updateLights();
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
        if (level().isClientSide) PacketDistributor.sendToServer(new PacketOpenFrame(getId()));
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
        lights.clear();
        int[] flat = tag.getIntArray("Lights");
        for (int i = 0; i + 2 < flat.length; i += 3) lights.add(new int[] { flat[i], flat[i + 1], flat[i + 2] });
        settingsChanged();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        frame.writePlacement(tag);
        CompoundTag settings = new CompoundTag();
        frame.writeSettings(settings);
        tag.put("Settings", settings);
        int[] flat = new int[lights.size() * 3];
        for (int i = 0; i < lights.size(); i++) System.arraycopy(lights.get(i), 0, flat, i * 3, 3);
        tag.putIntArray("Lights", flat);
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
        frame.readPlacement(tag);
        frame.readSettings(tag);
        refresh();
    }
}
