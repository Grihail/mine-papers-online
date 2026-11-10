package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.client.ImageSync;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
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
    public static final int FIRST_MATERIAL_SLOT = 36, MATERIAL_X = 9, MATERIAL_Y = 8, MATERIAL_STEP = 22;

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
            addSlot(new MaterialSlot(new MaterialContainer(holder, materials[i]), MATERIAL_X, MATERIAL_Y + MaterialSlots.ROW[i] * MATERIAL_STEP));
        }
    }

    /** Что сервер сообщает клиенту при открытии окна: какую картину настраивают (PictureTarget), можно ли менять материалы стопке и лимит веса картинки. */
    public record OpenData(int target, boolean stackMaterials, int maxImageMb) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(target);
            buf.writeBoolean(stackMaterials);
            buf.writeVarInt(maxImageMb);
        }

        public static OpenData read(FriendlyByteBuf buf) {
            return new OpenData(buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
        }
    }

    /** Открыть окно игроку (на сервере). */
    public static void open(ServerPlayer player, FrameHolder holder) {
        boolean stackMaterials = ModConfig.stackMaterials;
        MinePapers.platform.openMenu(player, new SimpleMenuProvider(
            (id, inventory, p) -> new ContainerPictureFrame(id, inventory, holder, stackMaterials),
            Component.translatable("minepapers.gui.title")), new OpenData(holder.target, stackMaterials, ModConfig.maxImageMb));
    }

    /** На клиенте: картину находим по номеру, который сервер прислал при открытии окна. */
    public static ContainerPictureFrame fromData(int id, Inventory inventory, OpenData data) {
        ImageSync.setServerImageMb(data.maxImageMb());
        return new ContainerPictureFrame(id, inventory, FrameHolder.of(inventory.player, data.target()), data.stackMaterials());
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

    /** Слоты материалов — со своей механикой (см. MaterialSlots), её выполняет сервер и присылает результат. Цифры и F меняют предмет под мышью с хотбаром и второй рукой мимо проверок слотов: настраиваемую картину-предмет так тоже не переложить. */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (type == ClickType.SWAP && holder.isItem() && button == holder.slot()) return;
        if (slotId >= 0 && slotId < slots.size() && isMaterialSlot(slotId)) {
            if (!player.level().isClientSide() && holder.frame.mayEdit(player.getUUID()) && materialsEditable()) {
                int index = slotId - FIRST_MATERIAL_SLOT;
                if (type == ClickType.PICKUP) new HolderMaterials(holder, player, this).click(index, button == 1);
                if (type == ClickType.QUICK_MOVE) new HolderMaterials(holder, player, this).takeToInventory(index);
            }
            return;
        }
        super.clicked(slotId, button, type, player);
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
        if (isMaterialSlot(index) || !slot.hasItem() || player.level().isClientSide()
            || !holder.frame.mayEdit(player.getUUID()) || !materialsEditable()) return ItemStack.EMPTY;
        if (new HolderMaterials(holder, player, this).quickMove(slot.getItem())) slot.setChanged();
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
