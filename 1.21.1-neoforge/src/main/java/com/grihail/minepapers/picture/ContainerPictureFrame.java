package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.item.ItemPictureFrame;
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

/** Окно настройки картины (сущности или стопки картин-предметов в руке, см. FrameHolder): инвентарь игрока и слоты материалов (рамка, холст, свечение). Предметы из слотов хранятся в самих картинах (PictureFrame.border, canvas, glow), по одному на картину; остальные настройки уходят на сервер пакетом PacketSaveFrame. */
public class ContainerPictureFrame extends AbstractContainerMenu {

    /** Верх инвентаря игрока в окне; размер окна — в GuiPictureFrame. */
    public static final int INVENTORY_Y = 164;
    /** Слоты материалов — после 36 слотов игрока, списком сверху вниз: x предмета в окне и y первого, шаг строк. */
    public static final int FIRST_MATERIAL_SLOT = 36, MATERIAL_X = 9, MATERIAL_Y = 19, MATERIAL_STEP = 22;

    public final FrameHolder holder;
    /** Стопка картин меняет материалы целиком (ModConfig.stackMaterials сервера; клиент получает его при открытии окна). */
    private final boolean stackMaterials;

    public ContainerPictureFrame(int id, Inventory inventory, FrameHolder holder, boolean stackMaterials) {
        super(MinePapers.FRAME_MENU.get(), id);
        this.holder = holder;
        this.stackMaterials = stackMaterials;
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
            addSlot(new MaterialSlot(new MaterialContainer(holder, materials[i]), MATERIAL_X, MATERIAL_Y + i * MATERIAL_STEP));
        }
    }

    /** Открыть окно игроку (на сервере); клиент узнаёт картину по номеру из PictureTarget, а заодно — можно ли менять материалы стопке. */
    public static void open(ServerPlayer player, FrameHolder holder) {
        boolean stackMaterials = ModConfig.stackMaterials;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ContainerPictureFrame(id, inventory, holder, stackMaterials),
            Component.translatable("minepapers.gui.title")), buf -> {
                buf.writeVarInt(holder.target);
                buf.writeBoolean(stackMaterials);
            });
    }

    /** На клиенте: картину находим по номеру, который сервер прислал при открытии окна. */
    public static ContainerPictureFrame fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        FrameHolder holder = FrameHolder.of(inventory.player, buf.readVarInt());
        return new ContainerPictureFrame(id, inventory, holder, buf.readBoolean());
    }

    /** Можно ли менять материалы: у одной картины — всегда, у стопки — если сервер разрешает (см. MaterialStack.editable). */
    public boolean materialsEditable() {
        return MaterialStack.editable(stackMaterials, holder.count());
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

    /** Слоты материалов — со своей механикой (см. MaterialStack), её выполняет сервер и присылает результат. Цифры и F меняют предмет под мышью с хотбаром и второй рукой мимо проверок слотов: настраиваемую картину-предмет так тоже не переложить. */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (type == ClickType.SWAP && holder.isItem() && button == holder.slot()) return;
        if (slotId >= 0 && slotId < slots.size() && isMaterialSlot(slotId)) {
            if (!player.level().isClientSide && holder.frame.mayEdit(player.getUUID()) && materialsEditable()) {
                int index = slotId - FIRST_MATERIAL_SLOT;
                if (type == ClickType.PICKUP) clickMaterial(index, button == 1, player);
                if (type == ClickType.QUICK_MOVE) takeToInventory(index, player);
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** Клик по слоту материала: пустой рукой — забрать блоки (картины остаются без материала), с блоками — положить или заменить. */
    private void clickMaterial(int index, boolean right, Player player) {
        PictureFrame.Material m = holder.frame.materials()[index];
        ItemStack current = m.stack();
        ItemStack carried = getCarried();
        int pictures = holder.count();
        if (carried.isEmpty()) {
            if (current.isEmpty()) return;
            setCarried(change(index, ItemStack.EMPTY, MaterialStack.takeCount(pictures, right, current.getMaxStackSize()), player));
            return;
        }
        if (!accepts(m, carried) || ItemStack.isSameItemSameComponents(current, carried)) return;
        int count = MaterialStack.putCount(pictures, carried.getCount(), right);
        ItemStack old = change(index, carried, count, player);
        carried.shrink(count);
        if (carried.isEmpty() && old.getCount() <= old.getMaxStackSize()) {
            setCarried(old);
        } else {
            give(player, old);
        }
    }

    /** Shift+клик по слоту материала: все блоки — в инвентарь. */
    private void takeToInventory(int index, Player player) {
        ItemStack current = holder.frame.materials()[index].stack();
        if (current.isEmpty()) return;
        give(player, change(index, ItemStack.EMPTY, holder.count(), player));
    }

    /** У count картин материал index становится material (пустой — снять). Изменились не все картины стопки — изменённые отделяются от неё и уходят в инвентарь. Только на сервере. @return блоки прежнего материала, по одному с каждой изменённой картины */
    private ItemStack change(int index, ItemStack material, int count, Player player) {
        ItemStack old = holder.frame.materials()[index].stack();
        ItemStack held = holder.stack();
        if (held == null) {
            // Висящая картина — одна.
            holder.frame.materials()[index].set(material);
            holder.settingsChanged();
        } else {
            PictureFrame edited = ItemPictureFrame.read(held);
            edited.materials()[index].set(material);
            if (count >= held.getCount()) {
                ItemPictureFrame.store(held, edited);
            } else {
                ItemStack split = held.copyWithCount(count);
                ItemPictureFrame.store(split, edited);
                held.shrink(count);
                give(player, split);
            }
            holder.reload();
        }
        return old.isEmpty() ? ItemStack.EMPTY : old.copyWithCount(count);
    }

    /** В инвентарь, а что не влезло — на землю. Пачками не больше стопки. */
    private static void give(Player player, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(stack.getMaxStackSize());
            if (!player.getInventory().add(part)) player.drop(part, false);
        }
    }

    /** Саму рамку для картин класть нельзя (картина в картине), в слот свечения — только светящиеся блоки. */
    private static boolean accepts(PictureFrame.Material m, ItemStack stack) {
        return !stack.is(MinePapers.FRAME.get()) && m.accepts(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        // Ушёл далеко, картину сломали или предмет пропал из слота — окно закрывается.
        return holder.exists() && (!(player instanceof ServerPlayer sp) || ServerImages.canEdit(sp, holder));
    }

    /** Shift+клик по инвентарю: блоки — в первый пустой подходящий слот материала (по блоку на картину). Слоты материалов обрабатывает clicked. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (isMaterialSlot(index) || !slot.hasItem() || player.level().isClientSide
            || !holder.frame.mayEdit(player.getUUID()) || !materialsEditable()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        PictureFrame.Material[] materials = holder.frame.materials();
        for (int i = 0; i < materials.length; i++) {
            if (!materials[i].stack().isEmpty() || !accepts(materials[i], stack)) continue;
            int count = MaterialStack.putCount(holder.count(), stack.getCount(), false);
            change(i, stack, count, player);
            stack.shrink(count);
            slot.setChanged();
            break;
        }
        return ItemStack.EMPTY;
    }

    /** Слот материала: показывает блок с числом картин, ванильная механика слотов его не трогает (см. clicked). Окно прячет его на других вкладках. */
    private static class MaterialSlot extends Slot {

        /** Виден ли слот (меняет только окно на клиенте). */
        boolean shown = true;

        MaterialSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return shown;
        }
    }

    /** Материал картины как слот: блок материала, сколько картин — столько и блоков. Запись (setItem) приходит только с сервера на клиент: меняет материал в настройках окна. */
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
            ItemStack stack = material.stack();
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(holder.count());
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            material.set(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return holder.exists();
        }

        @Override
        public void clearContent() {}
    }
}
