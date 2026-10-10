package com.grihail.minepapers.client;

import java.util.Optional;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Управление картинами на клиенте — чтобы картина-сущность ощущалась как блок:
 * дотягиваемся до неё так же далеко, как до блоков; ломаем с той же скоростью и с трещинами;
 * при наведении — обводка хитбокса; с картиной в руке — контур будущей картины.
 */
public class PictureControls {

    /** Как у блоков в творческом режиме: при зажатой кнопке следующая картина ломается не раньше чем через 6 тиков. */
    private static final int CREATIVE_BREAK_DELAY = 6;

    // ---------- Куда смотрит игрок ----------

    /**
     * Ближайшая картина на луче взгляда в пределах досягаемости блоков, если перед ней нет блока.
     * Ванильный выбор цели дотягивается до сущностей только на 3 блока.
     */
    static EntityHitResult pick(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Entity view = mc.getCameraEntity();
        if (view == null || mc.level == null || mc.player == null || mc.gameMode == null) return null;
        double reach = mc.player.blockInteractionRange();
        Vec3 eye = view.getEyePosition(partialTick);
        Vec3 look = view.getViewVector(partialTick);
        Vec3 end = eye.add(look.scale(reach));
        HitResult block = view.pick(reach, partialTick, false);
        double nearest = block.getType() == HitResult.Type.BLOCK ? block.getLocation().distanceTo(eye) : reach;

        EntityPicture best = null;
        Vec3 bestHit = null;
        AABB search = view.getBoundingBox().expandTowards(look.scale(reach)).inflate(1 + FrameGeometry.SEARCH_MARGIN);
        for (EntityPicture picture : mc.level.getEntitiesOfClass(EntityPicture.class, search)) {
            if (picture.isRemoved()) continue;
            AABB box = picture.getBoundingBox();
            if (box.contains(eye)) {
                best = picture;
                bestHit = eye;
                nearest = 0;
                continue;
            }
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double dist = eye.distanceTo(hit.get());
            if (dist < nearest) {
                best = picture;
                bestHit = hit.get();
                nearest = dist;
            }
        }
        return best == null ? null : new EntityHitResult(best, bestHit);
    }

    /**
     * Клик мышью: подставляем картину, до которой ванильный выбор не дотянулся. Тогда ПКМ откроет настройки,
     * колёсико даст копию. ЛКМ ванилью не обрабатываем — иначе она ломала бы блок за картиной:
     * в выживании картину копает onClientTick, в творческом она ломается сразу.
     */
    @SubscribeEvent
    public void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // Моб или другая сущность перед картиной — не перехватываем.
        if (mc.hitResult instanceof EntityHitResult current && !(current.getEntity() instanceof EntityPicture)) return;
        EntityHitResult hit = pick(1f);
        if (hit == null) return;
        mc.hitResult = hit;
        mc.crosshairPickEntity = hit.getEntity();
        if (!event.isAttack()) return;

        event.setCanceled(true);
        event.setSwingHand(false);
        if (mc.player.isCreative() && creativeDelay == 0) {
            PacketDistributor.sendToServer(new PacketBreakPicture(hit.getEntity().getId(), PacketBreakPicture.FINISH));
            mc.player.swing(InteractionHand.MAIN_HAND);
            creativeDelay = CREATIVE_BREAK_DELAY;
        }
    }

    // ---------- Ломание ----------

    private EntityPicture breaking;
    private float progress;
    private int ticks;
    private int creativeDelay;

    /** Пока зажата ЛКМ на картине, копим прогресс, как при копании блока. */
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (creativeDelay > 0) creativeDelay--;
        EntityPicture aimed = null;
        if (mc.level != null && mc.player != null && mc.screen == null && mc.mouseHandler.isMouseGrabbed()
            && mc.options.keyAttack.isDown() && !mc.player.isCreative()) {
            EntityHitResult hit = pick(1f);
            if (hit != null) aimed = (EntityPicture) hit.getEntity();
        }
        if (aimed != breaking) {
            stopBreaking();
            if (aimed == null) return;
            breaking = aimed;
            PacketDistributor.sendToServer(new PacketBreakPicture(aimed.getId(), PacketBreakPicture.START));
        }
        if (breaking == null) return;

        progress += breaking.breakRate(mc.player);
        breaking.breakProgress = Math.max(progress, 0.001f);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (ticks++ % 4 == 0) {
            SoundType sound = SoundType.WOOD;
            mc.getSoundManager().play(new SimpleSoundInstance(sound.getHitSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1f) / 8f, sound.getPitch() * 0.5f, RandomSource.create(),
                breaking.getX(), breaking.getY(), breaking.getZ()));
        }
        if (progress >= 1f) {
            PacketDistributor.sendToServer(new PacketBreakPicture(breaking.getId(), PacketBreakPicture.FINISH));
            stopBreaking();
        }
    }

    private void stopBreaking() {
        if (breaking != null) breaking.breakProgress = 0;
        breaking = null;
        progress = 0;
        ticks = 0;
    }

    // ---------- Обводка и контур будущей картины ----------

    /** Перед блоком висит картина — ванильную обводку блока за ней не рисуем; иначе, с картиной в руке, — контур. */
    @SubscribeEvent
    public void onHighlightBlock(RenderHighlightEvent.Block event) {
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (pick(partialTick) != null) {
            event.setCanceled(true);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.is(MinePapers.FRAME.get())) stack = mc.player.getOffhandItem();
        if (!stack.is(MinePapers.FRAME.get())) return;
        BlockHitResult target = event.getTarget();
        PictureFrame ghost = ItemPictureFrame.ghostAt(mc.level, target.getBlockPos(), target.getDirection(),
            mc.player.getYRot(), stack);
        boolean canPlace = !FrameGeometry.isBlocked(mc.level, ghost, null);
        drawGhost(event.getPoseStack(), event.getMultiBufferSource(), event.getCamera().getPosition(), ghost, canPlace);
    }

    /**
     * Обводка хитбокса картины под прицелом — как у блоков. Здесь, а не в RenderHighlightEvent: его не бывает,
     * если за картиной нет блока в пределах досягаемости.
     */
    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        EntityHitResult hit = pick(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (hit == null) return;
        Vec3 cam = event.getCamera().getPosition();
        AABB box = FrameGeometry.canvasBox(((EntityPicture) hit.getEntity()).frame).inflate(0.002)
            .move(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(event.getPoseStack(), buffers.getBuffer(RenderType.lines()), box, 0f, 0f, 0f, 0.4f);
        buffers.endBatch(RenderType.lines());
    }

    /** Контур будущей картины (он же её хитбокс); красный — если сюда поставить нельзя. Виден сквозь блоки. */
    private static void drawGhost(PoseStack poseStack, MultiBufferSource buffers, Vec3 cam, PictureFrame ghost,
                                  boolean canPlace) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
        float alpha = 0.6f + 0.35f * pulse;
        AABB canvas = FrameGeometry.canvasBox(ghost).inflate(0.002).move(-cam.x, -cam.y, -cam.z);
        if (canPlace) {
            LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(PictureRenderTypes.GHOST_LINES), canvas,
                1f, 1f, 1f, alpha);
        } else {
            LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(PictureRenderTypes.GHOST_LINES), canvas,
                1f, 0.3f, 0.3f, alpha);
        }
    }
}
