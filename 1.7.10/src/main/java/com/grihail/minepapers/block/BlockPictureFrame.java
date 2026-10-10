package com.grihail.minepapers.block;

import java.util.ArrayList;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.PictureFrame;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * Блок картины. Сама картина — сущность (picture.EntityPicture); блок остался, потому что к нему привязан
 * предмет картины (ItemPictureFrame) и на нём зарегистрированы текстуры рамки, холста и трещин.
 * В мире он встречается только в старых сохранениях: TileLegacyPicture превращает его в картину-сущность.
 */
public class BlockPictureFrame extends BlockContainer {

    /** Ключ настроек в NBT предмета. */
    public static final String ITEM_TAG = "MinePapersFrame";

    /** Холст (окантовка — обычная иконка блока). */
    @SideOnly(Side.CLIENT)
    public IIcon canvasIcon;
    /** Ванильные трещины разрушения (те же спрайты, что у блоков). */
    @SideOnly(Side.CLIENT)
    public IIcon[] crackIcons;

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
        crackIcons = new IIcon[10];
        for (int i = 0; i < crackIcons.length; i++) crackIcons[i] = reg.registerIcon("destroy_stage_" + i);
    }

    /** В инвентаре — плоская иконка (textures/items/picture_frame.png). */
    @Override
    @SideOnly(Side.CLIENT)
    public String getItemIconName() {
        return MinePapers.MODID + ":picture_frame";
    }

    /** Предмет картины; настройки сохраняются, если картину настраивали. */
    public static ItemStack makeStack(PictureFrame frame) {
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

    // ---------- Старые сохранения ----------

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileLegacyPicture();
    }

    /** Предмет выпадает из картины-сущности; сам блок существует лишь тик до превращения. */
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
