package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Предметы настраиваемой картины holder (слоты материалов, вставка, сохранение — MaterialSlots) на стопках 1.7.10: пустая стопка — null, у предмета есть метаданные. Только на сервере. */
public final class HolderMaterials extends MaterialSlots<ItemStack> {

    private final FrameHolder holder;
    private final EntityPlayer player;

    public HolderMaterials(FrameHolder holder, EntityPlayer player) {
        this.holder = holder;
        this.player = player;
    }

    @Override
    protected ItemStack empty() {
        return null;
    }

    @Override
    protected boolean isEmpty(ItemStack stack) {
        return stack == null || stack.stackSize <= 0;
    }

    @Override
    protected int count(ItemStack stack) {
        return stack.stackSize;
    }

    @Override
    protected int maxStack(ItemStack stack) {
        return stack.getMaxStackSize();
    }

    @Override
    protected ItemStack copy(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.stackSize = count;
        return copy;
    }

    @Override
    protected void shrink(ItemStack stack, int n) {
        stack.stackSize -= n;
    }

    @Override
    protected boolean same(ItemStack a, ItemStack b) {
        return a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    @Override
    protected boolean matches(ItemStack stack, PictureConfig.Item item) {
        return item.id.equals(Item.itemRegistry.getNameForObject(stack.getItem()))
            && (item.meta < 0 || stack.getItemDamage() == item.meta);
    }

    @Override
    protected ItemStack create(PictureConfig.Item item) {
        Item found = (Item) Item.itemRegistry.getObject(item.id);
        return found != null ? new ItemStack(found, 1, Math.max(item.meta, 0)) : null;
    }

    @Override
    protected PictureSettings frame() {
        return holder.frame;
    }

    @Override
    protected int pictures() {
        return holder.count();
    }

    @Override
    protected ItemStack material(int index) {
        return holder.frame.materials()[index].stack;
    }

    @Override
    protected void setMaterial(int index, ItemStack stack) {
        holder.frame.materials()[index].set(stack);
    }

    @Override
    protected boolean accepts(int index, ItemStack stack) {
        return stack.getItem() != Item.getItemFromBlock(MinePapers.frame) && holder.frame.materials()[index].accepts(stack);
    }

    @Override
    protected void settingsChanged() {
        holder.settingsChanged();
    }

    @Override
    protected ItemStack held() {
        return holder.stack();
    }

    @Override
    protected void storeMaterial(ItemStack stack, int index, ItemStack material) {
        PictureFrame edited = FrameHolder.read(stack);
        edited.materials()[index].set(material);
        FrameHolder.store(stack, edited);
    }

    @Override
    protected void reload() {
        holder.reload();
    }

    @Override
    protected ItemStack carried() {
        return player.inventory.getItemStack();
    }

    @Override
    protected void setCarried(ItemStack stack) {
        player.inventory.setItemStack(stack);
    }

    @Override
    protected void addToInventory(ItemStack stack) {
        if (!player.inventory.addItemStackToInventory(stack)) player.dropPlayerItemWithRandomChoice(stack, false);
    }

    @Override
    protected int inventorySize() {
        return player.inventory.mainInventory.length;
    }

    @Override
    protected ItemStack inventoryItem(int slot) {
        return player.inventory.mainInventory[slot];
    }

    @Override
    protected ItemStack removeFromInventory(int slot, int n) {
        return player.inventory.decrStackSize(slot, n);
    }

    @Override
    protected boolean creative() {
        return player.capabilities.isCreativeMode;
    }
}
