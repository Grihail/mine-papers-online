package com.grihail.minepapers.block;

import java.util.ArrayList;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.tile.FrameGeometry;
import com.grihail.minepapers.tile.TileFrameFiller;
import com.grihail.minepapers.tile.TilePictureFrame;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Невидимая часть большой картины. Даёт хитбокс на своём куске холста;
 * клик открывает настройки, разрушение ломает всю картину целиком.
 */
public class BlockPictureFiller extends BlockContainer {

    public BlockPictureFiller() {
        super(Material.wood);
        setHardness(0.5f);
        setStepSound(soundTypeWood);
        setBlockName(MinePapers.MODID + ".picture_frame_part");
        setBlockTextureName(MinePapers.MODID + ":frame_border");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon(MinePapers.MODID + ":frame_border");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileFrameFiller();
    }

    private static TileFrameFiller part(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileFrameFiller ? (TileFrameFiller) te : null;
    }

    private static TilePictureFrame master(IBlockAccess world, int x, int y, int z) {
        TileFrameFiller part = part(world, x, y, z);
        return part != null ? part.getMaster() : null;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        TilePictureFrame master = master(world, x, y, z);
        if (master == null) {
            if (!world.isRemote) world.setBlockToAir(x, y, z);
            return false;
        }
        if (world.isRemote) MinePapers.proxy.openFrameGui(master);
        return true;
    }

    // ---------- Разрушение — всей картины сразу ----------

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        TilePictureFrame master = master(world, x, y, z);
        if (master != null && !world.isRemote) {
            // Ломаем главный блок (с дропом настроек, если не творческий режим) — он снимет и все части.
            world.func_147480_a(master.xCoord, master.yCoord, master.zCoord, !player.capabilities.isCreativeMode);
            if (world.getBlock(x, y, z) == this) world.setBlockToAir(x, y, z);
            return true;
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, int x, int y, int z, int meta) {
        // Сам по себе ничего не роняет — предмет выпадает из главного блока.
    }

    @Override
    public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
        TilePictureFrame master = master(world, x, y, z);
        if (master != null && !world.isRemote) {
            world.func_147480_a(master.xCoord, master.yCoord, master.zCoord, true);
        }
        super.onBlockExploded(world, x, y, z, explosion);
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
        return BlockPictureFrame.makeStack(master(world, x, y, z));
    }

    /** Осиротевшая часть (рамку убрали чем-то необычным) исчезает при первом изменении рядом. */
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (!world.isRemote && master(world, x, y, z) == null) {
            world.setBlockToAir(x, y, z);
        }
    }

    // ---------- Форма ----------

    private static double[] bounds(IBlockAccess world, int x, int y, int z) {
        TileFrameFiller part = part(world, x, y, z);
        TilePictureFrame master = part != null ? part.getMaster() : null;
        return master != null ? FrameGeometry.cellBounds(master, part.getCellU(), part.getCellV()) : null;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        double[] b = bounds(world, x, y, z);
        if (b == null) {
            setBlockBounds(0f, 0f, 0f, 0f, 0f, 0f);
        } else {
            setBlockBounds((float) b[0], (float) b[1], (float) b[2], (float) b[3], (float) b[4], (float) b[5]);
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        TilePictureFrame master = master(world, x, y, z);
        if (master != null) return FrameGeometry.canvasBox(master);
        return AxisAlignedBB.getBoundingBox(x, y, z, x, y, z);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public int getMobilityFlag() {
        return 2;
    }
}
