package com.grihail.minepapers.block;

import java.util.ArrayList;
import java.util.List;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Предмет картины: вешает картину-сущность; в подсказке видно, какие настройки в нём сохранены. Остаётся ItemBlock своего блока, чтобы старые предметы в мирах и инвентарях не пропали. */
public class ItemPictureFrame extends ItemBlock {

    public ItemPictureFrame(Block block) {
        super(block);
    }

    /** Будущая картина при клике по грани side блока (x, y, z), с настройками из предмета. Лежит ровно на этой грани — на настоящей поверхности блока (полублок, ковёр, боковина забора), а не на границе клетки. На полу и потолке верх картинки — в ту сторону, куда смотрит игрок (yaw). */
    public static PictureFrame ghostAt(World world, int x, int y, int z, int side, float yaw, ItemStack stack) {
        if (side < 0 || side > 5) return null;
        Block block = world.getBlock(x, y, z);
        block.setBlockBoundsBasedOnState(world, x, y, z);
        double[] min = { block.getBlockBoundsMinX(), block.getBlockBoundsMinY(), block.getBlockBoundsMinZ() };
        double[] max = { block.getBlockBoundsMaxX(), block.getBlockBoundsMaxY(), block.getBlockBoundsMaxZ() };
        int axis = side < 2 ? 1 : side < 4 ? 2 : 0;
        boolean positive = (side & 1) == 1;

        ForgeDirection dir = ForgeDirection.getOrientation(side);
        PictureFrame ghost = new PictureFrame();
        ghost.x = x + dir.offsetX;
        ghost.y = y + dir.offsetY;
        ghost.z = z + dir.offsetZ;
        ghost.facing = side;
        ghost.rotation = MathHelper.floor_double(yaw * 4f / 360f + 0.5) & 3;
        // Грань блока может не доходить до границы клетки: прижимаем картину к ней.
        ghost.surface = (float) (positive ? max[axis] - 1 : -min[axis]);
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(BlockPictureFrame.ITEM_TAG)) {
            ghost.readSettings(stack.getTagCompound().getCompoundTag(BlockPictureFrame.ITEM_TAG));
        }
        return ghost;
    }

    /** Установка картины-сущности. Клетки она не занимает, поэтому рядом и в той же клетке может быть что угодно; нельзя только провести холст сквозь блоки и другие картины. */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        if (stack.stackSize == 0) return false;
        PictureFrame ghost = ghostAt(world, x, y, z, side, player.rotationYaw, stack);
        if (ghost == null || !player.canPlayerEdit(ghost.x, ghost.y, ghost.z, side, stack)) return false;
        if (FrameGeometry.isBlocked(world, ghost, null)) return false;
        if (!world.isRemote) {
            EntityPicture picture = new EntityPicture(world, ghost);
            world.spawnEntityInWorld(picture);
            Block.SoundType sound = field_150939_a.stepSound;
            world.playSoundEffect(picture.posX, picture.posY, picture.posZ, sound.func_150496_b(),
                (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        }
        stack.stackSize--;
        return true;
    }

    /** Проверка на клиенте перед отправкой клика на сервер. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean func_150936_a(World world, int x, int y, int z, int side, EntityPlayer player, ItemStack stack) {
        PictureFrame ghost = ghostAt(world, x, y, z, side, player.rotationYaw, stack);
        return ghost != null && !FrameGeometry.isBlocked(world, ghost, null);
    }

    /** Блоки из слотов материалов (рамка, холст), лежащие в картине-предмете. */
    public static List<ItemStack> storedMaterials(ItemStack stack) {
        List<ItemStack> stored = new ArrayList<ItemStack>();
        if (stack == null || stack.getItem() != Item.getItemFromBlock(MinePapers.frame) || !stack.hasTagCompound()) {
            return stored;
        }
        PictureFrame frame = new PictureFrame();
        NBTTagCompound settings = stack.getTagCompound().getCompoundTag(BlockPictureFrame.ITEM_TAG);
        for (PictureFrame.Material m : frame.materials()) {
            m.read(settings);
            if (m.stack != null) stored.add(m.stack);
        }
        return stored;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(BlockPictureFrame.ITEM_TAG)) return;
        PictureFrame s = new PictureFrame();
        s.readSettings(stack.getTagCompound().getCompoundTag(BlockPictureFrame.ITEM_TAG));

        lines.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("minepapers.tooltip.configured"));
        String path = s.getSourcePath();
        if (path.length() > 40) path = "..." + path.substring(path.length() - 37);
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("minepapers.tooltip.source." + s.source)
            + (path.isEmpty() ? "" : ": " + EnumChatFormatting.WHITE + path));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("minepapers.tooltip.size",
            fmt(s.getWidthBlocks()), fmt(s.getHeightBlocks()))
            + ", " + StatCollector.translateToLocal("minepapers.gui.mode." + s.mode));
        material(lines, s.border, "minepapers.tooltip.border");
        material(lines, s.canvas, "minepapers.tooltip.canvas");
        if (PictureFrame.isGlowDust(s.glow.stack)) {
            lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("minepapers.tooltip.glow.self"));
        } else if (s.glowLevel() > 0) {
            lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("minepapers.tooltip.glow",
                s.glowLevel()));
        }
    }

    @SideOnly(Side.CLIENT)
    private static void material(List<String> lines, PictureFrame.Material m, String key) {
        Item item = m.item.isEmpty() ? null : (Item) Item.itemRegistry.getObject(m.item);
        if (item == null) return;
        String name;
        try {
            name = new ItemStack(item, 1, m.meta).getDisplayName();
        } catch (Throwable t) {
            name = m.item;
        }
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(key, name));
    }

    private static String fmt(float blocks) {
        if (blocks == Math.round(blocks)) return String.valueOf(Math.round(blocks));
        return String.format(java.util.Locale.ROOT, "%.2f", blocks);
    }
}
