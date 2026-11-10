package com.grihail.minepapers.client;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
//? if >=26.1 {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
*///?} else {
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Управление картинами на клиенте — чтобы картина-сущность ощущалась как блок: дотягиваемся до неё так же далеко, как до блоков; ломаем с той же скоростью (без трещин: на больших картинах они слишком дорогие); при наведении — обводка хитбокса; с картиной в руке — контур будущей картины. */
public class PictureControls {

    /** Как у блоков в творческом режиме: при зажатой кнопке следующая картина ломается не раньше чем через 6 тиков. */
    private static final int CREATIVE_BREAK_DELAY = 6;

    // ---------- Куда смотрит игрок ----------

    /** Ближайшая картина на луче взгляда в пределах досягаемости блоков, если перед ней нет блока. Ванильный выбор цели дотягивается до сущностей только на 3 блока. Поиск и расстояния — только ванильными методами (ProjectileUtil, HitResult.distanceTo): моды, которые переносят блоки и сущности в свои координаты (движущиеся конструкции), поправляют именно их. */
    static EntityHitResult pick(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Entity view = mc.getCameraEntity();
        if (mc.player == null) return null;
        double reach = ModClient.reach();
        Vec3 eye = view.getEyePosition(partialTick);
        Vec3 look = view.getViewVector(partialTick);
        AABB search = view.getBoundingBox().expandTowards(look.scale(reach)).inflate(1 + FrameGeometry.SEARCH_MARGIN);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(view, eye, eye.add(look.scale(reach)), search,
            e -> e instanceof EntityPicture && !e.isRemoved(), reach * reach);
        if (hit == null) return null;
        // Картина за блоком недоступна. Обе точки лежат на одном луче, поэтому сравнивать можно и от позиции сущности.
        HitResult block = view.pick(reach, partialTick, false);
        if (block.getType() == HitResult.Type.BLOCK && block.distanceTo(view) < hit.distanceTo(view)) return null;
        return hit;
    }

    /** Картина под прицелом в текущем кадре (считается до отрисовки сущностей). */
    private static EntityPicture aimed;

    static boolean isAimed(EntityPicture picture) {
        return picture == aimed;
    }

    /** Клик мышью (движок вызывает до ванильной обработки атаки, использования и выбора блока): подставляем картину, до которой ванильный выбор не дотянулся. Тогда ПКМ откроет настройки, колёсико даст копию. ЛКМ ванилью не обрабатываем — иначе она ломала бы блок за картиной: в выживании картину копает onClientTick, в творческом она ломается сразу. @return true — отменить ванильную атаку (и не махать рукой) */
    public boolean onInteraction(boolean attack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        // Моб или другая сущность перед картиной — не перехватываем.
        if (mc.hitResult instanceof EntityHitResult current && !(current.getEntity() instanceof EntityPicture)) return false;
        EntityHitResult hit = pick(1f);
        if (hit == null) return false;
        mc.hitResult = hit;
        mc.crosshairPickEntity = hit.getEntity();
        if (!attack) return false;

        if (mc.player.isCreative() && creativeDelay == 0) {
            MinePapers.platform.sendToServer(new PacketBreakPicture(hit.getEntity().getId(), PacketBreakPicture.FINISH));
            ModClient.swing();
            creativeDelay = CREATIVE_BREAK_DELAY;
        }
        return true;
    }

    // ---------- Ломание ----------

    private EntityPicture breaking;
    private float progress;
    private int ticks;
    private int creativeDelay;

    /** Конец клиентского тика: пока зажата ЛКМ на картине, копим прогресс, как при копании блока. */
    public void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (creativeDelay > 0) creativeDelay--;
        EntityPicture aimed = null;
        if (mc.level != null && mc.player != null && ModClient.screen() == null && mc.mouseHandler.isMouseGrabbed()
            && mc.options.keyAttack.isDown() && !mc.player.isCreative()) {
            EntityHitResult hit = pick(1f);
            if (hit != null) aimed = (EntityPicture) hit.getEntity();
        }
        if (aimed != breaking) {
            stopBreaking();
            if (aimed == null) return;
            breaking = aimed;
            MinePapers.platform.sendToServer(new PacketBreakPicture(aimed.getId(), PacketBreakPicture.START));
        }
        if (breaking == null) return;

        progress += breaking.breakRate(mc.player);
        ModClient.swing();
        if (ticks++ % 4 == 0) {
            // Своего звука удара у ванильной рамки нет — тихий щелчок рамки.
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.5f, 1f, RandomSource.create(),
                breaking.getX(), breaking.getY(), breaking.getZ()));
        }
        if (progress >= 1f) {
            MinePapers.platform.sendToServer(new PacketBreakPicture(breaking.getId(), PacketBreakPicture.FINISH));
            stopBreaking();
        }
    }

    private void stopBreaking() {
        breaking = null;
        progress = 0;
        ticks = 0;
    }

    // ---------- Обводка и контур будущей картины ----------

    //? if >=26.1 {
    /*/^* Что рисовать вместо обводки блока под прицелом (с 26.1 движок спрашивает об этом при сборе кадра, а рисует позже). *^/
    public interface Highlight {
        void draw(PoseStack poseStack, VertexSink sink);
    }

    /^* Перед блоком висит картина — ничего (обводку блока за ней не рисуем); с картиной в руке — обводка блока и контур будущей картины; иначе null — ванильная обводка. *^/
    public Highlight onHighlightBlock(Vec3 cam, BlockHitResult target) {
        if (aimed != null) return (poseStack, sink) -> {};
        PictureFrame ghost = ghost(cam, target);
        if (ghost == null) return null;
        Minecraft mc = Minecraft.getInstance();
        boolean canPlace = !FrameGeometry.isBlocked(mc.level, ghost, null);
        BlockPos pos = target.getBlockPos();
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos, CollisionContext.of(mc.getCameraEntity()));
        return (poseStack, sink) -> {
            // Ванильную обводку движок в этом случае не рисует — повторяем её: рёбра формы блока.
            VertexConsumer lines = sink.buffer(PictureRenderTypes.lines());
            PoseStack.Pose pose = poseStack.last();
            double dx = pos.getX() - cam.x, dy = pos.getY() - cam.y, dz = pos.getZ() - cam.z;
            shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> {
                float nx = (float) (x1 - x0), ny = (float) (y1 - y0), nz = (float) (z1 - z0);
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                lines.addVertex(pose, (float) (x0 + dx), (float) (y0 + dy), (float) (z0 + dz)).setColor(0f, 0f, 0f, 0.4f)
                    .setNormal(pose, nx / len, ny / len, nz / len).setLineWidth(2f);
                lines.addVertex(pose, (float) (x1 + dx), (float) (y1 + dy), (float) (z1 + dz)).setColor(0f, 0f, 0f, 0.4f)
                    .setNormal(pose, nx / len, ny / len, nz / len).setLineWidth(2f);
            });
            drawGhost(poseStack, sink, cam, ghost, canPlace);
        };
    }
    *///?} else {
    /** Обводка блока под прицелом (движок вызывает перед ванильной). Перед блоком висит картина — ванильную обводку блока за ней не рисуем; иначе, с картиной в руке, — контур будущей картины. @return true — ванильную обводку не рисовать */
    public boolean onHighlightBlock(PoseStack poseStack, MultiBufferSource buffers, Vec3 cam, BlockHitResult target) {
        if (aimed != null) return true;
        PictureFrame ghost = ghost(cam, target);
        if (ghost == null) return false;
        boolean canPlace = !FrameGeometry.isBlocked(Minecraft.getInstance().level, ghost, null);
        drawGhost(poseStack, buffers::getBuffer, cam, ghost, canPlace);
        return false;
    }
    //?}

    /** Будущая картина у блока под прицелом, если в руке картина; иначе null. */
    private static PictureFrame ghost(Vec3 cam, BlockHitResult target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || target.getType() != HitResult.Type.BLOCK) return null;
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.is(MinePapers.FRAME.get())) stack = mc.player.getOffhandItem();
        if (!stack.is(MinePapers.FRAME.get())) return null;
        // Блок дальше, чем можно дотянуться: его координаты не мировые (движущиеся конструкции других модов хранят
        // блоки в своей области и рисуют их со сдвигом и поворотом). Где он на экране, отсюда не узнать — контур не рисуем.
        double far = ModClient.reach() + 2;
        if (Vec3.atCenterOf(target.getBlockPos()).distanceToSqr(cam) > far * far) return null;
        return ItemPictureFrame.ghostAt(mc.level, target.getBlockPos(), target.getDirection(), mc.player.getYRot(), stack);
    }

    /** Начало отрисовки мира, до сущностей: картину под прицелом ищем один раз за кадр (её обводку рисует RenderPicture). */
    public void onRenderLevel(float partialTick) {
        EntityHitResult hit = pick(partialTick);
        aimed = hit != null ? (EntityPicture) hit.getEntity() : null;
    }

    /** Контур будущей картины (он же её хитбокс); красный — если сюда поставить нельзя. Виден сквозь блоки. */
    private static void drawGhost(PoseStack poseStack, VertexSink sink, Vec3 cam, PictureFrame ghost, boolean canPlace) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));
        float alpha = 0.6f + 0.35f * pulse;
        AABB canvas = FrameGeometry.canvasBox(ghost).inflate(0.002).move(-cam.x, -cam.y, -cam.z);
        float gb = canPlace ? 1f : 0.3f;
        //? if >=26.1 {
        /*PictureRenderTypes.lineBox(sink.buffer(PictureRenderTypes.GHOST_LINES), poseStack.last(), canvas, 1f, gb, gb, alpha, 2.5f);
        *///?} else {
        LevelRenderer.renderLineBox(poseStack, sink.buffer(PictureRenderTypes.GHOST_LINES), canvas, 1f, gb, gb, alpha);
        //?}
    }
}
