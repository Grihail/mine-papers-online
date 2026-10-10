package com.grihail.minepapers.block;

import java.util.List;

import com.grihail.minepapers.tile.TilePictureFrame;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/** Предмет рамки: в подсказке видно, какие настройки в нём сохранены. */
public class ItemPictureFrame extends ItemBlock {

    public ItemPictureFrame(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(BlockPictureFrame.ITEM_TAG)) return;
        TilePictureFrame s = new TilePictureFrame();
        s.readSettings(stack.getTagCompound().getCompoundTag(BlockPictureFrame.ITEM_TAG));

        lines.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("minepapers.tooltip.configured"));
        String path = s.source == TilePictureFrame.SOURCE_YANDEX ? s.yandexUrl
            : s.source == TilePictureFrame.SOURCE_FOLDER ? s.folderPath : s.localPath;
        if (path.length() > 40) path = "..." + path.substring(path.length() - 37);
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("minepapers.tooltip.source." + s.source)
            + (path.isEmpty() ? "" : ": " + EnumChatFormatting.WHITE + path));
        lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("minepapers.tooltip.size",
            fmt(s.getWidthBlocks()), fmt(s.getHeightBlocks()))
            + ", " + StatCollector.translateToLocal("minepapers.gui.mode." + s.mode));
        Item border = s.borderItem.isEmpty() ? null : (Item) Item.itemRegistry.getObject(s.borderItem);
        if (border != null) {
            String name;
            try {
                name = new ItemStack(border, 1, s.borderMeta).getDisplayName();
            } catch (Throwable t) {
                name = s.borderItem;
            }
            lines.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted("minepapers.tooltip.border", name));
        }
    }

    private static String fmt(float blocks) {
        if (blocks == Math.round(blocks)) return String.valueOf(Math.round(blocks));
        return String.format(java.util.Locale.ROOT, "%.2f", blocks);
    }
}
