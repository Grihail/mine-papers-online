package com.grihail.minepapers.picture;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.server.ServerImages;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Окно настройки картины: инвентарь игрока и слоты материалов (рамка, холст). Предметы из слотов хранятся в самой картине (PictureFrame.border, canvas); остальные настройки уходят на сервер пакетом PacketSaveFrame. */
public class ContainerPictureFrame extends AbstractContainerMenu {

    /** Верх инвентаря игрока в окне; размер окна — в GuiPictureFrame. */
    public static final int INVENTORY_Y = 164;
    /** Слоты материалов — после 36 слотов игрока, списком сверху вниз: x предмета в окне и y первого, шаг строк. */
    public static final int FIRST_MATERIAL_SLOT = 36, MATERIAL_X = 9, MATERIAL_Y = 19, MATERIAL_STEP = 22;

    public final EntityPicture picture;

    public ContainerPictureFrame(int id, Inventory inventory, EntityPicture picture) {
        super(MinePapers.FRAME_MENU.get(), id);
        this.picture = picture;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        PictureFrame.Material[] materials = picture.frame.materials();
        for (int i = 0; i < materials.length; i++) {
            addSlot(new MaterialSlot(new MaterialContainer(picture, materials[i]), materials[i], inventory.player,
                picture.frame, MATERIAL_X, MATERIAL_Y + i * MATERIAL_STEP));
        }
    }

    /** На клиенте: картину находим по id, который сервер прислал при открытии окна. */
    public static ContainerPictureFrame fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        Entity entity = inventory.player.level().getEntity(buf.readVarInt());
        EntityPicture picture = entity instanceof EntityPicture p ? p
            : new EntityPicture(MinePapers.PICTURE.get(), inventory.player.level()); // картина пропала — пустышка, окно закроется
        return new ContainerPictureFrame(id, inventory, picture);
    }

    public static boolean isMaterialSlot(int index) {
        return index >= FIRST_MATERIAL_SLOT;
    }

    /** Показать или спрятать слоты материалов (окно показывает их только на своей вкладке). */
    public void showMaterials(boolean shown) {
        for (int i = FIRST_MATERIAL_SLOT; i < slots.size(); i++) ((MaterialSlot) slots.get(i)).shown = shown;
    }

    @Override
    public boolean stillValid(Player player) {
        // Ушёл далеко или картину сломали — окно закрывается.
        return !picture.isRemoved() && (!(player instanceof ServerPlayer sp) || ServerImages.canEdit(sp, picture));
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
        /** Чужую заприваченную картину менять нельзя: слоты только для владельца. */
        private final Player player;
        private final PictureFrame frame;
        /** Виден ли слот (меняет только окно на клиенте). */
        boolean shown = true;

        MaterialSlot(Container container, PictureFrame.Material material, Player player, PictureFrame frame, int x, int y) {
            super(container, 0, x, y);
            this.material = material;
            this.player = player;
            this.frame = frame;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && !stack.is(MinePapers.FRAME.get()) && material.accepts(stack)
                && frame.mayEdit(player.getUUID());
        }

        @Override
        public boolean mayPickup(Player player) {
            return frame.mayEdit(player.getUUID());
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

    /** Один слот, который читает и пишет предмет прямо в материал картины. На сервере изменение сразу рассылается игрокам. */
    private static class MaterialContainer implements Container {

        private final EntityPicture picture;
        private final PictureFrame.Material material;

        MaterialContainer(EntityPicture picture, PictureFrame.Material material) {
            this.picture = picture;
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
            if (stack.isEmpty() || count <= 0) return ItemStack.EMPTY;
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
            if (!picture.level().isClientSide) picture.settingsChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !picture.isRemoved();
        }

        @Override
        public void clearContent() {
            setItem(0, ItemStack.EMPTY);
        }
    }
}
