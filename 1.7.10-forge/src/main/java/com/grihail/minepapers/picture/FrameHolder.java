package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.block.BlockPictureFrame;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Картина, которую настраивает окно (номер — PictureTarget): картина-сущность или картина-предмет в слоте инвентаря игрока. Настройки предмета читаются из него при открытии, а меняет их сервер, записывая обратно в предмет. */
public final class FrameHolder {

    public final int target;
    public final PictureFrame frame;
    /** Картина-сущность; null — предмет. */
    public final EntityPicture picture;
    public final EntityPlayer player;

    private FrameHolder(int target, PictureFrame frame, EntityPicture picture, EntityPlayer player) {
        this.target = target;
        this.frame = frame;
        this.picture = picture;
        this.player = player;
    }

    /** null — картины нет: сущность сломана, предмет убран из слота. */
    public static FrameHolder of(EntityPlayer player, int target) {
        if (!PictureTarget.isItem(target)) {
            Entity entity = player.worldObj.getEntityByID(target);
            return entity instanceof EntityPicture && !entity.isDead ? of((EntityPicture) entity, player) : null;
        }
        ItemStack stack = stack(player, target);
        return stack != null ? new FrameHolder(target, read(stack), null, player) : null;
    }

    /** Картина-сущность; player — кто её настраивает (null — никто, меняет сам сервер). */
    public static FrameHolder of(EntityPicture picture, EntityPlayer player) {
        return new FrameHolder(PictureTarget.ofEntity(picture.getEntityId()), picture.frame, picture, player);
    }

    /** Картина-предмет в руке. */
    public static int targetOfHeld(EntityPlayer player) {
        return PictureTarget.ofSlot(player.inventory.currentItem);
    }

    private static ItemStack stack(EntityPlayer player, int target) {
        int slot = PictureTarget.slot(target);
        if (slot >= player.inventory.mainInventory.length) return null;
        ItemStack stack = player.inventory.mainInventory[slot];
        return stack != null && stack.getItem() == Item.getItemFromBlock(MinePapers.frame) ? stack : null;
    }

    /** Настройки из предмета (не настраивали — как у новой картины). */
    public static PictureFrame read(ItemStack stack) {
        PictureFrame frame = new PictureFrame();
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(BlockPictureFrame.ITEM_TAG)) {
            frame.readSettings(stack.getTagCompound().getCompoundTag(BlockPictureFrame.ITEM_TAG));
        }
        return frame;
    }

    /** Записать настройки в предмет; ненастроенная картина остаётся без них и складывается с новыми. */
    public static void store(ItemStack stack, PictureFrame frame) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        if (frame.isCustomized()) {
            NBTTagCompound settings = new NBTTagCompound();
            frame.writeSettings(settings);
            tag.setTag(BlockPictureFrame.ITEM_TAG, settings);
        } else {
            tag.removeTag(BlockPictureFrame.ITEM_TAG);
        }
        stack.setTagCompound(tag.hasNoTags() ? null : tag);
    }

    public boolean isItem() {
        return picture == null;
    }

    /** Слот инвентаря с картиной-предметом (-1 — это сущность). */
    public int slot() {
        return isItem() ? PictureTarget.slot(target) : -1;
    }

    /** Картина на месте: сущность не сломана, предмет не убрали из слота. */
    public boolean exists() {
        return picture != null ? !picture.isDead : stack(player, target) != null;
    }

    /** Картина-предмет (null — это сущность или предмета уже нет). */
    public ItemStack stack() {
        return isItem() ? stack(player, target) : null;
    }

    /** Сколько картин настраивается: вся стопка картин-предметов или одна висящая картина. */
    public int count() {
        ItemStack stack = stack();
        return stack != null ? stack.stackSize : 1;
    }

    /** Перечитать настройки из предмета: его могли поменять мимо окна (выложены картинки, отделились картины). */
    public void reload() {
        ItemStack stack = stack();
        if (stack == null) return;
        NBTTagCompound settings = new NBTTagCompound();
        read(stack).writeSettings(settings);
        frame.readSettings(settings);
    }

    /** Настройки изменились (на сервере): сущность рассылает их игрокам, предмет записывает в себя. */
    public void settingsChanged() {
        if (picture != null) {
            picture.settingsChanged();
            return;
        }
        ItemStack stack = stack(player, target);
        if (stack != null) store(stack, frame);
    }
}
