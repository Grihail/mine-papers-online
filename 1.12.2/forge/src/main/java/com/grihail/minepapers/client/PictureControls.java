package com.grihail.minepapers.client;

import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.block.SoundType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Управление картинами на клиенте — чтобы картина-сущность ощущалась как блок: дотягиваемся до неё так же далеко, как до блоков; ломаем с той же скоростью (без трещин: на больших картинах они слишком дорогие); при наведении — обводка хитбокса; с картиной в руке — контур будущей картины. */
public class PictureControls {

    // ---------- Куда смотрит игрок ----------

    /** Ближайшая картина на луче взгляда в пределах досягаемости блоков (4.5, в творческом 5), если перед ней нет блока. Ванильный выбор цели дотягивается до сущностей только на 3 блока. */
    static RayTraceResult pick(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.world == null || mc.playerController == null) return null;
        double reach = mc.playerController.getBlockReachDistance();
        Vec3d eye = view.getPositionEyes(partialTicks);
        Vec3d look = view.getLook(partialTicks);
        Vec3d end = eye.add(look.scale(reach));
        RayTraceResult block = view.rayTrace(reach, partialTicks);
        double nearest = block != null && block.typeOfHit == RayTraceResult.Type.BLOCK ? block.hitVec.distanceTo(eye) : reach;

        EntityPicture best = null;
        Vec3d bestHit = null;
        AxisAlignedBB around = view.getEntityBoundingBox().expand(look.x * reach, look.y * reach, look.z * reach).grow(1);
        for (EntityPicture picture : mc.world.getEntitiesWithinAABB(EntityPicture.class, around)) {
            if (picture.isDead) continue;
            AxisAlignedBB box = picture.getEntityBoundingBox();
            if (box.contains(eye)) {
                best = picture;
                bestHit = eye;
                nearest = 0;
                continue;
            }
            RayTraceResult hit = box.calculateIntercept(eye, end);
            if (hit == null) continue;
            double dist = eye.distanceTo(hit.hitVec);
            if (dist < nearest) {
                best = picture;
                bestHit = hit.hitVec;
                nearest = dist;
            }
        }
        return best == null ? null : new RayTraceResult(best, bestHit);
    }

    /** Мышь обрабатывается после ванильного выбора цели, но до кликов — подставляем картину, до которой ванильный выбор не дотянулся. Тогда ПКМ откроет настройки, колёсико даст копию, ЛКМ начнёт ломать. */
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.world == null) return;
        RayTraceResult current = mc.objectMouseOver;
        // Моб или другая сущность перед картиной — не перехватываем.
        if (current != null && current.typeOfHit == RayTraceResult.Type.ENTITY && !(current.entityHit instanceof EntityPicture)) {
            return;
        }
        RayTraceResult hit = pick(1f);
        if (hit != null) {
            mc.objectMouseOver = hit;
            mc.pointedEntity = hit.entityHit;
        }
    }

    // ---------- Ломание ----------

    private EntityPicture breaking;
    private float progress;
    private int ticks;

    /** Пока зажата ЛКМ на картине, копим прогресс, как при копании блока; в творческом картина ломается ударом. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPicture aimed = null;
        if (mc.world != null && mc.player != null && mc.currentScreen == null && mc.inGameHasFocus
            && mc.gameSettings.keyBindAttack.isKeyDown() && !mc.player.capabilities.isCreativeMode) {
            RayTraceResult hit = pick(1f);
            if (hit != null) aimed = (EntityPicture) hit.entityHit;
        }
        if (aimed != breaking) {
            stopBreaking();
            if (aimed == null) return;
            breaking = aimed;
            MinePapers.network.sendToServer(new PacketBreakPicture(aimed.getEntityId(), PacketBreakPicture.START));
        }
        if (breaking == null) return;

        progress += breaking.breakRate(mc.player);
        mc.player.swingArm(EnumHand.MAIN_HAND);
        if (ticks++ % 4 == 0) {
            SoundType sound = SoundType.WOOD;
            mc.getSoundHandler().playSound(new PositionedSoundRecord(sound.getHitSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1f) / 8f, sound.getPitch() * 0.5f,
                (float) breaking.posX, (float) breaking.posY, (float) breaking.posZ));
        }
        if (progress >= 1f) {
            MinePapers.network.sendToServer(new PacketBreakPicture(breaking.getEntityId(), PacketBreakPicture.FINISH));
            stopBreaking();
        }
    }

    private void stopBreaking() {
        breaking = null;
        progress = 0;
        ticks = 0;
    }

    // ---------- Обводка и контур будущей картины ----------

    /** Перед блоком висит картина — ванильную обводку блока за ней не рисуем; иначе, с картиной в руке, — контур. */
    @SubscribeEvent
    public void onDrawHighlight(DrawBlockHighlightEvent event) {
        RayTraceResult target = event.getTarget();
        if (target != null && target.typeOfHit == RayTraceResult.Type.ENTITY && !(target.entityHit instanceof EntityPicture)) {
            return;
        }
        if (pick(event.getPartialTicks()) != null) {
            event.setCanceled(true);
            return;
        }
        if (target == null || target.typeOfHit != RayTraceResult.Type.BLOCK) return;
        EntityPlayer player = event.getPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() != MinePapers.frame) stack = player.getHeldItemOffhand();
        if (stack.getItem() != MinePapers.frame) return;
        PictureFrame ghost = ItemPictureFrame.ghostAt(player.world, target.getBlockPos(), target.sideHit, player.rotationYaw, stack);
        boolean canPlace = !FrameGeometry.isBlocked(player.world, ghost, null);
        drawGhost(ghost, canPlace, event.getPartialTicks());
    }

    /** Обводка хитбокса картины под прицелом — как у блоков. Здесь, а не в DrawBlockHighlightEvent: его не бывает, если за картиной нет блока в пределах досягаемости. */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        RayTraceResult hit = pick(event.getPartialTicks());
        if (hit == null) return;
        AxisAlignedBB box = FrameGeometry.canvasBox(((EntityPicture) hit.entityHit).frame);
        double[] cam = camera(event.getPartialTicks());
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.glLineWidth(2f);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        RenderGlobal.drawSelectionBoundingBox(box.grow(0.002).offset(-cam[0], -cam[1], -cam[2]), 0f, 0f, 0f, 0.4f);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static double[] camera(float partialTicks) {
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        return new double[] {
            view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks,
            view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks,
            view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks };
    }

    /** Контур будущей картины (он же её хитбокс); красный — если сюда поставить нельзя. */
    private static void drawGhost(PictureFrame ghost, boolean canPlace, float partialTicks) {
        double[] cam = camera(partialTicks);
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));

        GlStateManager.pushMatrix();
        GlStateManager.translate(-cam[0], -cam[1], -cam[2]);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        AxisAlignedBB canvas = FrameGeometry.canvasBox(ghost).grow(0.002);
        GlStateManager.glLineWidth(2.5f);
        float alpha = 0.6f + 0.35f * pulse;
        if (canPlace) {
            edges(canvas, 1f, 1f, 1f, alpha);
        } else {
            edges(canvas, 1f, 0.3f, 0.3f, alpha);
        }

        GlStateManager.glLineWidth(1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private static void edges(AxisAlignedBB b, float r, float g, float bl, float a) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder buf = t.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        double[] xs = { b.minX, b.maxX }, ys = { b.minY, b.maxY }, zs = { b.minZ, b.maxZ };
        for (double y : ys) for (double z : zs) { line(buf, b.minX, y, z, b.maxX, y, z, r, g, bl, a); }
        for (double x : xs) for (double z : zs) { line(buf, x, b.minY, z, x, b.maxY, z, r, g, bl, a); }
        for (double x : xs) for (double y : ys) { line(buf, x, y, b.minZ, x, y, b.maxZ, r, g, bl, a); }
        t.draw();
    }

    private static void line(BufferBuilder buf, double x0, double y0, double z0, double x1, double y1, double z1,
                             float r, float g, float b, float a) {
        buf.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        buf.pos(x1, y1, z1).color(r, g, b, a).endVertex();
    }
}
