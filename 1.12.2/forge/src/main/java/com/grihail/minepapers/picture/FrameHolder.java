package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;

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
            Entity entity = player.world.getEntityByID(target);
            return entity instanceof EntityPicture && !entity.isDead ? of((EntityPicture) entity, player) : null;
        }
        ItemStack stack = stack(player, target);
        return stack != null ? new FrameHolder(target, read(stack), null, player) : null;
    }

    /** Картина-сущность; player — кто её настраивает (null — никто, меняет сам сервер). */
    public static FrameHolder of(EntityPicture picture, EntityPlayer player) {
        return new FrameHolder(PictureTarget.ofEntity(picture.getEntityId()), picture.frame, picture, player);
    }

    /** Слот второй руки в нумерации InventoryPlayer.getStackInSlot: после основного инвентаря и брони. */
    private static final int SLOT_OFFHAND = 40;

    /** Картина-предмет в руке hand. */
    public static int targetOf(EntityPlayer player, EnumHand hand) {
        return PictureTarget.ofSlot(hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : SLOT_OFFHAND);
    }

    private static ItemStack stack(EntityPlayer player, int target) {
        int slot = PictureTarget.slot(target);
        InventoryPlayer inv = player.inventory;
        if (slot >= inv.getSizeInventory()) return null;
        ItemStack stack = inv.getStackInSlot(slot);
        return stack.getItem() == MinePapers.frame ? stack : null;
    }

    /** Настройки из предмета (не настраивали — как у новой картины). */
    public static PictureFrame read(ItemStack stack) {
        PictureFrame frame = new PictureFrame();
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(ItemPictureFrame.ITEM_TAG)) {
            frame.readSettings(stack.getTagCompound().getCompoundTag(ItemPictureFrame.ITEM_TAG));
        }
        return frame;
    }

    /** Записать настройки в предмет; ненастроенная картина остаётся без них и складывается с новыми. */
    public static void store(ItemStack stack, PictureFrame frame) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        if (frame.isCustomized()) {
            NBTTagCompound settings = new NBTTagCompound();
            frame.writeSettings(settings);
            tag.setTag(ItemPictureFrame.ITEM_TAG, settings);
        } else {
            tag.removeTag(ItemPictureFrame.ITEM_TAG);
        }
        stack.setTagCompound(tag.isEmpty() ? null : tag);
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
        return stack != null ? stack.getCount() : 1;
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
