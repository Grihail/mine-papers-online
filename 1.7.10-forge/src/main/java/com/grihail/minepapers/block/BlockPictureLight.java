package com.grihail.minepapers.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.MaterialTransparent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Свет светящейся картины — невидимый блок без коллизии, как "блок света" новых версий: сила (0..15) в метаданных. Его ставит и убирает сама картина (EntityPicture), на его место можно поставить любой блок. Если картины-хозяина рядом нет — исчезает сам. */
public class BlockPictureLight extends Block {

    public BlockPictureLight() {
        super(new MaterialTransparent(MapColor.airColor) {
            {
                setReplaceable();
            }
        });
        setBlockName(MinePapers.MODID + ".picture_light");
        setBlockTextureName(MinePapers.MODID + ":frame_canvas");
        setBlockBounds(0f, 0f, 0f, 0f, 0f, 0f);
        setTickRandomly(true);
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) & 15;
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random random) {
        // Позиция картины — центр холста: свет может быть до полудиагонали самой большой картины от него.
        double r = PictureFrame.MAX_SIZE_BLOCKS * 0.75 + 2;
        @SuppressWarnings("unchecked")
        List<EntityPicture> pictures = world.getEntitiesWithinAABB(EntityPicture.class,
            AxisAlignedBB.getBoundingBox(x - r, y - r, z - r, x + 1 + r, y + 1 + r, z + 1 + r));
        for (EntityPicture picture : pictures) {
            if (picture.ownsLight(x, y, z)) return;
        }
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

    /** Не ловит прицел: клик проходит к картине или к блоку за ней. */
    @Override
    public boolean canCollideCheck(int meta, boolean hitIfLiquid) {
        return false;
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
