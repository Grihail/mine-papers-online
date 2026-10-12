package com.grihail.minepapers.client;

import java.util.List;

import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.block.ItemPictureFrame;
import com.grihail.minepapers.network.PacketBreakPicture;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/** Управление картинами на клиенте — чтобы картина-сущность ощущалась как блок: дотягиваемся до неё так же далеко, как до блоков; ломаем с той же скоростью и с трещинами; при наведении — обводка хитбокса; с картиной в руке — контур будущей картины. */
public class PictureControls {

    // ---------- Куда смотрит игрок ----------

    /** Ближайшая картина на луче взгляда в пределах досягаемости блоков (4.5, в творческом 5), если перед ней нет блока. Ванильный выбор цели дотягивается до сущностей только на 3 блока. */
    static MovingObjectPosition pick(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase view = mc.renderViewEntity;
        if (view == null || mc.theWorld == null || mc.playerController == null) return null;
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 eye = view.getPosition(partialTicks);
        Vec3 look = view.getLook(partialTicks);
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition block = view.rayTrace(reach, partialTicks);
        double nearest = block != null && block.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            ? block.hitVec.distanceTo(eye) : reach;

        EntityPicture best = null;
        Vec3 bestHit = null;
        List<?> list = mc.theWorld.getEntitiesWithinAABB(EntityPicture.class,
            view.boundingBox.addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach).expand(1, 1, 1));
        for (Object o : list) {
            EntityPicture picture = (EntityPicture) o;
            if (picture.isDead) continue;
            AxisAlignedBB box = picture.boundingBox;
            if (box.isVecInside(eye)) {
                best = picture;
                bestHit = eye;
                nearest = 0;
                continue;
            }
            MovingObjectPosition hit = box.calculateIntercept(eye, end);
            if (hit == null) continue;
            double dist = eye.distanceTo(hit.hitVec);
            if (dist < nearest) {
                best = picture;
                bestHit = hit.hitVec;
                nearest = dist;
            }
        }
        return best == null ? null : new MovingObjectPosition(best, bestHit);
    }

    /** Мышь обрабатывается после ванильного выбора цели, но до кликов — подставляем картину, до которой ванильный выбор не дотянулся. Тогда ПКМ откроет настройки, колёсико даст копию, ЛКМ начнёт ломать. */
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.theWorld == null) return;
        MovingObjectPosition current = mc.objectMouseOver;
        // Моб или другая сущность перед картиной — не перехватываем.
        if (current != null && current.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
            && !(current.entityHit instanceof EntityPicture)) {
            return;
        }
        MovingObjectPosition hit = pick(1f);
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
        if (mc.theWorld != null && mc.thePlayer != null && mc.currentScreen == null && mc.inGameHasFocus
            && mc.gameSettings.keyBindAttack.getIsKeyPressed() && !mc.thePlayer.capabilities.isCreativeMode) {
            MovingObjectPosition hit = pick(1f);
            if (hit != null) aimed = (EntityPicture) hit.entityHit;
        }
        if (aimed != breaking) {
            stopBreaking();
            if (aimed == null) return;
            breaking = aimed;
            MinePapers.network.sendToServer(new PacketBreakPicture(aimed.getEntityId(), PacketBreakPicture.START));
        }
        if (breaking == null) return;

        progress += breaking.breakRate(mc.thePlayer);
        breaking.breakProgress = Math.max(progress, 0.001f);
        mc.thePlayer.swingItem();
        if (ticks++ % 4 == 0) {
            Block.SoundType sound = MinePapers.frame.stepSound;
            mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(sound.getStepResourcePath()),
                (sound.getVolume() + 1f) / 8f, sound.getPitch() * 0.5f,
                (float) breaking.posX, (float) breaking.posY, (float) breaking.posZ));
        }
        if (progress >= 1f) {
            MinePapers.network.sendToServer(new PacketBreakPicture(breaking.getEntityId(), PacketBreakPicture.FINISH));
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
    public void onDrawHighlight(DrawBlockHighlightEvent event) {
        MovingObjectPosition target = event.target;
        if (target != null && target.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
            && !(target.entityHit instanceof EntityPicture)) {
            return;
        }
        if (pick(event.partialTicks) != null) {
            event.setCanceled(true);
            return;
        }
        ItemStack stack = event.currentItem;
        if (stack == null || stack.getItem() != Item.getItemFromBlock(MinePapers.frame)) return;
        if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        PictureFrame ghost = ItemPictureFrame.ghostAt(event.player.worldObj, target.blockX, target.blockY, target.blockZ,
            target.sideHit, event.player.rotationYaw, stack);
        if (ghost == null) return;
        boolean canPlace = !FrameGeometry.isBlocked(event.player.worldObj, ghost, null);
        drawGhost(ghost, canPlace, event.partialTicks);
    }

    /** Обводка хитбокса картины под прицелом — как у блоков. Здесь, а не в DrawBlockHighlightEvent: его не бывает, если за картиной нет блока в пределах досягаемости. */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        MovingObjectPosition hit = pick(event.partialTicks);
        if (hit == null) return;
        AxisAlignedBB box = FrameGeometry.canvasBox(((EntityPicture) hit.entityHit).frame);
        double[] cam = camera(event.partialTicks);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(0f, 0f, 0f, 0.4f);
        GL11.glLineWidth(2f);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        RenderGlobal.drawOutlinedBoundingBox(box.expand(0.002, 0.002, 0.002).getOffsetBoundingBox(-cam[0], -cam[1], -cam[2]), -1);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static double[] camera(float partialTicks) {
        EntityLivingBase view = Minecraft.getMinecraft().renderViewEntity;
        return new double[] {
            view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks,
            view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks,
            view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks };
    }

    /** Контур будущей картины (он же её хитбокс); красный — если сюда поставить нельзя. */
    private static void drawGhost(PictureFrame ghost, boolean canPlace, float partialTicks) {
        double[] cam = camera(partialTicks);
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));

        GL11.glPushMatrix();
        GL11.glTranslated(-cam[0], -cam[1], -cam[2]);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        AxisAlignedBB canvas = FrameGeometry.canvasBox(ghost).expand(0.002, 0.002, 0.002);
        GL11.glLineWidth(2.5f);
        if (canPlace) {
            GL11.glColor4f(1f, 1f, 1f, 0.6f + 0.35f * pulse);
        } else {
            GL11.glColor4f(1f, 0.3f, 0.3f, 0.6f + 0.35f * pulse);
        }
        edges(canvas);

        GL11.glLineWidth(1f);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glPopMatrix();
    }

    private static void edges(AxisAlignedBB b) {
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        double[] xs = { b.minX, b.maxX }, ys = { b.minY, b.maxY }, zs = { b.minZ, b.maxZ };
        for (double y : ys) for (double z : zs) { t.addVertex(b.minX, y, z); t.addVertex(b.maxX, y, z); }
        for (double x : xs) for (double z : zs) { t.addVertex(x, b.minY, z); t.addVertex(x, b.maxY, z); }
        for (double x : xs) for (double y : ys) { t.addVertex(x, y, b.minZ); t.addVertex(x, y, b.maxZ); }
        t.draw();
    }
}
