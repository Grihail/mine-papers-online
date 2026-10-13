package com.grihail.minepapers.client;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketPasteMaterials;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.Numbers;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.Links;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/** Окно настройки картины: вкладки слева, под настройками — инвентарь игрока. Настройки применяются при закрытии окна. */
public class GuiPictureFrame extends GuiContainer implements GuiYesNoCallback {

    private static final int TAB_SOURCE = 0, TAB_SIZE = 1, TAB_STYLE = 2, TAB_FRAME = 3, TAB_INFO = 4, TAB_COUNT = 5;

    /** id кнопок: вкладки — 0..3, группы переключателей — начало группы + значение. */
    private static final int ID_TAB = 0, ID_SOURCE = 10, ID_FIT = 20, ID_MODE = 30, ID_STYLE = 40,
        ID_PREV = 50, ID_NEXT = 51, ID_FOLDER = 52, ID_RELOAD = 53, ID_SIZE_PX = 54, ID_POS_PX = 55,
        ID_COPY = 56, ID_PASTE = 57, ID_PAD_PX = 58, ID_LOCK = 59;

    /** Ширина — как у сундука; низ окна — часть его текстуры с инвентарём, слоты в ней на 14 пикселей ниже её верха. */
    private static final int WIDTH = 176,
        HEIGHT = ContainerPictureFrame.INVENTORY_Y - GuiWidgets.INVENTORY_PART_SLOTS_Y + GuiWidgets.INVENTORY_PART_HEIGHT;

    /** Последняя открытая вкладка — с неё окно откроется в следующий раз. */
    private static int lastTab = TAB_SOURCE;

    /** Картина-сущность или картина-предмет в руке. */
    private final FrameHolder holder;
    private final PictureFrame tile;

    private int tab = lastTab;
    private int source, fitMode, style, mode;
    private boolean sizePx, posPx, padPx;
    /** Замок закрыт (картина заприватится при закрытии окна). */
    private boolean locked;
    /** Чужая заприваченная картина: без вкладок, только заголовок и описание. */
    private final boolean readOnly;
    private final UUID me;
    private final String myName;
    private GuiWidgets.Lock btnLock;

    private final List<GuiWidgets.Tab> tabs = new ArrayList<GuiWidgets.Tab>();
    private final List<GuiWidgets.Radio> radios = new ArrayList<GuiWidgets.Radio>();
    /** Источник и вписывание — ряды кнопок с картинками; название и пояснение — в подсказке. */
    private final List<GuiWidgets.Toggle> sourceButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> fitButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Многострочные поля (путь и ссылки): им нужны перетаскивание мыши и колёсико. */
    private final List<GuiTextArea> areas = new ArrayList<GuiTextArea>();
    /** Вкладка, на которой лежит кнопка (у самих вкладок записи нет — они видны всегда). */
    private final Map<GuiButton, Integer> buttonTab = new HashMap<GuiButton, Integer>();
    private GuiWidgets.Small btnPrev, btnNext, btnFolder, btnReload, btnCopy, btnPaste;
    private GuiWidgets.Check cbSizePx, cbPosPx, cbPadPx;

    private GuiTextField fPath, fUrl, fFolder, fImage, fInterval, fWidth, fHeight, fOffX, fOffY, fPad, fPpb, fTitle, fDesc;
    private final List<GuiTextField> fields = new ArrayList<GuiTextField>();
    /** Поле -> {вкладка, x, y, ширина, высота}: видимость и подсказки при наведении. */
    private final Map<GuiTextField, int[]> fieldRect = new HashMap<GuiTextField, int[]>();
    /** Однострочное поле, в котором зажали кнопку мыши: перетаскивание выделяет в нём текст. */
    private GuiTextField dragField;

    private boolean statusError;

    public GuiPictureFrame(ContainerPictureFrame container) {
        super(container);
        this.holder = container.holder;
        this.tile = holder.frame;
        this.source = tile.source;
        this.fitMode = tile.fitMode;
        this.style = tile.style;
        this.mode = tile.mode;
        this.sizePx = tile.sizeInPixels;
        this.posPx = tile.offsetInPixels;
        this.padPx = tile.paddingInPixels;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        this.me = player.getUniqueID();
        this.myName = player.getCommandSenderName();
        this.locked = tile.isLocked();
        this.readOnly = !tile.mayEdit(me);
        this.xSize = WIDTH;
        this.ySize = HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        // При изменении размера окна initGui вызывается снова — сохраняем то, что уже введено.
        List<String> old = null;
        if (!fields.isEmpty()) {
            old = new ArrayList<String>();
            for (GuiTextField f : fields) old.add(f.getText());
        }
        fields.clear();
        fieldRect.clear();
        tabs.clear();
        radios.clear();
        sourceButtons.clear();
        fitButtons.clear();
        areas.clear();
        buttonTab.clear();

        int x = guiLeft, y = guiTop;
        for (int i = 0; i < TAB_COUNT; i++) {
            GuiWidgets.Tab t = new GuiWidgets.Tab(ID_TAB + i, x - GuiWidgets.Tab.WIDTH + GuiWidgets.Tab.OVERLAP,
                y + 4 + i * (GuiWidgets.Tab.HEIGHT + 1), i);
            tabs.add(t);
            add(t, -1);
        }
        // Справа сверху, вне окна — копировать и вставить все настройки как JSON (PictureConfig).
        btnCopy = add(new GuiWidgets.Small(ID_COPY, x + xSize + 2, y + 4, 20, 20, "", GuiWidgets.ICON_COPY_U), -1);
        btnPaste = add(new GuiWidgets.Small(ID_PASTE, x + xSize + 2, y + 26, 20, 20, "", GuiWidgets.ICON_PASTE_U), -1);
        // Ниже — замок привата. Чужой замок не нажимается, только показывает владельца.
        btnLock = add(new GuiWidgets.Lock(ID_LOCK, x + xSize + 2, y + 126), -1);
        btnLock.enabled = !readOnly;

        // Источник
        for (int i = 0; i < PictureFrame.SOURCE_COUNT; i++) {
            sourceButtons.add(add(new GuiWidgets.Toggle(ID_SOURCE + i, x + 8 + i * 22, y + 17, i * 16,
                GuiWidgets.SOURCE_ICON_V), TAB_SOURCE));
        }
        fPath = area(TAB_SOURCE, x + 9, y + 53, 158, 44, tile.localPath);
        fUrl = area(TAB_SOURCE, x + 9, y + 53, 158, 44, tile.yandexUrl);
        fFolder = area(TAB_SOURCE, x + 9, y + 53, 158, 44, tile.folderPath);
        fImage = area(TAB_SOURCE, x + 9, y + 53, 158, 44, tile.imageUrl);
        btnPrev = small(TAB_SOURCE, ID_PREV, x + 8, y + 100, 14, 14, "<", -1);
        btnNext = small(TAB_SOURCE, ID_NEXT, x + 24, y + 100, 14, 14, ">", -1);
        String open = I18n.format("minepapers.gui.openFolder");
        btnFolder = small(TAB_SOURCE, ID_FOLDER, x + 40, y + 100, fontRendererObj.getStringWidth(open) + 12, 14, open, -1);
        // Интервал и состояние — внизу вкладки, над инвентарём
        fInterval = field(TAB_SOURCE, x + 129, y + 117, 38, 6, String.valueOf(tile.interval));
        btnReload = small(TAB_SOURCE, ID_RELOAD, x + 154, y + 131, 14, 14, "", GuiWidgets.ICON_RELOAD_U);

        // Размер и позиция
        fWidth = field(TAB_SIZE, x + 9, y + 19, 40, 10, Numbers.fmt(tile.width));
        fHeight = field(TAB_SIZE, x + 63, y + 19, 40, 10, Numbers.fmt(tile.height));
        cbSizePx = check(TAB_SIZE, ID_SIZE_PX, x + 112, y + 20, sizePx);
        fOffX = field(TAB_SIZE, x + 9, y + 49, 40, 10, Numbers.fmt(tile.offsetX));
        fOffY = field(TAB_SIZE, x + 63, y + 49, 40, 10, Numbers.fmt(tile.offsetY));
        cbPosPx = check(TAB_SIZE, ID_POS_PX, x + 112, y + 50, posPx);
        fPad = field(TAB_SIZE, x + 9, y + 79, 40, 10, Numbers.fmt(tile.padding));
        cbPadPx = check(TAB_SIZE, ID_PAD_PX, x + 112, y + 80, padPx);
        for (int i = 0; i < PictureFrame.FIT_COUNT; i++) {
            // Внизу вкладки, над инвентарём
            fitButtons.add(add(new GuiWidgets.Toggle(ID_FIT + i, x + 8 + i * 22, y + 128, i * 16, GuiWidgets.FIT_ICON_V),
                TAB_SIZE));
        }

        // Вид и стиль
        for (int i = 0; i < PictureFrame.MODE_COUNT; i++) {
            radio(TAB_STYLE, ID_MODE + i, x + 8, y + 29 + i * 11, "minepapers.gui.mode." + i);
        }
        for (int i = 0; i < PictureFrame.STYLE_COUNT; i++) {
            radio(TAB_STYLE, ID_STYLE + i, x + 8, y + 86 + i * 11, "minepapers.gui.style." + i);
        }
        fPpb = field(TAB_STYLE, x + 129, y + 133, 38, 3, String.valueOf(tile.pixelsPerBlock));

        // Описание
        fTitle = field(TAB_INFO, x + 9, y + 18, 158, PictureFrame.MAX_TITLE, tile.title);
        fDesc = area(new GuiTextArea(fontRendererObj, x + 9, y + 48, 158, 92).multiline(), TAB_INFO, x + 9, y + 48, 158, 92,
            tile.description);

        if (old != null && old.size() == fields.size()) {
            for (int i = 0; i < fields.size(); i++) fields.get(i).setText(old.get(i));
        }
        updateWidgets();
    }

    @SuppressWarnings("unchecked")
    private <T extends GuiButton> T add(T button, int onTab) {
        buttonList.add(button);
        if (onTab >= 0) buttonTab.put(button, onTab);
        return button;
    }

    private void radio(int onTab, int id, int x, int y, String key) {
        radios.add(add(new GuiWidgets.Radio(id, x, y, 160, I18n.format(key)), onTab));
    }

    private GuiWidgets.Small small(int onTab, int id, int x, int y, int w, int h, String text, int iconU) {
        return add(new GuiWidgets.Small(id, x, y, w, h, text, iconU), onTab);
    }

    private GuiWidgets.Check check(int onTab, int id, int x, int y, boolean checked) {
        return add(new GuiWidgets.Check(id, x, y, I18n.format("minepapers.gui.pixels"), checked), onTab);
    }

    private GuiTextField field(int onTab, int x, int y, int w, int maxLength, String text) {
        return register(new GuiTextField(fontRendererObj, x, y, w, 12), onTab, x, y, w, 12, maxLength, text);
    }

    /** Многострочное поле для пути или ссылки. */
    private GuiTextField area(int onTab, int x, int y, int w, int h, String text) {
        return area(new GuiTextArea(fontRendererObj, x, y, w, h), onTab, x, y, w, h, text);
    }

    private GuiTextField area(GuiTextArea a, int onTab, int x, int y, int w, int h, String text) {
        areas.add(a);
        return register(a, onTab, x, y, w, h, 512, text);
    }

    private GuiTextField register(GuiTextField f, int onTab, int x, int y, int w, int h, int maxLength, String text) {
        f.setMaxStringLength(maxLength);
        f.setText(text);
        fields.add(f);
        fieldRect.put(f, new int[] { onTab, x, y, w, h });
        return f;
    }

    private void updateWidgets() {
        boolean local = source == PictureFrame.SOURCE_LOCAL;
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            Integer onTab = buttonTab.get(b);
            if (onTab != null) b.visible = !readOnly && onTab == tab;
        }
        for (GuiWidgets.Tab t : tabs) t.visible = !readOnly;
        btnCopy.visible = btnPaste.visible = !readOnly;
        btnLock.owner = !locked ? null : tile.owner != null ? tile.owner : me;
        btnLock.ownerName = !locked ? "" : tile.owner != null ? tile.ownerName : myName;
        btnLock.mine = !readOnly;
        btnPrev.visible &= local;
        btnNext.visible &= local;
        btnFolder.visible &= local;
        for (GuiWidgets.Tab t : tabs) t.selected = t.id - ID_TAB == tab;
        for (GuiWidgets.Radio r : radios) r.checked = isChosen(r.id);
        for (GuiWidgets.Toggle t : sourceButtons) t.checked = t.id - ID_SOURCE == source;
        for (GuiWidgets.Toggle t : fitButtons) t.checked = t.id - ID_FIT == fitMode;
        // Слот рамки есть всегда (его содержимое синхронизирует сервер), но виден и кликается только на своей вкладке.
        for (Object o : inventorySlots.inventorySlots) {
            Slot s = (Slot) o;
            if (container().isMaterialSlot(s.slotNumber)) {
                s.xDisplayPosition = !readOnly && tab == TAB_FRAME ? ContainerPictureFrame.MATERIAL_X : -10000;
            }
        }
        cbSizePx.checked = sizePx;
        cbPosPx.checked = posPx;
        cbPadPx.checked = padPx;
        for (GuiTextField f : fields) {
            if (!isActive(f)) f.setFocused(false);
        }
    }

    private boolean isChosen(int radioId) {
        if (radioId >= ID_STYLE) return radioId - ID_STYLE == style;
        return radioId - ID_MODE == mode;
    }

    private boolean isActive(GuiTextField f) {
        if (readOnly || fieldRect.get(f)[0] != tab) return false;
        if (f == fPath) return source == PictureFrame.SOURCE_LOCAL;
        if (f == fUrl) return source == PictureFrame.SOURCE_YANDEX;
        if (f == fFolder) return source == PictureFrame.SOURCE_FOLDER;
        if (f == fImage) return source == PictureFrame.SOURCE_URL;
        return true;
    }

    private String currentKey() {
        return PictureFrame.makeSourceKey(source, fPath.getText(), fUrl.getText(), fFolder.getText(), fImage.getText());
    }

    /** Что на самом деле показывает рамка: если источник не меняли и картинки уже на сервере — их. */
    private String shownKey() {
        String key = currentKey();
        return key != null && key.equals(tile.getOriginKey()) && tile.hasSharedFiles() ? tile.getSourceKey() : key;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int id = button.id;
        if (id < ID_SOURCE) {
            tab = lastTab = id - ID_TAB;
        } else if (id < ID_FIT) {
            source = id - ID_SOURCE;
        } else if (id < ID_MODE) {
            fitMode = id - ID_FIT;
        } else if (id < ID_STYLE) {
            mode = id - ID_MODE;
        } else if (id < ID_PREV) {
            style = id - ID_STYLE;
        } else {
            switch (id) {
                case ID_PREV:
                case ID_NEXT:
                    cycleLocal(id == ID_NEXT ? 1 : -1);
                    break;
                case ID_FOLDER:
                    openFolder();
                    break;
                case ID_COPY:
                    copyConfig();
                    break;
                case ID_PASTE:
                    pasteConfig();
                    break;
                case ID_RELOAD:
                    reload();
                    break;
                case ID_SIZE_PX:
                    sizePx = !sizePx;
                    convert(fWidth, sizePx);
                    convert(fHeight, sizePx);
                    break;
                case ID_POS_PX:
                    posPx = !posPx;
                    convert(fOffX, posPx);
                    convert(fOffY, posPx);
                    break;
                case ID_LOCK:
                    if (!readOnly) locked = !locked;
                    break;
                case ID_PAD_PX:
                    padPx = !padPx;
                    convert(fPad, padPx);
                    break;
            }
        }
        updateWidgets();
    }

    private void reload() {
        String key = currentKey();
        if (key == null) return;
        PictureManager.reload(key);
        PictureManager.get(key);
        // Перечитать папку и выложить на сервер то, что в ней появилось.
        if (key.equals(tile.getOriginKey())) {
            if (tile.hasSharedFiles()) PictureManager.reload(tile.getSourceKey());
            ImageSync.offer(holder.target, tile);
        }
    }

    /** Переключение блоки <-> пиксели: пересчитываем значение, чтобы картинка не "прыгала". */
    private void convert(GuiTextField f, boolean toPixels) {
        Float v = Numbers.parse(f.getText());
        if (v != null) {
            f.setText(Numbers.fmt(toPixels ? v * 16f : v / 16f));
        }
    }

    private void cycleLocal(int dir) {
        List<String> entries = PictureManager.listLocalEntries();
        if (entries.isEmpty()) return;
        int index = entries.indexOf(fPath.getText().trim());
        if (index < 0) {
            index = dir > 0 ? 0 : entries.size() - 1;
        } else {
            index = (index + dir + entries.size()) % entries.size();
        }
        fPath.setText(entries.get(index));
    }

    private void openFolder() {
        try {
            Desktop.getDesktop().open(MinePapers.picturesDir);
        } catch (Throwable t) {
            Sys.openURL("file://" + MinePapers.picturesDir.getAbsolutePath());
        }
    }

    // ---------- Копировать и вставить ----------

    private GuiTextField pathField(int src) {
        switch (src) {
            case PictureFrame.SOURCE_YANDEX: return fUrl;
            case PictureFrame.SOURCE_FOLDER: return fFolder;
            case PictureFrame.SOURCE_URL: return fImage;
            default: return fPath;
        }
    }

    /** Все настройки окна (как они введены сейчас) и материалы картины — в буфер обмена JSON-ом. */
    private void copyConfig() {
        PictureConfig c = new PictureConfig();
        c.minecraft = "1.7.10";
        c.title = fTitle.getText().trim();
        c.description = fDesc.getText().trim();
        c.source = source;
        c.path = pathField(source).getText().trim();
        Float interval = Numbers.parse(fInterval.getText());
        if (interval != null) c.interval = Math.round(interval);
        c.width = Numbers.parse(fWidth.getText());
        c.height = Numbers.parse(fHeight.getText());
        c.sizePixels = sizePx;
        c.offsetRight = Numbers.parse(fOffX.getText());
        c.offsetUp = Numbers.parse(fOffY.getText());
        c.offsetPixels = posPx;
        c.padding = Numbers.parse(fPad.getText());
        c.paddingPixels = padPx;
        c.fit = fitMode;
        c.look = mode;
        c.style = style;
        Float ppb = Numbers.parse(fPpb.getText());
        if (ppb != null) c.pixelsPerBlock = Math.round(ppb);
        PictureFrame.Material[] materials = tile.materials();
        for (int i = 0; i < materials.length && i < PictureConfig.MATERIALS.length; i++) {
            c.hasMaterial[i] = true;
            c.materials[i] = materials[i].stack != null ? new PictureConfig.Item(materials[i].item, materials[i].meta) : null;
        }
        setClipboardString(c.toJson());
        chat(new ChatComponentTranslation("minepapers.chat.copied"), EnumChatFormatting.GRAY);
    }

    /** Настройки из буфера обмена — в окно (применятся при закрытии, как и введённые руками); материалы сервер берёт из инвентаря (PacketPasteMaterials). */
    private void pasteConfig() {
        PictureConfig c;
        try {
            c = PictureConfig.fromJson(getClipboardString());
        } catch (IllegalArgumentException e) {
            chat(new ChatComponentTranslation("minepapers.chat.badConfig"), EnumChatFormatting.RED);
            return;
        }
        if (c.title != null) fTitle.setText(c.title);
        if (c.description != null) fDesc.setText(c.description);
        if (c.source != null) source = c.source;
        if (c.path != null) pathField(source).setText(c.path);
        if (c.interval != null) fInterval.setText(String.valueOf(c.interval));
        if (c.sizePixels != null) sizePx = c.sizePixels;
        if (c.width != null) fWidth.setText(Numbers.fmt(c.width));
        if (c.height != null) fHeight.setText(Numbers.fmt(c.height));
        if (c.offsetPixels != null) posPx = c.offsetPixels;
        if (c.offsetRight != null) fOffX.setText(Numbers.fmt(c.offsetRight));
        if (c.offsetUp != null) fOffY.setText(Numbers.fmt(c.offsetUp));
        if (c.paddingPixels != null) padPx = c.paddingPixels;
        if (c.padding != null) fPad.setText(Numbers.fmt(c.padding));
        if (c.fit != null) fitMode = c.fit;
        if (c.look != null) mode = c.look;
        if (c.style != null) style = c.style;
        if (c.pixelsPerBlock != null) fPpb.setText(String.valueOf(c.pixelsPerBlock));
        updateWidgets();
        // Конфиг из новых версий: предметы там названы иначе (grass_block вместо grass) — переводим.
        for (int i = 0; i < c.materials.length; i++) {
            c.materials[i] = c.translate(c.materials[i], false, id -> Item.itemRegistry.getObject(id) != null);
        }
        for (boolean has : c.hasMaterial) {
            if (has && holder.materialsEditable()) {
                MinePapers.network.sendToServer(new PacketPasteMaterials(holder.target, c));
                break;
            }
        }
    }

    private void chat(IChatComponent text, EnumChatFormatting color) {
        text.getChatStyle().setColor(color);
        mc.thePlayer.addChatMessage(text);
    }

    // ---------- Сохранение ----------

    private void save() {
        NBTTagCompound before = new NBTTagCompound();
        tile.writeSettings(before);

        tile.title = fTitle.getText().trim();
        tile.description = fDesc.getText().trim();
        // Владельца решает сервер (PacketSaveFrame); здесь — только просьба закрыть или открыть замок.
        if (locked && tile.owner == null) {
            tile.owner = me;
            tile.ownerName = myName;
        } else if (!locked) {
            tile.owner = null;
            tile.ownerName = "";
        }
        tile.source = source;
        tile.localPath = fPath.getText().trim();
        tile.yandexUrl = fUrl.getText().trim();
        tile.folderPath = fFolder.getText().trim();
        tile.imageUrl = fImage.getText().trim();
        Float interval = Numbers.parse(fInterval.getText());
        if (interval != null) tile.interval = Math.round(interval);
        tile.sizeInPixels = sizePx;
        float[] size = resolveSize(Numbers.parse(fWidth.getText()), Numbers.parse(fHeight.getText()));
        tile.width = size[0];
        tile.height = size[1];
        tile.offsetInPixels = posPx;
        tile.offsetX = Numbers.orDefault(Numbers.parse(fOffX.getText()), tile.offsetX);
        tile.offsetY = Numbers.orDefault(Numbers.parse(fOffY.getText()), tile.offsetY);
        tile.paddingInPixels = padPx;
        tile.padding = Numbers.orDefault(Numbers.parse(fPad.getText()), tile.padding);
        tile.fitMode = fitMode;
        tile.style = style;
        tile.mode = mode;
        Float ppb = Numbers.parse(fPpb.getText());
        if (ppb != null) tile.pixelsPerBlock = Math.round(ppb);

        // Прогоняем через те же ограничения, что и сервер.
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeSettings(tag);
        tile.readSettings(tag);
        NBTTagCompound after = new NBTTagCompound();
        tile.writeSettings(after);
        if (after.equals(before)) return; // окно открыли и закрыли — серверу отправлять нечего

        if (holder.picture != null) holder.picture.refresh(); // хитбокс сразу по новому размеру, не дожидаясь сервера
        MinePapers.network.sendToServer(new PacketSaveFrame(holder));
        // Файлы с этого компьютера другие игроки увидят, только когда они окажутся на сервере.
        ImageSync.offer(holder.target, tile);
    }

    /** Хитбокс должен быть известен серверу, поэтому "0 = авто" превращаем в конкретный размер по пропорциям текущей картинки (если она ещё не загружена — квадрат). Округляем до пикселя. */
    private float[] resolveSize(Float w, Float h) {
        float unit = sizePx ? 16f : 1f;
        boolean wAuto = w == null || w <= 0, hAuto = h == null || h <= 0;
        if (wAuto || hAuto) {
            double aspect = currentAspect();
            if (wAuto && hAuto) {
                w = unit;
                h = (float) (unit * aspect);
            } else if (hAuto) {
                h = (float) (w * aspect);
            } else {
                w = (float) (h / aspect);
            }
        }
        return new float[] { Numbers.snap(w, sizePx), Numbers.snap(h, sizePx) };
    }

    /** Высота / ширина картинки, которая сейчас показывается в рамке (1, если неизвестно). */
    private double currentAspect() {
        PictureManager.Source s = PictureManager.peek(shownKey());
        if (s != null) {
            PictureManager.Picture p = s.slide(mc.theWorld.getTotalWorldTime(), tile.interval, 0, tile.getSlideSeed());
            if (p != null && p.request() && p.getOrigWidth() > 0) {
                return (double) p.getOrigHeight() / p.getOrigWidth();
            }
        }
        return 1.0;
    }

    // ---------- Ввод ----------

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.thePlayer.closeScreen(); // сохранение — в onGuiClosed
            return;
        }
        if (key == Keyboard.KEY_TAB) {
            focusNext();
            return;
        }
        for (GuiTextField f : fields) {
            if (isActive(f) && f.isFocused()) {
                f.textboxKeyTyped(c, key);
                return;
            }
        }
        super.keyTyped(c, key); // E закрывает окно, цифры — обмен с хотбаром, как в любом инвентаре
    }

    private void focusNext() {
        List<GuiTextField> active = new ArrayList<GuiTextField>();
        for (GuiTextField f : fields) {
            if (isActive(f)) active.add(f);
        }
        if (active.isEmpty()) return;
        int focused = -1;
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).isFocused()) focused = i;
            active.get(i).setFocused(false);
        }
        active.get((focused + 1) % active.size()).setFocused(true);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        String link = button == 0 ? linkAt(x, y) : null;
        if (link != null) {
            openLink(link);
            return;
        }
        dragField = null;
        for (GuiTextField f : fields) {
            if (!isActive(f)) continue;
            f.mouseClicked(x, y, button);
            if (button == 0 && f.isFocused() && !(f instanceof GuiTextArea)) dragField = f;
        }
        // Кнопки обрабатываем сами: вкладки лежат за краем окна, а клик туда с предметом в руке GuiContainer счёл бы выбросом.
        if (button == 0) {
            for (Object o : buttonList) {
                GuiButton b = (GuiButton) o;
                if (b.mousePressed(mc, x, y)) {
                    b.func_146113_a(mc.getSoundHandler());
                    actionPerformed(b);
                    return;
                }
            }
        }
        super.mouseClicked(x, y, button);
    }

    /** Перетаскивание мыши после клика в многострочном поле — выделение текста, а не раскладка предметов по слотам. */
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long sinceClick) {
        for (GuiTextArea a : areas) {
            if (isActive(a) && a.mouseDragged(mouseX, mouseY)) return;
        }
        if (dragField != null && isActive(dragField) && dragField.isFocused()) {
            selectTo(dragField, mouseX);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, button, sinceClick);
    }

    /** Выделение в однострочном поле перетаскиванием: курсор остаётся, где нажали, конец выделения — под мышью. Позицию под мышью считает само поле (клик), затем курсор возвращаем. */
    private void selectTo(GuiTextField f, int mouseX) {
        int[] r = fieldRect.get(f);
        int anchor = f.getCursorPosition();
        f.mouseClicked(Math.max(r[1], Math.min(r[1] + r[3] - 1, mouseX)), r[2], 0);
        int pos = f.getCursorPosition();
        f.setCursorPosition(anchor);
        f.setSelectionPos(pos);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (button >= 0) {
            for (GuiTextArea a : areas) a.mouseReleased();
            dragField = null;
        }
        super.mouseMovedOrUp(mouseX, mouseY, button);
    }

    /** Колёсико над многострочным полем прокручивает его. */
    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        for (GuiTextArea a : areas) {
            if (isActive(a) && a.contains(mouseX, mouseY)) a.scrollBy(wheel > 0 ? -1 : 1);
        }
    }

    /** Shift+клик кладёт предмет в слот рамки — только когда этот слот виден. */
    @Override
    protected void handleMouseClick(Slot slot, int slotId, int button, int clickType) {
        if (clickType == 1 && tab != TAB_FRAME && slot != null && !container().isMaterialSlot(slot.slotNumber)) return;
        super.handleMouseClick(slot, slotId, button, clickType);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (GuiTextField f : fields) {
            if (isActive(f)) f.updateCursorCounter();
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        // Настройки применяются при любом закрытии окна: Esc, E, Enter или сервер закрыл его сам.
        if (!readOnly && mc.thePlayer != null && mc.getNetHandler() != null && holder.exists()) save();
    }

    // ---------- Отрисовка ----------

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        int x = guiLeft, y = guiTop;
        // Как в творческом режиме: невыбранные вкладки уходят под окно, выбранная лежит поверх и сливается с ним.
        for (GuiWidgets.Tab t : tabs) {
            if (!t.selected) t.drawBody();
        }
        GuiWidgets.panel(x, y, xSize, ySize); // слоты инвентаря — часть ванильной текстуры
        for (GuiWidgets.Tab t : tabs) {
            if (t.selected) t.drawBody();
        }
        for (GuiWidgets.Tab t : tabs) t.drawIcon();
        if (!readOnly && tab == TAB_FRAME) {
            for (int i = 0; i < tile.materials().length; i++) {
                GuiWidgets.slot(x + ContainerPictureFrame.MATERIAL_X - 1,
                    y + ContainerPictureFrame.MATERIAL_Y - 1 + i * ContainerPictureFrame.MATERIAL_STEP);
            }
        }
        for (GuiTextField f : fields) {
            if (isActive(f)) f.drawTextBox();
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int inventoryY = ySize - GuiWidgets.INVENTORY_PART_HEIGHT + 2;
        text(I18n.format("container.inventory"), 8, inventoryY, GuiWidgets.TEXT);
        if (readOnly) {
            // Чужая заприваченная картина: заголовок и под ним описание, только для чтения.
            String title = tile.title.isEmpty() ? I18n.format("minepapers.gui.untitled") : tile.title;
            int y = lines(title, 8, 6, 160, 100, GuiWidgets.TEXT) + 4;
            linkRects.clear();
            linkUrls.clear();
            for (String paragraph : tile.description.split("\n")) {
                if (y > inventoryY - 12) break;
                y = linkedLines(paragraph, 8, y, 160, inventoryY - 12);
            }
            return;
        }
        if (tab != TAB_SIZE && tab != TAB_INFO) text(I18n.format("minepapers.gui.tab." + tab), 8, 6, GuiWidgets.TEXT);
        switch (tab) {
            case TAB_SOURCE: {
                text(I18n.format("minepapers.gui.path." + source), 8, 42, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.interval"), 8, 119, GuiWidgets.TEXT);
                String status = statusText();
                lines(status, 8, 133, 142, 2, statusError ? GuiWidgets.ERROR : GuiWidgets.TEXT);
                break;
            }
            case TAB_SIZE:
                text(I18n.format("minepapers.gui.size"), 8, 6, GuiWidgets.TEXT);
                text("x", 53, 21, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.offset"), 8, 36, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.padding"), 8, 66, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.fit"), 8, 117, GuiWidgets.TEXT);
                break;
            case TAB_STYLE:
                text(I18n.format("minepapers.gui.mode"), 8, 18, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.style"), 8, 75, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.ppb"), 8, 135, GuiWidgets.TEXT);
                break;
            case TAB_INFO:
                text(I18n.format("minepapers.gui.titleLabel"), 8, 6, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.description"), 8, 36, GuiWidgets.TEXT);
                break;
            case TAB_FRAME: {
                // Список материалов: слот, справа — что это и какой блок в нём лежит.
                PictureFrame.Material[] materials = tile.materials();
                for (int i = 0; i < materials.length; i++) {
                    String key = "minepapers.gui." + materials[i].key.toLowerCase(java.util.Locale.ROOT);
                    String name = materials[i].stack != null ? materials[i].stack.getDisplayName() : I18n.format(key + ".default");
                    if (materials[i].light && materials[i].stack != null) {
                        name += ", " + (PictureFrame.isGlowDust(materials[i].stack) ? I18n.format("minepapers.gui.glow.self")
                            : I18n.format("minepapers.gui.glow.level", PictureFrame.lightOf(materials[i].stack)));
                    }
                    int ty = ContainerPictureFrame.MATERIAL_Y + 1 + i * ContainerPictureFrame.MATERIAL_STEP;
                    text(I18n.format(key), 30, ty - 1, GuiWidgets.TEXT);
                    text(fontRendererObj.trimStringToWidth(name, 138), 30, ty + 8, GuiWidgets.HINT);
                }
                if (!holder.materialsEditable()) {
                    lines(I18n.format("minepapers.gui.materials.stack"), 8,
                        ContainerPictureFrame.MATERIAL_Y + materials.length * ContainerPictureFrame.MATERIAL_STEP + 2, 160, 4,
                        GuiWidgets.HINT);
                }
                break;
            }
        }
    }

    private ContainerPictureFrame container() {
        return (ContainerPictureFrame) inventorySlots;
    }

    private void text(String s, int x, int y, int color) {
        fontRendererObj.drawString(s, x, y, color);
    }

    /** Текст с переносом по ширине, не больше maxLines строк. Возвращает y под последней строкой. */
    @SuppressWarnings("unchecked")
    private int lines(String s, int x, int y, int width, int maxLines, int color) {
        List<String> list = fontRendererObj.listFormattedStringToWidth(s, width);
        for (int i = 0; i < list.size() && i < maxLines; i++) {
            text(list.get(i), x, y, color);
            y += 9;
        }
        return y;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (mc.thePlayer.inventory.getItemStack() == null) drawTooltips(mouseX, mouseY);
        String link = linkAt(mouseX, mouseY);
        if (link != null) drawHoveringText(Arrays.asList(link), mouseX, mouseY, fontRendererObj);
    }

    // ---------- Ссылки в описании ----------

    /** Ссылки описания в окне чужой картины: прямоугольники {x0, y0, x1, y1} в координатах окна и их адреса. */
    private final List<int[]> linkRects = new ArrayList<int[]>();
    private final List<String> linkUrls = new ArrayList<String>();
    /** Ссылка, которую спросили открыть (ванильный вопрос "Открыть ссылку?"). */
    private String pendingLink;

    /** Абзац с переносом по ширине; ссылки в нём — синие и подчёркнутые, их место запоминается для кликов. Возвращает y под последней строкой. */
    @SuppressWarnings("unchecked")
    private int linkedLines(String paragraph, int x, int y, int width, int maxY) {
        List<int[]> spans = Links.find(paragraph);
        int from = 0;
        for (String line : (List<String>) fontRendererObj.listFormattedStringToWidth(paragraph, width)) {
            if (y > maxY) break;
            int start = paragraph.indexOf(line, from);
            if (start < 0) start = from;
            int end = Math.min(paragraph.length(), start + line.length());
            from = end;
            text(line, x, y, GuiWidgets.HINT);
            for (int[] s : spans) {
                int a = Math.max(start, s[0]), b = Math.min(end, s[1]);
                if (a >= b) continue;
                int x0 = x + fontRendererObj.getStringWidth(paragraph.substring(start, a));
                int x1 = x + fontRendererObj.getStringWidth(paragraph.substring(start, b));
                text(paragraph.substring(a, b), x0, y, GuiWidgets.HOVER);
                drawRect(x0, y + 8, x1, y + 9, 0xFF000000 | GuiWidgets.HOVER);
                linkRects.add(new int[] { x0, y, x1, y + 9 });
                linkUrls.add(paragraph.substring(s[0], s[1]));
            }
            y += 9;
        }
        return y;
    }

    /** Ссылка под мышью (только в окне чужой картины) или null. */
    private String linkAt(int mouseX, int mouseY) {
        if (!readOnly) return null;
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        for (int i = 0; i < linkRects.size(); i++) {
            int[] r = linkRects.get(i);
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) return linkUrls.get(i);
        }
        return null;
    }

    /** Как ссылка в чате: с вопросом, если он включён в настройках чата; ссылки в чате выключены — ничего. */
    private void openLink(String url) {
        if (!mc.gameSettings.chatLinks) return;
        if (mc.gameSettings.chatLinksPrompt) {
            pendingLink = url;
            mc.displayGuiScreen(new GuiConfirmOpenLink(this, url, 0, false));
        } else {
            browse(url);
        }
    }

    @Override
    public void confirmClicked(boolean yes, int id) {
        if (yes && pendingLink != null) browse(pendingLink);
        pendingLink = null;
        mc.displayGuiScreen(this);
    }

    private static void browse(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Throwable t) {
            Sys.openURL(url);
        }
    }

    private void drawTooltips(int mouseX, int mouseY) {
        for (GuiWidgets.Tab t : tabs) {
            if (t.hovered()) {
                tooltip("minepapers.gui.tab." + (t.id - ID_TAB), mouseX, mouseY);
                return;
            }
        }
        for (GuiWidgets.Radio r : radios) {
            if (!r.hovered()) continue;
            if (r.id >= ID_STYLE) {
                tooltip("minepapers.gui.style.tip", mouseX, mouseY);
            } else if (r.id >= ID_MODE) {
                tooltip("minepapers.gui.mode." + (r.id - ID_MODE) + ".tip", mouseX, mouseY);
            }
            return;
        }
        for (GuiWidgets.Toggle t : sourceButtons) {
            if (t.hovered()) {
                int s = t.id - ID_SOURCE;
                titledTooltip("minepapers.gui.source." + s, "minepapers.gui.path." + s + ".tip", mouseX, mouseY);
                return;
            }
        }
        for (GuiWidgets.Toggle t : fitButtons) {
            if (t.hovered()) {
                int fit = t.id - ID_FIT;
                titledTooltip("minepapers.gui.fit." + fit, "minepapers.gui.fit." + fit + ".tip", mouseX, mouseY);
                return;
            }
        }
        if (btnLock.hovered()) {
            if (btnLock.owner == null) {
                titledTooltip("minepapers.gui.lock", "minepapers.gui.lock.tip", mouseX, mouseY);
            } else {
                // Ник владельца, а ему самому — как снять приват.
                List<String> lines = new ArrayList<String>();
                lines.add(I18n.format("minepapers.gui.owner", btnLock.ownerName));
                if (!readOnly) lines.add("§7" + I18n.format("minepapers.gui.unlock.tip"));
                drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
            }
        } else if (btnCopy.hovered()) {
            titledTooltip("minepapers.gui.copy", "minepapers.gui.copy.tip", mouseX, mouseY);
        } else if (btnPaste.hovered()) {
            titledTooltip("minepapers.gui.paste", "minepapers.gui.paste.tip", mouseX, mouseY);
        } else if (cbSizePx.hovered() || cbPosPx.hovered() || cbPadPx.hovered()) {
            tooltip("minepapers.gui.pixels.tip", mouseX, mouseY);
        } else if (tab == TAB_SIZE && over(fWidth, mouseX, mouseY)) {
            tooltip("minepapers.gui.width", mouseX, mouseY);
        } else if (tab == TAB_SIZE && over(fHeight, mouseX, mouseY)) {
            tooltip("minepapers.gui.height", mouseX, mouseY);
        } else if (tab == TAB_SIZE && over(fOffX, mouseX, mouseY)) {
            tooltip("minepapers.gui.shift.x", mouseX, mouseY);
        } else if (tab == TAB_SIZE && over(fOffY, mouseX, mouseY)) {
            tooltip("minepapers.gui.shift.y", mouseX, mouseY);
        } else if (tab == TAB_SIZE && over(fPad, mouseX, mouseY)) {
            tooltip("minepapers.gui.padding.tip", mouseX, mouseY);
        } else if (btnReload.hovered()) {
            tooltip("minepapers.gui.reload", mouseX, mouseY);
        } else if (tab == TAB_STYLE && over(fPpb, mouseX, mouseY)) {
            tooltip("minepapers.gui.ppb.tip", mouseX, mouseY);
        }
    }

    private boolean over(GuiTextField f, int mouseX, int mouseY) {
        int[] r = fieldRect.get(f);
        return mouseX >= r[1] - 1 && mouseY >= r[2] - 1 && mouseX < r[1] + r[3] + 1 && mouseY < r[2] + r[4] + 1;
    }

    /** Название — заголовком, пояснение — серым под ним, как описание предмета. */
    private void titledTooltip(String titleKey, String tipKey, int x, int y) {
        List<String> lines = new ArrayList<String>();
        lines.add(I18n.format(titleKey));
        for (String s : I18n.format(tipKey).split("\\\\n")) lines.add("§7" + s);
        drawHoveringText(lines, x, y, fontRendererObj);
    }

    private void tooltip(String key, int x, int y, Object... args) {
        List<String> lines = new ArrayList<String>(Arrays.asList(I18n.format(key, args).split("\\\\n")));
        drawHoveringText(lines, x, y, fontRendererObj);
    }


    private String statusText() {
        statusError = false;
        String key = currentKey();
        if (key == null) return I18n.format("minepapers.gui.status.none");
        int[] job = ImageSync.jobStatus(key);
        if (job != null) {
            String stage = job[0] == ImageSync.STAGE_PREPARING ? "preparing" : "uploading";
            return I18n.format("minepapers.gui.status." + stage, job[1], job[2]);
        }
        key = shownKey();
        PictureManager.Source s = PictureManager.peek(key);
        if (s == null) return I18n.format("minepapers.gui.status.unknown");
        if (s.isLoading()) return I18n.format("minepapers.gui.status.loading");
        if (s.getErrorKey() != null || s.getErrorDetail() != null) {
            statusError = true;
            String detail = s.getErrorKey() != null ? I18n.format(s.getErrorKey()) : s.getErrorDetail();
            return I18n.format("minepapers.gui.status.error", detail);
        }
        return I18n.format(key.startsWith("shared:") ? "minepapers.gui.status.shared" : "minepapers.gui.status.count",
            s.count());
    }
}
