package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Предметы настраиваемой картины holder (слоты материалов, вставка, сохранение — MaterialSlots) на ItemStack: пустая стопка — ItemStack.EMPTY. Только на сервере. */
public final class HolderMaterials extends MaterialSlots<ItemStack> {

    private final FrameHolder holder;
    private final Player player;
    /** Окно, в котором кликают по слотам (предмет в руке мыши — его). */
    private final AbstractContainerMenu menu;

    public HolderMaterials(FrameHolder holder, Player player, AbstractContainerMenu menu) {
        this.holder = holder;
        this.player = player;
        this.menu = menu;
    }

    /** Без окна — для пакетов: руку мыши они не трогают. */
    public HolderMaterials(FrameHolder holder, Player player) {
        this(holder, player, player.containerMenu);
    }

    @Override
    protected ItemStack empty() {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean isEmpty(ItemStack stack) {
        return stack.isEmpty();
    }

    @Override
    protected int count(ItemStack stack) {
        return stack.getCount();
    }

    @Override
    protected int maxStack(ItemStack stack) {
        return stack.getMaxStackSize();
    }

    @Override
    protected ItemStack copy(ItemStack stack, int count) {
        return stack.copyWithCount(count);
    }

    @Override
    protected void shrink(ItemStack stack, int n) {
        stack.shrink(n);
    }

    @Override
    protected boolean same(ItemStack a, ItemStack b) {
        //? if >=1.20.5 {
        return ItemStack.isSameItemSameComponents(a, b);
        //?} else {
        /*return ItemStack.isSameItemSameTags(a, b);
        *///?}
    }

    @Override
    protected boolean matches(ItemStack stack, PictureConfig.Item item) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item.id);
    }

    @Override
    protected ItemStack create(PictureConfig.Item item) {
        Item found = item(item.id);
        return found != null ? new ItemStack(found) : ItemStack.EMPTY;
    }

    /** Предмет по id из реестра (null — нет такого). */
    public static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null ? BuiltInRegistries.ITEM.getOptional(key).orElse(null) : null;
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
        return holder.frame.materials()[index].stack();
    }

    @Override
    protected void setMaterial(int index, ItemStack stack) {
        holder.frame.materials()[index].set(stack);
    }

    @Override
    protected boolean accepts(int index, ItemStack stack) {
        return !stack.is(MinePapers.FRAME.get()) && holder.frame.materials()[index].accepts(stack);
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
        PictureFrame edited = ItemPictureFrame.read(stack);
        edited.materials()[index].set(material);
        ItemPictureFrame.store(stack, edited);
    }

    @Override
    protected void reload() {
        holder.reload();
    }

    @Override
    protected ItemStack carried() {
        return menu.getCarried();
    }

    @Override
    protected void setCarried(ItemStack stack) {
        menu.setCarried(stack);
    }

    @Override
    protected void addToInventory(ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    @Override
    protected int inventorySize() {
        return player.getInventory().items.size();
    }

    @Override
    protected ItemStack inventoryItem(int slot) {
        return player.getInventory().items.get(slot);
    }

    @Override
    protected ItemStack removeFromInventory(int slot, int n) {
        return player.getInventory().removeItem(slot, n);
    }

    @Override
    protected boolean creative() {
        return player.isCreative();
    }
}
