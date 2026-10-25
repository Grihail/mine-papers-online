package com.grihail.minepapers.picture;

import com.grihail.minepapers.server.ServerImages;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/** Окно настройки картины (сущности или стопки картин-предметов в руке, см. FrameHolder): инвентарь игрока и слоты материалов (рамка, холст, свечение). Предметы из слотов хранятся в самих картинах (PictureFrame.border, canvas, glow), по одному на картину; остальные настройки уходят на сервер пакетом PacketSaveFrame. */
public class ContainerPictureFrame extends Container {

    /** Верх инвентаря игрока в окне; размер окна — в GuiPictureFrame. */
    public static final int INVENTORY_Y = 164;
    /** Слоты материалов — после 36 слотов игрока, списком сверху вниз: x предмета в окне и y первого, шаг строк. */
    public static final int FIRST_MATERIAL_SLOT = 36, MATERIAL_X = 9, MATERIAL_Y = 8, MATERIAL_STEP = 22;

    public final FrameHolder holder;
    /** Стопка картин меняет материалы целиком (ModConfig.stackMaterials сервера; клиент получает его при открытии окна). */
    private final boolean stackMaterials;

    public ContainerPictureFrame(InventoryPlayer inventory, FrameHolder holder, boolean stackMaterials) {
        this.holder = holder;
        this.stackMaterials = stackMaterials;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(inventorySlot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(inventorySlot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        PictureFrame.Material[] materials = holder.frame.materials();
        for (int i = 0; i < materials.length; i++) {
            addSlotToContainer(new MaterialSlot(new MaterialInventory(holder, materials[i]), MATERIAL_X,
                MATERIAL_Y + i * MATERIAL_STEP));
        }
    }

    /** Слот инвентаря; в слоте с настраиваемой картиной-предметом она заперта: её не взять, и туда ничего не положить. */
    private Slot inventorySlot(InventoryPlayer inventory, int index, int x, int y) {
        if (index != holder.slot()) return new Slot(inventory, index, x, y);
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean canTakeStack(EntityPlayer player) {
                return false;
            }

            @Override
            public boolean isItemValid(ItemStack stack) {
                return false;
            }
        };
    }

    public boolean isMaterialSlot(int index) {
        return index >= FIRST_MATERIAL_SLOT;
    }

    /** Можно ли менять материалы: у одной картины — всегда, у стопки — если сервер разрешает (см. MaterialStack.editable). */
    public boolean materialsEditable() {
        return MaterialStack.editable(stackMaterials, holder.count());
    }

    /** Слоты материалов — со своей механикой (см. MaterialSlots), её выполняет сервер и присылает окно целиком. Цифры меняют предмет под мышью с хотбаром мимо проверок слотов: настраиваемую картину-предмет так тоже не переложить. */
    @Override
    public ItemStack slotClick(int slotId, int button, ClickType type, EntityPlayer player) {
        if (type == ClickType.SWAP && holder.isItem() && button == holder.slot()) return ItemStack.EMPTY;
        if (slotId >= 0 && slotId < inventorySlots.size() && isMaterialSlot(slotId)) {
            if (player instanceof EntityPlayerMP && holder.frame.mayEdit(player.getUniqueID()) && materialsEditable()) {
                int index = slotId - FIRST_MATERIAL_SLOT;
                if (type == ClickType.PICKUP) new HolderMaterials(holder, player).click(index, button == 1);
                if (type == ClickType.QUICK_MOVE) new HolderMaterials(holder, player).takeToInventory(index);
                resend((EntityPlayerMP) player);
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, button, type, player);
    }

    /** Клиент изменения слотов материалов не предсказывает, а сервер после клика рассылает только то, что клиент якобы уже знает, — поэтому окно (вместе с предметом в руке мыши) шлём заново. */
    private void resend(EntityPlayerMP player) {
        player.sendContainerToPlayer(this);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        // Ушёл далеко, картину сломали или предмет пропал из слота — окно закрывается.
        return holder.exists() && (!(player instanceof EntityPlayerMP)
            || ServerImages.canEdit((EntityPlayerMP) player, holder));
    }

    /** Shift+клик по инвентарю: блоки — в первый пустой подходящий слот материала (по блоку на картину). Слоты материалов обрабатывает slotClick. */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = inventorySlots.get(index);
        if (isMaterialSlot(index) || slot == null || !slot.getHasStack() || !slot.canTakeStack(player)
            || !(player instanceof EntityPlayerMP) || !holder.frame.mayEdit(player.getUniqueID()) || !materialsEditable()) {
            return ItemStack.EMPTY;
        }
        if (new HolderMaterials(holder, player).quickMove(slot.getStack())) {
            slot.onSlotChanged();
            resend((EntityPlayerMP) player);
        }
        return ItemStack.EMPTY;
    }

    /** Слот материала: показывает блок с числом картин, ванильная механика слотов его не трогает (см. slotClick). */
    private static class MaterialSlot extends Slot {

        MaterialSlot(IInventory inventory, int x, int y) {
            super(inventory, 0, x, y);
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeStack(EntityPlayer player) {
            return false;
        }
    }

    /** Материал картины как слот: блок материала, сколько картин — столько и блоков. Запись (setInventorySlotContents) приходит только с сервера на клиент: меняет материал в настройках окна. */
    private static class MaterialInventory implements IInventory {

        private final FrameHolder holder;
        private final PictureFrame.Material material;

        MaterialInventory(FrameHolder holder, PictureFrame.Material material) {
            this.holder = holder;
            this.material = material;
        }

        @Override
        public int getSizeInventory() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return material.stack.isEmpty();
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            if (material.stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack stack = material.stack.copy();
            stack.setCount(holder.count());
            return stack;
        }

        @Override
        public ItemStack decrStackSize(int index, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStackFromSlot(int index) {
            return ItemStack.EMPTY; // предмет остаётся в картине
        }

        @Override
        public void setInventorySlotContents(int index, ItemStack stack) {
            material.set(stack);
        }

        @Override
        public int getInventoryStackLimit() {
            return 64;
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUsableByPlayer(EntityPlayer player) {
            return holder.exists();
        }

        @Override
        public void openInventory(EntityPlayer player) {}

        @Override
        public void closeInventory(EntityPlayer player) {}

        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) {
            return false;
        }

        @Override
        public int getField(int id) {
            return 0;
        }

        @Override
        public void setField(int id, int value) {}

        @Override
        public int getFieldCount() {
            return 0;
        }

        @Override
        public void clear() {}

        @Override
        public String getName() {
            return "container.minepapers.material";
        }

        @Override
        public boolean hasCustomName() {
            return false;
        }

        @Override
        public ITextComponent getDisplayName() {
            return new TextComponentTranslation(getName());
        }
    }
}
