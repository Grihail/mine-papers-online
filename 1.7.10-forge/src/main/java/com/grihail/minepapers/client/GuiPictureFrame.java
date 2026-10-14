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
import net.minecraft.client.gui.GuiYesNo;
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
    private static final int ID_TAB = 0, ID_SOURCE = 10, ID_FIT = 20, ID_FRAME = 30, ID_CANVAS = 35, ID_STYLE = 40,
        ID_PREV = 50, ID_NEXT = 51, ID_FOLDER = 52, ID_RELOAD = 53, ID_SIZE_PX = 54, ID_POS_PX = 55,
        ID_COPY = 56, ID_PASTE = 57, ID_PAD_PX = 58, ID_LOCK = 59, ID_SIGN = 60, ID_SHARP = 61, ID_MAP_PALETTE = 62, ID_NSFW = 63;

    /** Ширина — как у сундука; низ окна — часть его текстуры с инвентарём, слоты в ней на 14 пикселей ниже её верха. */
    private static final int WIDTH = 176,
        HEIGHT = ContainerPictureFrame.INVENTORY_Y - GuiWidgets.INVENTORY_PART_SLOTS_Y + GuiWidgets.INVENTORY_PART_HEIGHT;

    /** Левый край кнопок вида рамки и холста (глаз и две кнопки в ряд, до правого края панели). */
    private static final int LOOK_X = WIDTH - 8 - GuiWidgets.Eye.WIDTH - 2 - 42;

    /** Последняя открытая вкладка — с неё окно откроется в следующий раз. */
    private static int lastTab = TAB_SOURCE;

    /** Картина-сущность или картина-предмет в руке. */
    private final FrameHolder holder;
    private final PictureFrame tile;

    private int tab = lastTab;
    private int source, fitMode, frameShape, canvasKind;
    /** Резкие пиксели и палитра карт (галочки), число цветов, сила дизеринга и пикселей на блок (ползунки) на вкладке "Стиль". */
    private boolean sharp, mapPalette;
    private int colors, dither, ppb;
    private boolean sizePx, posPx, padPx;
    /** Замок закрыт (картина заприватится при закрытии окна). */
    private boolean locked;
    /** Подписать картину при закрытии окна (подпись ставит сервер, и только на картину без привата). */
    private boolean sign;
    /** Открыто ванильное окно "Подписать картину?": окно картины к нему вернётся, поэтому не сохраняется и не закрывается. */
    private boolean confirming;
    /** id вопроса о подписи в confirmClicked (0 — ссылка). */
    private static final int CONFIRM_SIGN = 1;
    /** Чужая заприваченная картина: без вкладок, только заголовок и описание. */
    private final boolean readOnly;
    private final UUID me;
    private final String myName;
    private GuiWidgets.Lock btnLock;
    private GuiWidgets.Sign btnSign;
    /** Пометка NSFW (18+); применится при закрытии окна. */
    private boolean nsfw;
    private GuiWidgets.Nsfw btnNsfw;

    private final List<GuiWidgets.Tab> tabs = new ArrayList<GuiWidgets.Tab>();
    private GuiWidgets.Slider slColors, slDither, slPpb;
    /** Ползунки вкладки "Стиль" и их подсказки — по порядку. */
    private GuiWidgets.Slider[] sliders;
    private static final String[] SLIDER_TIPS = { "minepapers.gui.colors.tip", "minepapers.gui.dither.tip", "minepapers.gui.ppb.tip" };
    /** Источник и вписывание — ряды кнопок с картинками; название и пояснение — в подсказке. */
    private final List<GuiWidgets.Toggle> sourceButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> fitButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Рамка и холст — ряды кнопок справа от их слотов на вкладке "Рамка". */
    private final List<GuiWidgets.Toggle> frameButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> canvasButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Многострочные поля (путь и ссылки): им нужны перетаскивание мыши и колёсико. */
    private final List<GuiTextArea> areas = new ArrayList<GuiTextArea>();
    /** Вкладка, на которой лежит кнопка (у самих вкладок записи нет — они видны всегда). */
    private final Map<GuiButton, Integer> buttonTab = new HashMap<GuiButton, Integer>();
    private GuiWidgets.Small btnPrev, btnNext, btnFolder, btnReload, btnCopy, btnPaste;
    private GuiWidgets.Check cbSizePx, cbPosPx, cbPadPx, cbSharp, cbMapPalette;

    private GuiTextField fPath, fUrl, fFolder, fImage, fInterval, fWidth, fHeight, fOffX, fOffY, fPad, fTitle, fDesc;
    /** Ширина багета в пикселях: 0 — багета нет. */
    private GuiTextField fMolding;
    private final List<GuiTextField> fields = new ArrayList<GuiTextField>();
    /** Поле -> {вкладка, x, y, ширина, высота}: видимость и подсказки при наведении. */
    private final Map<GuiTextField, int[]> fieldRect = new HashMap<GuiTextField, int[]>();
    /** Числовые поля: набрать можно только число, стрелки вверх и вниз в поле и колёсико над ним меняют его на 1. */
    private final Map<GuiTextField, Numbers.Field> numbers = new HashMap<GuiTextField, Numbers.Field>();
    /** Однострочное поле, в котором зажали кнопку мыши: перетаскивание выделяет в нём текст. */
    private GuiTextField dragField;

    private boolean statusError;

    public GuiPictureFrame(ContainerPictureFrame container) {
        super(container);
        this.holder = container.holder;
        this.tile = holder.frame;
        this.source = tile.source;
        this.fitMode = tile.fitMode;
        this.sharp = tile.sharpPixels;
        this.mapPalette = tile.mapPalette;
        this.colors = tile.colors;
        this.dither = tile.dither;
        this.ppb = 1 << PictureFrame.ppbStep(tile.pixelsPerBlock);
        this.frameShape = tile.getFrameShape();
        this.canvasKind = tile.getCanvasKind();
        this.sizePx = tile.sizeInPixels;
        this.posPx = tile.offsetInPixels;
        this.padPx = tile.paddingInPixels;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        this.me = player.getUniqueID();
        this.myName = player.getCommandSenderName();
        this.locked = tile.isLocked();
        this.nsfw = tile.nsfw;
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
        numbers.clear();
        tabs.clear();
        sourceButtons.clear();
        fitButtons.clear();
        frameButtons.clear();
        canvasButtons.clear();
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
        // Ниже — пометка NSFW (ставит и снимает любой, кто может менять картину), подпись и замок привата. Чужой замок не нажимается, только показывает владельца.
        btnNsfw = add(new GuiWidgets.Nsfw(ID_NSFW, x + xSize + 2, y + 82), -1);
        btnSign = add(new GuiWidgets.Sign(ID_SIGN, x + xSize + 2, y + 104), -1);
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
        fInterval = field(TAB_SOURCE, x + 129, y + 117, 38, 6, String.valueOf(tile.interval), Numbers.INTERVAL);
        btnReload = small(TAB_SOURCE, ID_RELOAD, x + 154, y + 131, 14, 14, "", GuiWidgets.ICON_RELOAD_U);

        // Размер и позиция
        fWidth = field(TAB_SIZE, x + 9, y + 19, 40, 10, Numbers.fmt(tile.width), Numbers.SIZE);
        fHeight = field(TAB_SIZE, x + 63, y + 19, 40, 10, Numbers.fmt(tile.height), Numbers.SIZE);
        cbSizePx = check(TAB_SIZE, ID_SIZE_PX, x + 112, y + 20, sizePx);
        fOffX = field(TAB_SIZE, x + 9, y + 49, 40, 10, Numbers.fmt(tile.offsetX), Numbers.OFFSET);
        fOffY = field(TAB_SIZE, x + 63, y + 49, 40, 10, Numbers.fmt(tile.offsetY), Numbers.OFFSET);
        cbPosPx = check(TAB_SIZE, ID_POS_PX, x + 112, y + 50, posPx);
        fPad = field(TAB_SIZE, x + 9, y + 79, 40, 10, Numbers.fmt(tile.padding), Numbers.PADDING);
        cbPadPx = check(TAB_SIZE, ID_PAD_PX, x + 112, y + 80, padPx);
        for (int i = 0; i < PictureFrame.FIT_COUNT; i++) {
            // Внизу вкладки, над инвентарём
            fitButtons.add(add(new GuiWidgets.Toggle(ID_FIT + i, x + 8 + i * 22, y + 128, i * 16, GuiWidgets.FIT_ICON_V),
                TAB_SIZE));
        }

        // Стиль: резкие пиксели и палитра карт, под ними ползунки — число цветов (с палитрой не действует), сила дизеринга и пикселей на блок (степени двойки)
        cbSharp = add(new GuiWidgets.Check(ID_SHARP, x + 8, y + 18, I18n.format("minepapers.gui.sharp"), sharp), TAB_STYLE);
        cbMapPalette = add(new GuiWidgets.Check(ID_MAP_PALETTE, x + 8, y + 31, I18n.format("minepapers.gui.mapPalette"), mapPalette), TAB_STYLE);
        slColors = add(new GuiWidgets.Slider(ID_STYLE, x + 8, y + 44, 160, PictureFrame.MIN_COLORS, PictureFrame.MAX_COLORS, 1,
            colors, v -> I18n.format("minepapers.gui.colors", v), v -> colors = v), TAB_STYLE);
        slDither = add(new GuiWidgets.Slider(ID_STYLE + 1, x + 8, y + 68, 160, 0, PictureFrame.MAX_DITHER, 5,
            dither, v -> I18n.format("minepapers.gui.dither", v), v -> dither = v), TAB_STYLE);
        slPpb = add(new GuiWidgets.Slider(ID_STYLE + 2, x + 8, y + 92, 160, 0, PictureFrame.PPB_STEPS, 1,
            PictureFrame.ppbStep(ppb), v -> I18n.format("minepapers.gui.ppb", 1 << v), v -> ppb = 1 << v), TAB_STYLE);
        sliders = new GuiWidgets.Slider[] { slColors, slDither, slPpb };

        // Рамка: справа от слотов рамки и холста — их вид. У рамки кнопки "нет" и "обшивка", за ними — ширина багета (поле рисует рамку на пиксель шире себя — как кнопка).
        int lookY = y + ContainerPictureFrame.MATERIAL_Y - 2;
        lookButtons(frameButtons, ID_FRAME, PictureFrame.FRAME_MOLDING, 0, x, lookY);
        fMolding = field(TAB_FRAME, x + LOOK_X + GuiWidgets.Eye.WIDTH + 2 + 22 + 1, lookY + 4, 18, 3,
            tile.getFrameShape() == PictureFrame.FRAME_MOLDING ? String.valueOf(tile.moldingWidth) : "0", Numbers.MOLDING);
        lookButtons(canvasButtons, ID_CANVAS, PictureFrame.CANVAS_COUNT, 1, x, lookY + ContainerPictureFrame.MATERIAL_STEP);

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

    /** Ряд кнопок вида: первый — глаз "нет" (значение 0), за ним кнопки с иконками из ряда LOOK_ICON_V, начиная с иконки firstIcon. */
    private void lookButtons(List<GuiWidgets.Toggle> list, int firstId, int count, int firstIcon, int x, int y) {
        list.add(add(new GuiWidgets.Eye(firstId, x + LOOK_X - 4, y), TAB_FRAME)); // чуть в стороне от кнопок
        for (int i = 1; i < count; i++) {
            list.add(add(new GuiWidgets.Toggle(firstId + i, x + LOOK_X + GuiWidgets.Eye.WIDTH + 2 + (i - 1) * 22, y,
                (firstIcon + i - 1) * 16, GuiWidgets.LOOK_ICON_V), TAB_FRAME));
        }
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

    private GuiTextField field(int onTab, int x, int y, int w, int maxLength, String text, Numbers.Field number) {
        GuiTextField f = field(onTab, x, y, w, maxLength, text);
        numbers.put(f, number);
        return f;
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
        // Подписать можно только одну картину без привата и только один раз; подтверждённую подпись уже не отменить (поставится при закрытии окна, даже если закрыть и замок). Чужим подпись видна, если она есть.
        btnSign.signer = tile.signer != null ? tile.signer : sign ? me : null;
        btnSign.signerName = tile.signer != null ? tile.signerName : myName;
        btnSign.enabled = !readOnly && !tile.isSigned() && !sign && !locked && holder.count() == 1;
        btnSign.visible = !readOnly || tile.isSigned();
        btnNsfw.marked = nsfw;
        btnNsfw.enabled = !readOnly;
        btnNsfw.visible = !readOnly || nsfw;
        btnPrev.visible &= local;
        btnNext.visible &= local;
        btnFolder.visible &= local;
        for (GuiWidgets.Tab t : tabs) t.selected = t.id - ID_TAB == tab;
        cbSharp.checked = sharp;
        cbMapPalette.checked = mapPalette;
        slColors.setInt(colors);
        slColors.enabled = !mapPalette; // с палитрой карт цвета задаёт она
        slDither.setInt(dither);
        slPpb.setInt(PictureFrame.ppbStep(ppb));
        for (GuiWidgets.Toggle t : sourceButtons) t.checked = t.id - ID_SOURCE == source;
        for (GuiWidgets.Toggle t : fitButtons) t.checked = t.id - ID_FIT == fitMode;
        for (GuiWidgets.Toggle t : frameButtons) t.checked = t.id - ID_FRAME == frameShape;
        for (GuiWidgets.Toggle t : canvasButtons) t.checked = t.id - ID_CANVAS == canvasKind;
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

    /** Вид, выбранный в окне. */
    private int look() {
        return PictureFrame.look(frameShape, canvasKind);
    }

    /** "Нет" или "обшивка" — багета больше нет. Обшивать без холста нечего, поэтому с ней включается холст из блока. */
    private void chooseFrame(int value) {
        frameShape = value;
        if (value == PictureFrame.FRAME_EDGES && canvasKind == PictureFrame.CANVAS_NONE) canvasKind = PictureFrame.CANVAS_BLOCK;
        fMolding.setText("0");
    }

    /** Ширина багета больше нуля — рамка становится багетом, ноль — рамки нет (если был багет). */
    private void moldingChanged() {
        if (moldingWidth() > 0) {
            frameShape = PictureFrame.FRAME_MOLDING;
        } else if (frameShape == PictureFrame.FRAME_MOLDING) {
            frameShape = PictureFrame.FRAME_NONE;
        }
        updateWidgets();
    }

    private int moldingWidth() {
        Float v = Numbers.parse(fMolding.getText());
        return v == null ? 0 : Math.round(v);
    }

    /** Нужен ли выбранному в окне виду материал index (в порядке PictureFrame.materials(): рамка, холст, свечение). Ненужные блоки вернутся игроку при закрытии окна. */
    private boolean materialUsed(int index) {
        if (index == 0) return frameShape != PictureFrame.FRAME_NONE;
        if (index == 1) return canvasKind == PictureFrame.CANVAS_BLOCK;
        return true;
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
        } else if (id < ID_FRAME) {
            fitMode = id - ID_FIT;
        } else if (id < ID_CANVAS) {
            chooseFrame(id - ID_FRAME);
        } else if (id < ID_STYLE) {
            canvasKind = id - ID_CANVAS;
            frameShape = PictureFrame.frameShapeOf(look()); // без холста обшивки нет
        } else if (id < ID_PREV) {
            // Ползунки стиля: значение окно уже получило (Slider.change)
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
                case ID_NSFW:
                    if (!readOnly) nsfw = !nsfw;
                    break;
                case ID_SIGN:
                    if (btnSign.enabled) confirmSign();
                    break;
                case ID_SHARP:
                    sharp = !sharp;
                    break;
                case ID_MAP_PALETTE:
                    mapPalette = !mapPalette;
                    break;
                case ID_PAD_PX:
                    padPx = !padPx;
                    convert(fPad, padPx);
                    break;
            }
        }
        updateWidgets();
    }

    /** Подпись навсегда — сначала ванильный вопрос "Да / Нет" (Esc — нет). */
    private void confirmSign() {
        confirming = true;
        mc.displayGuiScreen(new GuiYesNo(this, I18n.format("minepapers.gui.sign.confirm"),
            I18n.format("minepapers.gui.sign.confirm.text", myName), CONFIRM_SIGN) {
            @Override
            protected void keyTyped(char c, int key) {
                if (key == Keyboard.KEY_ESCAPE) GuiPictureFrame.this.confirmClicked(false, CONFIRM_SIGN);
            }
        });
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
        c.nsfw = nsfw;
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
        c.look = PictureFrame.classicLook(look());
        c.frame = frameShape;
        if (frameShape == PictureFrame.FRAME_MOLDING) c.moldingWidth = moldingWidth();
        c.canvas = canvasKind;
        c.sharpPixels = sharp;
        c.mapPalette = mapPalette;
        c.colors = colors;
        c.dither = dither;
        c.style = PictureFrame.legacyStyle(mapPalette, dither);
        c.pixelsPerBlock = ppb;
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
        if (c.nsfw != null) nsfw = c.nsfw;
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
        if (c.look != null) {
            frameShape = PictureFrame.frameShapeOf(c.look);
            canvasKind = PictureFrame.canvasKindOf(c.look);
        }
        if (c.frame != null) frameShape = c.frame;
        if (c.canvas != null) canvasKind = c.canvas;
        frameShape = PictureFrame.frameShapeOf(look());
        // Багет без ширины (конфиг прежних версий) — обычной ширины.
        fMolding.setText(frameShape != PictureFrame.FRAME_MOLDING ? "0" : String.valueOf(
            c.moldingWidth != null && c.moldingWidth > 0 ? c.moldingWidth : PictureFrame.DEFAULT_MOLDING));
        if (c.style != null) {
            mapPalette = sharp = c.style != PictureFrame.STYLE_ORIGINAL;
            dither = PictureFrame.legacyDither(c.style);
        }
        if (c.sharpPixels != null) sharp = c.sharpPixels;
        if (c.mapPalette != null) mapPalette = c.mapPalette;
        if (c.colors != null) colors = Math.max(PictureFrame.MIN_COLORS, Math.min(PictureFrame.MAX_COLORS, c.colors));
        if (c.dither != null) dither = Math.max(0, Math.min(PictureFrame.MAX_DITHER, c.dither));
        if (c.pixelsPerBlock != null) ppb = 1 << PictureFrame.ppbStep(c.pixelsPerBlock);
        updateWidgets();
        // Конфиг из новых версий: предметы там названы иначе (grass_block вместо grass) — переводим.
        for (int i = 0; i < c.materials.length; i++) {
            c.materials[i] = c.translate(c.materials[i], false, id -> Item.itemRegistry.getObject(id) != null);
        }
        for (boolean has : c.hasMaterial) {
            if (has && container().materialsEditable()) {
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
        tile.nsfw = nsfw;
        // Владельца решает сервер (PacketSaveFrame); здесь — только просьба закрыть или открыть замок.
        if (locked && tile.owner == null) {
            tile.owner = me;
            tile.ownerName = myName;
        } else if (!locked) {
            tile.owner = null;
            tile.ownerName = "";
        }
        // Подпись тоже только просьба: её ставит сервер.
        if (sign && tile.signer == null) {
            tile.signer = me;
            tile.signerName = myName;
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
        tile.sharpPixels = sharp;
        tile.mapPalette = mapPalette;
        tile.colors = colors;
        tile.dither = dither;
        tile.mode = look();
        if (frameShape == PictureFrame.FRAME_MOLDING) tile.moldingWidth = moldingWidth();
        tile.pixelsPerBlock = ppb;

        // Прогоняем через те же ограничения, что и сервер.
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeSettings(tag);
        tile.readSettings(tag);
        NBTTagCompound after = new NBTTagCompound();
        tile.writeSettings(after);
        // Окно открыли и закрыли — серверу отправлять нечего, если только не надо вернуть ненужные виду блоки.
        if (after.equals(before) && !(container().materialsEditable() && hasUnusedMaterials())) return;

        if (holder.picture != null) holder.picture.refresh(); // хитбокс сразу по новому размеру, не дожидаясь сервера
        MinePapers.network.sendToServer(new PacketSaveFrame(holder));
        // Файлы с этого компьютера другие игроки увидят, только когда они окажутся на сервере.
        ImageSync.offer(holder.target, tile);
    }

    private boolean hasUnusedMaterials() {
        PictureFrame.Material[] materials = tile.materials();
        for (int i = 0; i < materials.length; i++) {
            if (materials[i].stack != null && !materialUsed(i)) return true;
        }
        return false;
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
                Numbers.Field number = numbers.get(f);
                if (number == null) {
                    f.textboxKeyTyped(c, key);
                } else {
                    numberKeyTyped(f, number, c, key);
                }
                return;
            }
        }
        super.keyTyped(c, key); // E закрывает окно, цифры — обмен с хотбаром, как в любом инвентаре
    }

    /** Числовое поле: стрелки вверх и вниз меняют число (влево и вправо, как обычно, двигают курсор); то, что не число (набранное или вставленное), не проходит. */
    private void numberKeyTyped(GuiTextField f, Numbers.Field number, char c, int key) {
        int dir = key == Keyboard.KEY_UP ? 1 : key == Keyboard.KEY_DOWN ? -1 : 0;
        if (dir != 0) {
            step(f, number, dir);
            return;
        }
        String old = f.getText();
        int cursor = f.getCursorPosition(), selection = f.getSelectionEnd();
        f.textboxKeyTyped(c, key);
        if (!number.accepts(f.getText())) {
            f.setText(old);
            f.setCursorPosition(cursor);
            f.setSelectionPos(selection);
        }
        if (f == fMolding) moldingChanged();
    }

    /** Шаг числа стрелкой или колёсиком: размер и отступ — в единицах, выбранных галочкой "px". */
    private void step(GuiTextField f, Numbers.Field number, int dir) {
        boolean px = f == fWidth || f == fHeight ? sizePx : f == fPad && padPx;
        f.setText(number.step(f.getText(), dir, px ? 16f : 1f));
        if (f == fMolding) moldingChanged();
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
        for (GuiWidgets.Slider s : sliders) {
            if (s.isDragging()) return; // ползунок тянется сам (Slider.drawButton)
        }
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

    /** Колёсико над многострочным полем прокручивает его, над числовым (и без клика по нему) — меняет число. */
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
        for (Map.Entry<GuiTextField, Numbers.Field> e : numbers.entrySet()) {
            if (isActive(e.getKey()) && over(e.getKey(), mouseX, mouseY)) step(e.getKey(), e.getValue(), wheel > 0 ? 1 : -1);
        }
        // Ползунок: колёсико на себя — вправо, от себя — влево.
        for (GuiWidgets.Slider s : sliders) {
            if (s.visible && s.hovered()) s.scroll(wheel < 0 ? 1 : -1);
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
        if (confirming) return; // ушли в вопрос о подписи — окно ещё вернётся
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
        if (tab != TAB_SIZE && tab != TAB_STYLE && tab != TAB_INFO) text(I18n.format("minepapers.gui.tab." + tab), 8, 6, GuiWidgets.TEXT);
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
                text(I18n.format("minepapers.gui.style"), 8, 6, GuiWidgets.TEXT);
                break;
            case TAB_INFO:
                text(I18n.format("minepapers.gui.titleLabel"), 8, 6, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.description"), 8, 36, GuiWidgets.TEXT);
                break;
            case TAB_FRAME: {
                // Список материалов: слот и что это (какой блок в слоте — в подсказке самого блока), у свечения — сила света. Слот, ненужный выбранному виду, бледный: его блок вернётся при закрытии окна.
                PictureFrame.Material[] materials = tile.materials();
                for (int i = 0; i < materials.length; i++) {
                    int ty = ContainerPictureFrame.MATERIAL_Y + 1 + i * ContainerPictureFrame.MATERIAL_STEP;
                    String label = I18n.format("minepapers.gui.row." + materials[i].key.toLowerCase(java.util.Locale.ROOT));
                    if (!materials[i].light) {
                        text(label, 30, ty + 3, GuiWidgets.TEXT);
                    } else {
                        ItemStack glow = materials[i].stack;
                        text(label, 30, ty - 1, GuiWidgets.TEXT);
                        text(glow == null ? I18n.format("minepapers.gui.glow.default") : PictureFrame.isGlowDust(glow)
                            ? I18n.format("minepapers.gui.glow.self") : I18n.format("minepapers.gui.glow.level", PictureFrame.lightOf(glow)),
                            30, ty + 8, GuiWidgets.HINT);
                    }
                    if (!materialUsed(i)) {
                        GL11.glDisable(GL11.GL_LIGHTING);
                        GL11.glDisable(GL11.GL_DEPTH_TEST);
                        drawRect(ContainerPictureFrame.MATERIAL_X, ty - 1, ContainerPictureFrame.MATERIAL_X + 16, ty + 15, 0xB0C6C6C6);
                        GL11.glEnable(GL11.GL_DEPTH_TEST);
                    }
                }
                if (!container().materialsEditable()) {
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
        if (id == CONFIRM_SIGN) {
            if (yes) sign = true;
            confirming = false;
            mc.displayGuiScreen(this);
            return;
        }
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
        for (int i = 0; i < sliders.length; i++) {
            if (!sliders[i].hovered()) continue;
            if (!sliders[i].isDragging()) tooltip(SLIDER_TIPS[i], mouseX, mouseY);
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
        for (GuiWidgets.Toggle t : frameButtons) {
            if (t.hovered()) {
                String key = "minepapers.gui.frameShape." + (t.id - ID_FRAME);
                titledTooltip(key, key + ".tip", mouseX, mouseY);
                return;
            }
        }
        if (tab == TAB_FRAME && over(fMolding, mouseX, mouseY)) {
            titledTooltip("minepapers.gui.frameShape.2", "minepapers.gui.frameShape.2.tip", mouseX, mouseY);
            return;
        }
        for (GuiWidgets.Toggle t : canvasButtons) {
            if (t.hovered()) {
                String key = "minepapers.gui.canvasKind." + (t.id - ID_CANVAS);
                titledTooltip(key, key + ".tip", mouseX, mouseY);
                return;
            }
        }
        if (btnNsfw.hovered()) {
            titledTooltip("minepapers.gui.nsfw", nsfw ? "minepapers.gui.nsfw.on" : "minepapers.gui.nsfw.tip", mouseX, mouseY);
        } else if (btnSign.hovered()) {
            if (tile.isSigned()) {
                tooltip("minepapers.gui.signer", mouseX, mouseY, tile.signerName);
            } else if (sign) {
                List<String> lines = new ArrayList<String>();
                lines.add(I18n.format("minepapers.gui.sign.pending", myName));
                lines.add("§7" + I18n.format("minepapers.gui.sign.onClose"));
                drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
            } else if (holder.count() > 1) {
                titledTooltip("minepapers.gui.sign", "minepapers.gui.sign.stack", mouseX, mouseY);
            } else {
                titledTooltip("minepapers.gui.sign", "minepapers.gui.sign.tip", mouseX, mouseY);
            }
        } else if (btnLock.hovered()) {
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
        } else if (cbSharp.hovered()) {
            tooltip("minepapers.gui.sharp.tip", mouseX, mouseY);
        } else if (cbMapPalette.hovered()) {
            tooltip("minepapers.gui.mapPalette.tip", mouseX, mouseY);
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
