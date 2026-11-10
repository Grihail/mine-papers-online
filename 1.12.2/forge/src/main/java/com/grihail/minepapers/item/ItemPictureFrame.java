package com.grihail.minepapers.item;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.grihail.minepapers.CommonProxy;
import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.Numbers;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.init.SoundEvents;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Предмет картины: вешает картину-сущность, ПКМ в воздух — окно её настройки; в подсказке видно, какие настройки в нём сохранены. */
public class ItemPictureFrame extends Item {

    /** Ключ настроек в NBT предмета (как в 1.7.10 и 1.20.1). */
    public static final String ITEM_TAG = "MinePapersFrame";

    public ItemPictureFrame() {
        setTranslationKey(MinePapers.MODID + ".picture_frame");
        setCreativeTab(CreativeTabs.DECORATIONS);
    }

    /** Название по ключу из общих словарей (как в 1.13+), а не по старому item.*.name. */
    @Override
    @SuppressWarnings("deprecation")
    public String getItemStackDisplayName(ItemStack stack) {
        return net.minecraft.util.text.translation.I18n.translateToLocal("item." + MinePapers.MODID + ".picture_frame").trim();
    }

    /** Предмет картины; настройки сохраняются, если картину настраивали. */
    public static ItemStack makeStack(PictureFrame frame) {
        ItemStack stack = new ItemStack(MinePapers.frame);
        if (frame != null) FrameHolder.store(stack, frame);
        return stack;
    }

    /** Настройки предмета (null — не настраивали). */
    public static NBTTagCompound settings(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(ITEM_TAG) ? stack.getTagCompound().getCompoundTag(ITEM_TAG) : null;
    }

    /** Будущая картина при клике по грани side блока pos, с настройками из предмета. Лежит ровно на этой грани — на настоящей поверхности блока (полублок, ковёр, боковина забора), а не на границе клетки. На полу и потолке верх картинки — в ту сторону, куда смотрит игрок (yaw). */
    public static PictureFrame ghostAt(World world, BlockPos pos, EnumFacing side, float yaw, ItemStack stack) {
        IBlockState state = world.getBlockState(pos);
        AxisAlignedBB box = state.getBoundingBox(world, pos);
        double[] min = { box.minX, box.minY, box.minZ };
        double[] max = { box.maxX, box.maxY, box.maxZ };
        int s = side.getIndex();
        int axis = s < 2 ? 1 : s < 4 ? 2 : 0;
        boolean positive = (s & 1) == 1;

        PictureFrame ghost = new PictureFrame();
        ghost.x = pos.getX() + side.getXOffset();
        ghost.y = pos.getY() + side.getYOffset();
        ghost.z = pos.getZ() + side.getZOffset();
        ghost.facing = s;
        ghost.rotation = MathHelper.floor(yaw * 4f / 360f + 0.5) & 3;
        // Грань блока может не доходить до границы клетки: прижимаем картину к ней.
        ghost.surface = (float) (positive ? max[axis] - 1 : -min[axis]);
        NBTTagCompound settings = settings(stack);
        if (settings != null) ghost.readSettings(settings);
        return ghost;
    }

    /** Установка картины-сущности. Клетки она не занимает, поэтому рядом и в той же клетке может быть что угодно; нельзя только провести холст сквозь блоки и другие картины. */
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side,
                                      float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        PictureFrame ghost = ghostAt(world, pos, side, player.rotationYaw, stack);
        if (!player.canPlayerEdit(new BlockPos(ghost.x, ghost.y, ghost.z), side, stack)) return EnumActionResult.FAIL;
        if (FrameGeometry.isBlocked(world, ghost, null)) return EnumActionResult.FAIL;
        if (!world.isRemote) {
            EntityPicture picture = new EntityPicture(world, ghost);
            world.spawnEntity(picture);
            world.playSound(null, picture.posX, picture.posY, picture.posZ, SoundEvents.ENTITY_ITEMFRAME_PLACE, SoundCategory.BLOCKS, 1f, 1f);
        }
        stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }

    /** ПКМ не по блоку — окно настройки этой картины, как у висящей. Сюда приходит и клик по блоку, если картина там не поместилась: тогда окно не открываем, как и в новых версиях. Окно с инвентарём открывает сервер. */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        RayTraceResult hit = rayTrace(world, player, false);
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
        if (!world.isRemote) CommonProxy.openFrame(player, FrameHolder.targetOf(player, hand));
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }

    /** Подписанная картина: подпись уникальна и навсегда — её не копируют и не сбрасывают. */
    public static boolean isSigned(ItemStack stack) {
        NBTTagCompound settings = settings(stack);
        return settings != null && PictureFrame.parseUuid(settings.getString("Signer")) != null;
    }

    /** Картина запривачена (в настройках есть владелец). */
    public static boolean isLocked(ItemStack stack) {
        NBTTagCompound settings = settings(stack);
        return settings != null && PictureFrame.parseUuid(settings.getString("Owner")) != null;
    }

    /** Заприваченная и подписанная картины блестят, как зачарованный предмет. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return isLocked(stack) || isSigned(stack) || super.hasEffect(stack);
    }

    /** Блоки из слотов материалов (рамка, холст), лежащие в картине-предмете. */
    public static List<ItemStack> storedMaterials(ItemStack stack) {
        List<ItemStack> stored = new ArrayList<ItemStack>();
        NBTTagCompound settings = stack.getItem() == MinePapers.frame ? settings(stack) : null;
        if (settings == null) return stored;
        PictureFrame frame = new PictureFrame();
        for (PictureFrame.Material m : frame.materials()) {
            m.read(settings);
            if (!m.stack.isEmpty()) stored.add(m.stack);
        }
        return stored;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> lines, ITooltipFlag flag) {
        NBTTagCompound settings = settings(stack);
        if (settings == null) return;
        PictureFrame s = new PictureFrame();
        s.readSettings(settings);

        lines.add(TextFormatting.GOLD + I18n.format("minepapers.tooltip.configured"));
        if (!s.title.isEmpty()) lines.add(TextFormatting.WHITE + s.title);
        for (String line : PictureFrame.wrap(s.description, 40)) {
            lines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + line);
        }
        String path = s.getSourcePath();
        if (path.length() > 40) path = "..." + path.substring(path.length() - 37);
        lines.add(TextFormatting.GRAY + I18n.format("minepapers.tooltip.source." + s.source)
            + (path.isEmpty() ? "" : ": " + TextFormatting.WHITE + path));
        lines.add(TextFormatting.GRAY + I18n.format("minepapers.tooltip.size",
            Numbers.blocks(s.getWidthBlocks()), Numbers.blocks(s.getHeightBlocks())));
        part(lines, "minepapers.tooltip.border", "minepapers.gui.frameShape." + s.getFrameShape(), s.usesBorder() ? s.border : null);
        part(lines, "minepapers.tooltip.canvas", "minepapers.gui.canvasKind." + s.getCanvasKind(), s.usesCanvas() ? s.canvas : null);
        if (s.glass) part(lines, "minepapers.tooltip.glass", "minepapers.gui.glass.1", s.pane);
        if (PictureFrame.isGlowDust(s.glow.stack)) {
            lines.add(TextFormatting.GRAY + I18n.format("minepapers.tooltip.glow.self"));
        } else if (s.glowLevel() > 0) {
            lines.add(TextFormatting.GRAY + I18n.format("minepapers.tooltip.glow", s.glowLevel()));
        }
        if (s.isSigned()) {
            lines.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + I18n.format("minepapers.tooltip.signer", s.signerName));
        }
        if (s.nsfw) lines.add(TextFormatting.RED + I18n.format("minepapers.tooltip.nsfw"));
        if (s.isLocked()) {
            lines.add(TextFormatting.LIGHT_PURPLE + I18n.format("minepapers.tooltip.owner", s.ownerName));
        }
    }

    /** Рамка или холст: какие они и, если нужен блок, из какого. */
    @SideOnly(Side.CLIENT)
    private static void part(List<String> lines, String key, String kindKey, PictureFrame.Material m) {
        String text = I18n.format(kindKey);
        Item item = m == null || m.item.isEmpty() ? null : Item.getByNameOrId(m.item);
        if (item != null) text += ", " + new ItemStack(item, 1, m.meta).getDisplayName();
        lines.add(TextFormatting.GRAY + I18n.format(key, text));
    }
}
