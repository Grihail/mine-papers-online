package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Картина, которую настраивает окно (номер — PictureTarget): картина-сущность или картина-предмет в слоте инвентаря игрока. Настройки предмета читаются из него при открытии, а меняет их сервер, записывая обратно в предмет. */
public final class FrameHolder {

    public final int target;
    public final PictureFrame frame;
    /** Картина-сущность; null — предмет. */
    public final EntityPicture picture;
    public final Player player;

    private FrameHolder(int target, PictureFrame frame, EntityPicture picture, Player player) {
        this.target = target;
        this.frame = frame;
        this.picture = picture;
        this.player = player;
    }

    /** null — картины нет: сущность сломана, предмет убран из слота. */
    public static FrameHolder of(Player player, int target) {
        if (!PictureTarget.isItem(target)) {
            Entity entity = player.level().getEntity(target);
            return entity instanceof EntityPicture picture && !picture.isRemoved() ? of(picture, player) : null;
        }
        ItemStack stack = stack(player, target);
        return stack != null ? new FrameHolder(target, ItemPictureFrame.read(stack), null, player) : null;
    }

    /** Картина-сущность; player — кто её настраивает (null — никто, меняет сам сервер). */
    public static FrameHolder of(EntityPicture picture, Player player) {
        return new FrameHolder(PictureTarget.ofEntity(picture.getId()), picture.frame, picture, player);
    }

    /** Картина-предмет в руке hand. */
    public static int targetOf(Player player, InteractionHand hand) {
        Inventory inv = player.getInventory();
        return PictureTarget.ofSlot(hand == InteractionHand.MAIN_HAND ? inv.selected : Inventory.SLOT_OFFHAND);
    }

    private static ItemStack stack(Player player, int target) {
        int slot = PictureTarget.slot(target);
        Inventory inv = player.getInventory();
        if (slot >= inv.getContainerSize()) return null;
        ItemStack stack = inv.getItem(slot);
        return stack.is(MinePapers.FRAME.get()) ? stack : null;
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
        return picture != null ? !picture.isRemoved() : stack(player, target) != null;
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
        CompoundTag settings = new CompoundTag();
        ItemPictureFrame.read(stack).writeSettings(settings);
        frame.readSettings(settings);
    }

    /** Настройки изменились (на сервере): сущность рассылает их игрокам, предмет записывает в себя. */
    public void settingsChanged() {
        if (picture != null) {
            picture.settingsChanged();
            return;
        }
        ItemStack stack = stack(player, target);
        if (stack != null) ItemPictureFrame.store(stack, frame);
    }
}
