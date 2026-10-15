package com.grihail.minepapers.block;

import java.util.List;
import java.util.Random;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.MaterialTransparent;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Свет светящейся картины — невидимый блок без коллизии, как "блок света" новых версий: сила (0..15) в состоянии LEVEL. Его ставит и убирает сама картина (EntityPicture), на его место можно поставить любой блок. Если картины-хозяина рядом нет — исчезает сам. */
public class BlockPictureLight extends Block {

    public static final PropertyInteger LEVEL = PropertyInteger.create("level", 0, 15);

    public BlockPictureLight() {
        super(new MaterialTransparent(MapColor.AIR) {
            {
                setReplaceable();
            }
        });
        setTranslationKey(MinePapers.MODID + ".picture_light");
        setTickRandomly(true);
        setDefaultState(blockState.getBaseState().withProperty(LEVEL, 15));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LEVEL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(LEVEL, meta & 15);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(LEVEL);
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(LEVEL);
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        // Позиция картины — центр холста: свет может быть до полудиагонали самой большой картины от него.
        double r = PictureFrame.MAX_SIZE_BLOCKS * 0.75 + 2;
        List<EntityPicture> pictures = world.getEntitiesWithinAABB(EntityPicture.class, new AxisAlignedBB(pos).grow(r));
        for (EntityPicture picture : pictures) {
            if (picture.ownsLight(pos.getX(), pos.getY(), pos.getZ())) return;
        }
        world.setBlockToAir(pos);
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    /** Не ловит прицел: клик проходит к картине или к блоку за ней. */
    @Override
    public boolean canCollideCheck(IBlockState state, boolean hitIfLiquid) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }
}
