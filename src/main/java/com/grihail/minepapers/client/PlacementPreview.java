package com.grihail.minepapers.client;

import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.block.BlockPictureFrame;
import com.grihail.minepapers.tile.FrameGeometry;
import com.grihail.minepapers.tile.TilePictureFrame;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;

/**
 * Подсказка перед установкой: если в руке рамка (с сохранёнными настройками или без),
 * на месте будущей картины рисуется её контур — с учётом размера и смещения.
 */
public class PlacementPreview {

    @SubscribeEvent
    public void onDrawHighlight(DrawBlockHighlightEvent event) {
        ItemStack stack = event.currentItem;
        if (stack == null || stack.getItem() != Item.getItemFromBlock(MinePapers.frame)) return;
        MovingObjectPosition hit = event.target;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        TilePictureFrame ghost = ghost(event.player.worldObj, hit, stack);
        if (ghost == null) return;
        boolean canPlace = MinePapers.frame.canPlaceBlockOnSide(event.player.worldObj,
            ghost.xCoord, ghost.yCoord, ghost.zCoord, ghost.getFacing());
        draw(event.player, ghost, canPlace, event.partialTicks);
    }

    /** Будущая рамка с настройками из предмета — там же, куда её поставит ItemBlock. */
    static TilePictureFrame ghost(World world, MovingObjectPosition hit, ItemStack stack) {
        int x = hit.blockX, y = hit.blockY, z = hit.blockZ, side = hit.sideHit;
        Block block = world.getBlock(x, y, z);
        if (block == Blocks.snow_layer && (world.getBlockMetadata(x, y, z) & 7) < 1) {
            side = 1;
        } else if (block != Blocks.vine && block != Blocks.tallgrass && block != Blocks.deadbush
            && !block.isReplaceable(world, x, y, z)) {
            switch (side) {
                case 0: y--; break;
                case 1: y++; break;
                case 2: z--; break;
                case 3: z++; break;
                case 4: x--; break;
                case 5: x++; break;
            }
        }
        if (side < 2 || side > 5) return null; // картины вешаются только на стены

        TilePictureFrame ghost = new TilePictureFrame();
        ghost.xCoord = x;
        ghost.yCoord = y;
        ghost.zCoord = z;
        ghost.blockMetadata = side;
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(BlockPictureFrame.ITEM_TAG)) {
            ghost.readSettings(stack.getTagCompound().getCompoundTag(BlockPictureFrame.ITEM_TAG));
        }
        return ghost;
    }

    private static void draw(EntityPlayer player, TilePictureFrame ghost, boolean canPlace, float partialTicks) {
        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0));

        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Только контур будущей картины (он же её хитбокс); красный — если сюда поставить нельзя
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
