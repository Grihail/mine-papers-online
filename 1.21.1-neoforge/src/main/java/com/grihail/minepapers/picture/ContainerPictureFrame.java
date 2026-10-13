package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Окно настройки картины (сущности или предмета в руке, см. FrameHolder): инвентарь игрока и слоты материалов (рамка, холст). Предметы из слотов хранятся в самой картине (PictureFrame.border, canvas); остальные настройки уходят на сервер пакетом PacketSaveFrame. */
public class ContainerPictureFrame extends AbstractContainerMenu {

    /** Верх инвентаря игрока в окне; размер окна — в GuiPictureFrame. */
    public static final int INVENTORY_Y = 164;
    /** Слоты материалов — после 36 слотов игрока, списком сверху вниз: x предмета в окне и y первого, шаг строк. */
    public static final int FIRST_MATERIAL_SLOT = 36, MATERIAL_X = 9, MATERIAL_Y = 19, MATERIAL_STEP = 22;

    public final FrameHolder holder;

    public ContainerPictureFrame(int id, Inventory inventory, FrameHolder holder) {
        super(MinePapers.FRAME_MENU.get(), id);
        this.holder = holder;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(inventorySlot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(inventorySlot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        PictureFrame.Material[] materials = holder.frame.materials();
        for (int i = 0; i < materials.length; i++) {
            addSlot(new MaterialSlot(new MaterialContainer(holder, materials[i]), materials[i], holder,
                MATERIAL_X, MATERIAL_Y + i * MATERIAL_STEP));
        }
    }

    /** Открыть окно игроку (на сервере); клиент узнаёт картину по номеру из PictureTarget. */
    public static void open(ServerPlayer player, FrameHolder holder) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ContainerPictureFrame(id, inventory, holder),
            Component.translatable("minepapers.gui.title")), buf -> buf.writeVarInt(holder.target));
    }

    /** На клиенте: картину находим по номеру, который сервер прислал при открытии окна. */
    public static ContainerPictureFrame fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new ContainerPictureFrame(id, inventory, FrameHolder.of(inventory.player, buf.readVarInt()));
    }

    /** Слот инвентаря; в слоте с настраиваемой картиной-предметом она заперта: её не взять, и туда ничего не положить. */
    private Slot inventorySlot(Inventory inventory, int index, int x, int y) {
        if (index != holder.slot()) return new Slot(inventory, index, x, y);
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    public static boolean isMaterialSlot(int index) {
        return index >= FIRST_MATERIAL_SLOT;
    }

    /** Показать или спрятать слоты материалов (окно показывает их только на своей вкладке). */
    public void showMaterials(boolean shown) {
        for (int i = FIRST_MATERIAL_SLOT; i < slots.size(); i++) ((MaterialSlot) slots.get(i)).shown = shown;
    }

    /** Цифры и F меняют предмет под мышью с хотбаром и второй рукой мимо проверок слотов: настраиваемую картину-предмет так тоже не переложить. */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (type == ClickType.SWAP && holder.isItem() && button == holder.slot()) return;
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean stillValid(Player player) {
        // Ушёл далеко, картину сломали или предмет пропал из слота — окно закрывается.
        return holder.exists() && (!(player instanceof ServerPlayer sp) || ServerImages.canEdit(sp, holder));
    }

    /** Shift+клик: из инвентаря — один предмет в первый пустой слот материала, из слота материала — обратно в инвентарь. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (isMaterialSlot(index)) {
            if (!moveItemStackTo(stack, 0, FIRST_MATERIAL_SLOT, true)) return ItemStack.EMPTY;
        } else {
            Slot target = null;
            for (int i = FIRST_MATERIAL_SLOT; i < slots.size() && target == null; i++) {
                Slot material = slots.get(i);
                if (!material.hasItem() && material.mayPlace(stack)) target = material;
            }
            if (target == null) return ItemStack.EMPTY;
            target.set(stack.split(1));
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    /** Слот на один предмет; саму рамку для картин класть нельзя (картина в картине), в слот свечения — только светящиеся блоки. Окно прячет его на других вкладках. */
    private static class MaterialSlot extends Slot {

        private final PictureFrame.Material material;
        private final FrameHolder holder;
        /** Виден ли слот (меняет только окно на клиенте). */
        boolean shown = true;

        MaterialSlot(Container container, PictureFrame.Material material, FrameHolder holder, int x, int y) {
            super(container, 0, x, y);
            this.material = material;
            this.holder = holder;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.is(MinePapers.FRAME.get()) && material.accepts(stack) && mayChange();
        }

        @Override
        public boolean mayPickup(Player player) {
            return mayChange();
        }

        /** Чужую заприваченную картину менять нельзя — слоты только для владельца; у стопки картин-предметов — никому. */
        private boolean mayChange() {
            return holder.frame.mayEdit(holder.player.getUUID()) && holder.materialsEditable();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return shown;
        }
    }

    /** Один слот, который читает и пишет предмет прямо в материал картины. На сервере изменение сразу рассылается игрокам (или записывается в картину-предмет). */
    private static class MaterialContainer implements Container {

        private final FrameHolder holder;
        private final PictureFrame.Material material;

        MaterialContainer(FrameHolder holder, PictureFrame.Material material) {
            this.holder = holder;
            this.material = material;
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return material.stack().isEmpty();
        }

        @Override
        public ItemStack getItem(int index) {
            return material.stack();
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack stack = material.stack().copy();
            setItem(0, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            return removeItem(index, 1);
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            material.set(stack);
            setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            if (!holder.player.level().isClientSide) holder.settingsChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return holder.exists();
        }

        @Override
        public void clearContent() {
            setItem(0, ItemStack.EMPTY);
        }
    }
}
