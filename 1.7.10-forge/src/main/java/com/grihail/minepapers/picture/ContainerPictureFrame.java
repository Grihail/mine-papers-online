package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Окно настройки картины: инвентарь игрока и слоты материалов (рамка, холст). Предметы из слотов хранятся в самой картине (PictureFrame.border, canvas); остальные настройки уходят на сервер пакетом PacketSaveFrame. */
public class ContainerPictureFrame extends Container {

    /** Верх инвентаря игрока в окне; размер окна — в GuiPictureFrame. */
    public static final int INVENTORY_Y = 164;
    /** Слоты материалов — после 36 слотов игрока, списком сверху вниз: x предмета в окне и y первого, шаг строк. */
    public static final int FIRST_MATERIAL_SLOT = 36, MATERIAL_X = 9, MATERIAL_Y = 19, MATERIAL_STEP = 22;

    public final EntityPicture picture;

    public ContainerPictureFrame(InventoryPlayer inventory, EntityPicture picture) {
        this.picture = picture;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        PictureFrame.Material[] materials = picture.frame.materials();
        for (int i = 0; i < materials.length; i++) {
            addSlotToContainer(new MaterialSlot(new MaterialInventory(picture, materials[i]), materials[i],
                inventory.player, picture.frame, MATERIAL_X, MATERIAL_Y + i * MATERIAL_STEP));
        }
    }

    public boolean isMaterialSlot(int index) {
        return index >= FIRST_MATERIAL_SLOT;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        // Вызывается только на сервере: ушёл далеко или картину сломали — окно закрывается.
        return !picture.isDead && (!(player instanceof EntityPlayerMP)
            || ServerImages.canEdit((EntityPlayerMP) player, picture));
    }

    /** Shift+клик: из инвентаря — один предмет в первый пустой слот материала, из слота материала — обратно в инвентарь. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack before = stack.copy();
        if (isMaterialSlot(index)) {
            if (!mergeItemStack(stack, 0, FIRST_MATERIAL_SLOT, true)) return null;
        } else {
            Slot target = null;
            for (int i = FIRST_MATERIAL_SLOT; i < inventorySlots.size() && target == null; i++) {
                Slot material = (Slot) inventorySlots.get(i);
                if (!material.getHasStack() && material.isItemValid(stack)) target = material;
            }
            if (target == null) return null;
            target.putStack(stack.splitStack(1));
        }
        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        return before;
    }

    /** Слот на один предмет; саму рамку для картин класть нельзя (картина в картине), в слот свечения — только светящиеся блоки. */
    private static class MaterialSlot extends Slot {

        private final PictureFrame.Material material;
        /** Чужую заприваченную картину менять нельзя: слоты только для владельца. */
        private final EntityPlayer player;
        private final PictureFrame frame;

        MaterialSlot(IInventory inventory, PictureFrame.Material material, EntityPlayer player, PictureFrame frame, int x,
                     int y) {
            super(inventory, 0, x, y);
            this.material = material;
            this.player = player;
            this.frame = frame;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return stack != null && stack.getItem() != Item.getItemFromBlock(MinePapers.frame) && material.accepts(stack)
                && frame.mayEdit(player.getUniqueID());
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return frame.mayEdit(player.getUniqueID());
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }

    /** Один слот, который читает и пишет предмет прямо в материал картины. На сервере изменение сразу рассылается игрокам. */
    private static class MaterialInventory implements IInventory {

        private final EntityPicture picture;
        private final PictureFrame.Material material;

        MaterialInventory(EntityPicture picture, PictureFrame.Material material) {
            this.picture = picture;
            this.material = material;
        }

        @Override
        public int getSizeInventory() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            return material.stack;
        }

        @Override
        public ItemStack decrStackSize(int index, int count) {
            ItemStack stack = material.stack;
            setInventorySlotContents(0, null);
            return stack;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int index) {
            return null; // предмет остаётся в картине
        }

        @Override
        public void setInventorySlotContents(int index, ItemStack stack) {
            material.set(stack);
            markDirty();
        }

        @Override
        public String getInventoryName() {
            return "container.minepapers.material";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return false;
        }

        @Override
        public int getInventoryStackLimit() {
            return 1;
        }

        @Override
        public void markDirty() {
            if (!picture.worldObj.isRemote) picture.settingsChanged();
        }

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return !picture.isDead;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) {
            return true;
        }
    }
}
