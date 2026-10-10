package com.grihail.minepapers.block;

import java.util.ArrayList;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.tile.FrameGeometry;
import com.grihail.minepapers.tile.FrameStructure;
import com.grihail.minepapers.tile.TilePictureFrame;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Главный блок картины. Метаданные = сторона (2..5), в которую картина смотрит.
 * Всё рисует TileEntitySpecialRenderer; хитбокс — часть холста в этой клетке.
 */
public class BlockPictureFrame extends BlockContainer {

    /** Ключ настроек в NBT предмета. */
    public static final String ITEM_TAG = "MinePapersFrame";

    /** Холст (окантовка — обычная иконка блока). */
    @SideOnly(Side.CLIENT)
    public IIcon canvasIcon;

    public BlockPictureFrame() {
        super(Material.wood);
        setHardness(0.5f);
        setStepSound(soundTypeWood);
        setBlockName(MinePapers.MODID + ".picture_frame");
        setBlockTextureName(MinePapers.MODID + ":frame_border");
        setCreativeTab(CreativeTabs.tabDecorations);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon(MinePapers.MODID + ":frame_border");
        canvasIcon = reg.registerIcon(MinePapers.MODID + ":frame_canvas");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TilePictureFrame();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TilePictureFrame) {
                MinePapers.proxy.openFrameGui((TilePictureFrame) te);
            }
        }
        return true;
    }

    // ---------- Установка: на боковую грань любого блока; опора не нужна — картина может парить в воздухе ----------

    @Override
    public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int side) {
        return side >= 2 && side <= 5 && world.getBlock(x, y, z).isReplaceable(world, x, y, z);
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
        return side;
    }

    /** Настройки из предмета (если рамку уже настраивали) и расстановка частей хитбокса. */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TilePictureFrame)) return;
        TilePictureFrame frame = (TilePictureFrame) te;
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(ITEM_TAG)) {
            frame.readSettings(stack.getTagCompound().getCompoundTag(ITEM_TAG));
        }
        if (!world.isRemote) {
            FrameStructure.place(world, frame);
            world.markBlockForUpdate(x, y, z);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TilePictureFrame && !world.isRemote) {
            FrameStructure.clear(world, (TilePictureFrame) te);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    // ---------- Дроп с сохранением настроек ----------

    public static ItemStack makeStack(TilePictureFrame frame) {
        ItemStack stack = new ItemStack(MinePapers.frame);
        if (frame != null && frame.isCustomized()) {
            NBTTagCompound settings = new NBTTagCompound();
            frame.writeSettings(settings);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag(ITEM_TAG, settings);
            stack.setTagCompound(tag);
        }
        return stack;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
        TileEntity te = world.getTileEntity(x, y, z);
        drops.add(makeStack(te instanceof TilePictureFrame ? (TilePictureFrame) te : null));
        return drops;
    }

    /** Блок убираем только после дропа (в harvestBlock), иначе настройки из TileEntity уже не прочитать. */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        if (willHarvest) return true;
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        super.harvestBlock(world, player, x, y, z, meta);
        world.setBlockToAir(x, y, z);
    }

    /** Колёсико мыши в творческом режиме — копия рамки вместе с настройками. */
    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return makeStack(te instanceof TilePictureFrame ? (TilePictureFrame) te : null);
    }

    // ---------- Форма ----------

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        double[] b = te instanceof TilePictureFrame ? FrameGeometry.cellBounds((TilePictureFrame) te, 0, 0) : null;
        if (b == null) {
            b = FrameGeometry.box(world.getBlockMetadata(x, y, z), 0, 0, 0, 1, 1, TilePictureFrame.PIXEL);
        }
        setBlockBounds((float) b[0], (float) b[1], (float) b[2], (float) b[3], (float) b[4], (float) b[5]);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0f, 0f, 0.5f - TilePictureFrame.PIXEL / 2, 1f, 1f, 0.5f + TilePictureFrame.PIXEL / 2);
    }

    /** Без коллизии: сквозь картину можно пройти. */
    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    /** Обводка при наведении — вокруг всей картины. */
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TilePictureFrame) return FrameGeometry.canvasBox((TilePictureFrame) te);
        return super.getSelectedBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    /** Всё рисует RenderPicture. */
    @Override
    public int getRenderType() {
        return -1;
    }

    /** Поршни не двигают картину (иначе она развалится на части). */
    @Override
    public int getMobilityFlag() {
        return 2;
    }

    /** В инвентаре — плоская иконка (textures/items/picture_frame.png). */
    @Override
    @SideOnly(Side.CLIENT)
    public String getItemIconName() {
        return MinePapers.MODID + ":picture_frame";
    }
}
