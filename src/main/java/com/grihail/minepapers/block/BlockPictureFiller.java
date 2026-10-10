package com.grihail.minepapers.block;

import java.util.ArrayList;
import java.util.Random;

import com.grihail.minepapers.MinePapers;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Невидимая часть большой картины из старых сохранений (когда картины были блоками).
 * Её убирает картина при превращении в сущность; оставшиеся без картины исчезают сами.
 */
public class BlockPictureFiller extends Block {

    public BlockPictureFiller() {
        super(Material.wood);
        setBlockName(MinePapers.MODID + ".picture_frame_part");
        setBlockTextureName(MinePapers.MODID + ":frame_border");
        setBlockBounds(0f, 0f, 0f, 0f, 0f, 0f);
        setTickRandomly(true);
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {
        world.setBlockToAir(x, y, z);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        world.setBlockToAir(x, y, z);
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
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
}
