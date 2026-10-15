package com.grihail.minepapers.client;

import static com.grihail.minepapers.client.FrameEditor.*;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketPasteMaterials;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.Links;
import com.grihail.minepapers.picture.Numbers;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ITextComponent;

/** Окно настройки картины: вкладки слева, под настройками — инвентарь игрока. Что выбрано и что с этим делать — в общем FrameEditor, здесь виджеты 1.12.2. Настройки применяются при закрытии окна. */
public class GuiPictureFrame extends GuiContainer implements GuiYesNoCallback {

    /** id кнопок: вкладки — 0..4, группы переключателей — начало группы + значение. */
    private static final int ID_TAB = 0, ID_SOURCE = 10, ID_FIT = 20, ID_FRAME = 30, ID_CANVAS = 35, ID_STYLE = 40, ID_DITHER_MODE = 45,
        ID_PREV = 50, ID_NEXT = 51, ID_FOLDER = 52, ID_RELOAD = 53, ID_SIZE_PX = 54, ID_POS_PX = 55,
        ID_COPY = 56, ID_PASTE = 57, ID_PAD_PX = 58, ID_LOCK = 59, ID_SIGN = 60, ID_SHARP = 61, ID_MAP_PALETTE = 62, ID_NSFW = 63, ID_ZOOM_PX = 64;

    /** Ширина — как у сундука; низ окна — часть его текстуры с инвентарём, слоты в ней на 14 пикселей ниже её верха. */
    private static final int WIDTH = 176,
        HEIGHT = ContainerPictureFrame.INVENTORY_Y - GuiWidgets.INVENTORY_PART_SLOTS_Y + GuiWidgets.INVENTORY_PART_HEIGHT;

    /** Левый край кнопок вида рамки и холста (три кнопки в ряд, до правого края панели). */
    private static final int LOOK_X = WIDTH - 8 - 64;

    /** Картина-сущность или картина-предмет в руке. */
    private final FrameHolder holder;
    private final PictureFrame tile;
    private final Editor ed;
    private final boolean readOnly;

    /** Открыто ванильное окно "Подписать картину?": окно картины к нему вернётся, поэтому не сохраняется и не закрывается. */
    private boolean confirming;
    /** id вопроса о подписи в confirmClicked (0 — ссылка). */
    private static final int CONFIRM_SIGN = 1;
    private GuiWidgets.Lock btnLock;
    private GuiWidgets.Sign btnSign;
    private GuiWidgets.Nsfw btnNsfw;

    private final List<GuiWidgets.Tab> tabs = new ArrayList<GuiWidgets.Tab>();
    private GuiWidgets.Slider slColors, slDither, slPpb;
    /** Ползунки вкладки "Стиль" и их подсказки — по порядку. */
    private GuiWidgets.Slider[] sliders;
    private static final String[] SLIDER_TIPS = { "minepapers.gui.colors.tip", "minepapers.gui.dither.tip", "minepapers.gui.ppb.tip" };
    /** Источник и вписывание — ряды кнопок с картинками; название и пояснение — в подсказке. */
    private final List<GuiWidgets.Toggle> sourceButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> fitButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Вид дизеринга на вкладке "Стиль" — кнопки с иконками, по порядку PictureFrame.DITHER_*. */
    private final List<GuiWidgets.Toggle> ditherButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Рамка и холст — ряды кнопок справа от их слотов на вкладке "Рамка". */
    private final List<GuiWidgets.Toggle> frameButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> canvasButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Многострочные поля (путь, ссылки, описание): им нужны перетаскивание мыши и колёсико. */
    private final List<GuiTextArea> areas = new ArrayList<GuiTextArea>();
    /** Вкладка, на которой лежит кнопка (у самих вкладок записи нет — они видны всегда). */
    private final Map<GuiButton, Integer> buttonTab = new HashMap<GuiButton, Integer>();
    private GuiWidgets.Small btnPrev, btnNext, btnFolder, btnReload, btnCopy, btnPaste;
    private GuiWidgets.Check cbSizePx, cbPosPx, cbPadPx, cbZoomPx, cbSharp, cbMapPalette;

    /** Поля ввода по номерам FrameEditor.F_* и их место {x, y, ширина, высота} — для подсказок и выделения мышью. */
    private final GuiTextField[] fields = new GuiTextField[FIELD_COUNT];
    private final int[][] fieldRect = new int[FIELD_COUNT][];
    /** Однострочное поле, в котором зажали кнопку мыши: перетаскивание выделяет в нём текст. */
    private GuiTextField dragField;

    public GuiPictureFrame(ContainerPictureFrame container) {
        super(container);
        this.holder = container.holder;
        this.tile = holder.frame;
        this.ed = new Editor(Minecraft.getMinecraft().player);
        this.readOnly = ed.readOnly;
        this.xSize = WIDTH;
        this.ySize = HEIGHT;
    }

    /** Общая логика окна с доступом к виджетам 1.12.2. */
    private final class Editor extends FrameEditor {

        Editor(EntityPlayer player) {
            super(holder.frame, holder.target, player.getUniqueID(), player.getName(), "1.12.2");
        }

        @Override
        protected String text(int field) {
            return fields[field].getText();
        }

        @Override
        protected void setText(int field, String value) {
            fields[field].setText(value);
        }

        @Override
        protected String tr(String key, Object... args) {
            return I18n.format(key, args);
        }

        @Override
        protected int count() {
            return holder.count();
        }

        @Override
        protected PictureConfig.Item material(int index) {
            PictureFrame.Material m = holder.frame.materials()[index];
            return !m.stack.isEmpty() ? new PictureConfig.Item(m.item, m.meta) : null;
        }

        @Override
        protected boolean itemExists(String id) {
            return Item.getByNameOrId(id) != null;
        }

        @Override
        protected long worldTime() {
            return mc.world.getTotalWorldTime();
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        // При изменении размера окна initGui вызывается снова — сохраняем то, что уже введено.
        String[] old = null;
        if (fields[0] != null) {
            old = new String[FIELD_COUNT];
            for (int i = 0; i < FIELD_COUNT; i++) old[i] = fields[i].getText();
        }
        tabs.clear();
        sourceButtons.clear();
        fitButtons.clear();
        ditherButtons.clear();
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
        for (int f = F_PATH; f <= F_IMAGE; f++) area(new GuiTextArea(f, fontRenderer, x + 9, y + 53, 158, 44), f, x + 9, y + 53, 158, 44);
        btnPrev = small(TAB_SOURCE, ID_PREV, x + 8, y + 100, 14, 14, "<", -1);
        btnNext = small(TAB_SOURCE, ID_NEXT, x + 24, y + 100, 14, 14, ">", -1);
        String open = I18n.format("minepapers.gui.openFolder");
        btnFolder = small(TAB_SOURCE, ID_FOLDER, x + 40, y + 100, fontRenderer.getStringWidth(open) + 12, 14, open, -1);
        // Интервал и состояние — внизу вкладки, над инвентарём
        field(F_INTERVAL, x + 129, y + 117, 38, 6);
        btnReload = small(TAB_SOURCE, ID_RELOAD, x + 154, y + 131, 14, 14, "", GuiWidgets.ICON_RELOAD_U);

        // Размер и позиция
        field(F_WIDTH, x + 9, y + 19, 40, 10);
        field(F_HEIGHT, x + 63, y + 19, 40, 10);
        cbSizePx = check(TAB_SIZE, ID_SIZE_PX, x + 112, y + 20);
        field(F_OFF_X, x + 9, y + 49, 40, 10);
        field(F_OFF_Y, x + 63, y + 49, 40, 10);
        cbPosPx = check(TAB_SIZE, ID_POS_PX, x + 112, y + 50);
        // Отступ и масштаб — в одном ряду, у каждого своя галочка "px".
        field(F_PAD, x + 9, y + 79, 40, 10);
        cbPadPx = check(TAB_SIZE, ID_PAD_PX, x + 54, y + 80);
        field(F_ZOOM, x + 85, y + 79, 40, 10);
        cbZoomPx = check(TAB_SIZE, ID_ZOOM_PX, x + 130, y + 80);
        for (int i = 0; i < PictureFrame.FIT_COUNT; i++) {
            // Внизу вкладки, над инвентарём
            fitButtons.add(add(new GuiWidgets.Toggle(ID_FIT + i, x + 8 + i * 22, y + 128, i * 16, GuiWidgets.FIT_ICON_V),
                TAB_SIZE));
        }

        // Стиль: резкие пиксели и палитра карт, под ними ползунки — число цветов (с палитрой не действует), сила дизеринга и пикселей на блок
        cbSharp = add(new GuiWidgets.Check(ID_SHARP, x + 8, y + 18, I18n.format("minepapers.gui.sharp"), ed.sharp), TAB_STYLE);
        cbMapPalette = add(new GuiWidgets.Check(ID_MAP_PALETTE, x + 8, y + 31, I18n.format("minepapers.gui.mapPalette"), ed.mapPalette), TAB_STYLE);
        slColors = add(new GuiWidgets.Slider(ID_STYLE, x + 8, y + 44, 160, PictureFrame.MIN_COLORS, PictureFrame.MAX_COLORS, (v, d) -> v + d,
            ed.colors, v -> I18n.format("minepapers.gui.colors", v), v -> ed.colors = v), TAB_STYLE);
        for (int i = 0; i < PictureFrame.DITHER_MODE_COUNT; i++) {
            ditherButtons.add(add(new GuiWidgets.Toggle(ID_DITHER_MODE + i, x + 8 + i * 22, y + 68, GuiWidgets.DITHER_ICON_U + i * 16,
                GuiWidgets.DITHER_ICON_V), TAB_STYLE));
        }
        slDither = add(new GuiWidgets.Slider(ID_STYLE + 1, x + 8, y + 92, 160, 0, PictureFrame.MAX_DITHER, (v, d) -> v + 5 * d,
            ed.dither, v -> I18n.format("minepapers.gui.dither", v), v -> ed.dither = v), TAB_STYLE);
        slPpb = add(new GuiWidgets.Slider(ID_STYLE + 2, x + 8, y + 116, 160, PictureFrame.MIN_PPB, PictureFrame.MAX_PPB, PictureFrame::ppbWheel,
            ed.ppb, v -> I18n.format("minepapers.gui.ppb", v), v -> ed.ppb = v), TAB_STYLE);
        sliders = new GuiWidgets.Slider[] { slColors, slDither, slPpb };

        // Рамка: справа от слотов рамки и холста — их вид. У рамки кнопки "нет" и "обшивка", за ними — ширина багета (поле рисует рамку на пиксель шире себя — как кнопка).
        int lookY = y + ContainerPictureFrame.MATERIAL_Y - 2;
        lookButtons(frameButtons, ID_FRAME, PictureFrame.FRAME_MOLDING, 0, x, lookY);
        field(F_MOLDING, x + LOOK_X + 2 * 22 + 1, lookY + 4, 18, 3);
        lookButtons(canvasButtons, ID_CANVAS, PictureFrame.CANVAS_COUNT, 1, x, lookY + ContainerPictureFrame.MATERIAL_STEP);

        // Описание
        field(F_TITLE, x + 9, y + 18, 158, PictureFrame.MAX_TITLE);
        area(new GuiTextArea(F_DESC, fontRenderer, x + 9, y + 48, 158, 92).multiline(), F_DESC, x + 9, y + 48, 158, 92);

        if (old != null) {
            for (int i = 0; i < FIELD_COUNT; i++) fields[i].setText(old[i]);
        }
        updateWidgets();
    }

    @SuppressWarnings("unchecked")
    private <T extends GuiButton> T add(T button, int onTab) {
        buttonList.add(button);
        if (onTab >= 0) buttonTab.put(button, onTab);
        return button;
    }

    /** Ряд кнопок вида: первая — крест "нет" (значение 0), за ним кнопки с иконками из ряда LOOK_ICON_V, начиная с иконки firstIcon. */
    private void lookButtons(List<GuiWidgets.Toggle> list, int firstId, int count, int firstIcon, int x, int y) {
        list.add(add(new GuiWidgets.Toggle(firstId, x + LOOK_X, y, GuiWidgets.NONE_ICON_U, GuiWidgets.NONE_ICON_V), TAB_FRAME));
        for (int i = 1; i < count; i++) {
            list.add(add(new GuiWidgets.Toggle(firstId + i, x + LOOK_X + i * 22, y,
                (firstIcon + i - 1) * 16, GuiWidgets.LOOK_ICON_V), TAB_FRAME));
        }
    }

    private GuiWidgets.Small small(int onTab, int id, int x, int y, int w, int h, String text, int iconU) {
        return add(new GuiWidgets.Small(id, x, y, w, h, text, iconU), onTab);
    }

    private GuiWidgets.Check check(int onTab, int id, int x, int y) {
        return add(new GuiWidgets.Check(id, x, y, I18n.format("minepapers.gui.pixels"), false), onTab);
    }

    /** Однострочное поле f; числовые (FrameEditor.number) принимают только число, стрелки и колёсико меняют его на шаг. */
    private void field(int f, int x, int y, int w, int maxLength) {
        register(new GuiTextField(f, fontRenderer, x, y, w, 12), f, x, y, w, 12, maxLength);
    }

    /** Многострочное поле f для пути, ссылки или описания. */
    private void area(GuiTextArea a, int f, int x, int y, int w, int h) {
        areas.add(a);
        register(a, f, x, y, w, h, 512);
    }

    private void register(GuiTextField field, int f, int x, int y, int w, int h, int maxLength) {
        field.setMaxStringLength(maxLength);
        field.setText(ed.initialText(f));
        fields[f] = field;
        fieldRect[f] = new int[] { x, y, w, h };
    }

    private void updateWidgets() {
        boolean local = ed.source == PictureFrame.SOURCE_LOCAL;
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            Integer onTab = buttonTab.get(b);
            if (onTab != null) b.visible = !readOnly && onTab == ed.tab;
        }
        for (GuiWidgets.Tab t : tabs) t.visible = !readOnly;
        btnCopy.visible = btnPaste.visible = !readOnly;
        btnLock.owner = ed.lockOwner();
        btnLock.ownerName = ed.lockOwnerName();
        btnLock.mine = !readOnly;
        // Чужим подпись видна, если она есть.
        btnSign.signer = ed.signer();
        btnSign.signerName = ed.signerName();
        btnSign.enabled = ed.canSign();
        btnSign.visible = !readOnly || tile.isSigned();
        btnNsfw.marked = ed.nsfw;
        btnNsfw.enabled = !readOnly;
        btnNsfw.visible = !readOnly || ed.nsfw;
        btnPrev.visible &= local;
        btnNext.visible &= local;
        btnFolder.visible &= local;
        for (GuiWidgets.Tab t : tabs) t.selected = t.id - ID_TAB == ed.tab;
        cbSharp.checked = ed.sharp;
        cbMapPalette.checked = ed.mapPalette;
        slColors.setInt(ed.colors);
        slColors.enabled = !ed.mapPalette; // с палитрой карт цвета задаёт она
        slDither.setInt(ed.dither);
        slPpb.setInt(ed.ppb);
        for (GuiWidgets.Toggle t : sourceButtons) t.checked = t.id - ID_SOURCE == ed.source;
        for (GuiWidgets.Toggle t : fitButtons) t.checked = t.id - ID_FIT == ed.fitMode;
        for (GuiWidgets.Toggle t : ditherButtons) t.checked = t.id - ID_DITHER_MODE == ed.ditherMode;
        for (GuiWidgets.Toggle t : frameButtons) t.checked = t.id - ID_FRAME == ed.frameShape;
        for (GuiWidgets.Toggle t : canvasButtons) t.checked = t.id - ID_CANVAS == ed.canvasKind;
        // Слот рамки есть всегда (его содержимое синхронизирует сервер), но виден и кликается только на своей вкладке.
        for (Object o : inventorySlots.inventorySlots) {
            Slot s = (Slot) o;
            if (container().isMaterialSlot(s.slotNumber)) {
                s.xPos = !readOnly && ed.tab == TAB_FRAME ? ContainerPictureFrame.MATERIAL_X : -10000;
            }
        }
        cbSizePx.checked = ed.sizePx;
        cbPosPx.checked = ed.posPx;
        cbPadPx.checked = ed.padPx;
        cbZoomPx.checked = ed.zoomPx;
        for (int f = 0; f < FIELD_COUNT; f++) {
            if (!ed.fieldShown(f)) fields[f].setFocused(false);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int id = button.id;
        if (id < ID_SOURCE) {
            ed.selectTab(id - ID_TAB);
        } else if (id < ID_FIT) {
            ed.source = id - ID_SOURCE;
        } else if (id < ID_FRAME) {
            ed.fitMode = id - ID_FIT;
        } else if (id < ID_CANVAS) {
            ed.chooseFrame(id - ID_FRAME);
        } else if (id < ID_STYLE) {
            ed.chooseCanvas(id - ID_CANVAS);
        } else if (id < ID_PREV) {
            // Ползунки стиля значение уже передали сами (Slider.change), кнопки вида дизеринга — здесь
            if (id >= ID_DITHER_MODE) ed.ditherMode = id - ID_DITHER_MODE;
        } else {
            switch (id) {
                case ID_PREV:
                case ID_NEXT:
                    ed.cycleLocal(id == ID_NEXT ? 1 : -1);
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
                    ed.reload();
                    break;
                case ID_SIZE_PX:
                    ed.toggleSizePx();
                    break;
                case ID_POS_PX:
                    ed.togglePosPx();
                    break;
                case ID_LOCK:
                    if (!readOnly) ed.locked = !ed.locked;
                    break;
                case ID_NSFW:
                    if (!readOnly) ed.nsfw = !ed.nsfw;
                    break;
                case ID_SIGN:
                    if (btnSign.enabled) confirmSign();
                    break;
                case ID_SHARP:
                    ed.sharp = !ed.sharp;
                    break;
                case ID_MAP_PALETTE:
                    ed.mapPalette = !ed.mapPalette;
                    break;
                case ID_PAD_PX:
                    ed.togglePadPx();
                    break;
                case ID_ZOOM_PX:
                    ed.toggleZoomPx();
                    break;
            }
        }
        updateWidgets();
    }

    /** Подпись навсегда — сначала ванильный вопрос "Да / Нет" (Esc — нет). */
    private void confirmSign() {
        confirming = true;
        mc.displayGuiScreen(new GuiYesNo(this, I18n.format("minepapers.gui.sign.confirm"),
            I18n.format("minepapers.gui.sign.confirm.text", ed.myName), CONFIRM_SIGN) {
            @Override
            protected void keyTyped(char c, int key) {
                if (key == Keyboard.KEY_ESCAPE) GuiPictureFrame.this.confirmClicked(false, CONFIRM_SIGN);
            }
        });
    }

    private void openFolder() {
        try {
            Desktop.getDesktop().open(MinePapers.picturesDir);
        } catch (Throwable t) {
            Sys.openURL("file://" + MinePapers.picturesDir.getAbsolutePath());
        }
    }

    // ---------- Копировать и вставить ----------

    /** Все настройки окна (как они введены сейчас) и материалы картины — в буфер обмена JSON-ом. */
    private void copyConfig() {
        setClipboardString(ed.copyConfig().toJson());
        chat(new TextComponentTranslation("minepapers.chat.copied"), TextFormatting.GRAY);
    }

    /** Настройки из буфера обмена — в окно (применятся при закрытии, как и введённые руками); материалы сервер берёт из инвентаря (PacketPasteMaterials). */
    private void pasteConfig() {
        PictureConfig c = ed.paste(getClipboardString());
        if (c == null) {
            chat(new TextComponentTranslation("minepapers.chat.badConfig"), TextFormatting.RED);
            return;
        }
        updateWidgets();
        if (c.anyMaterial() && container().materialsEditable()) MinePapers.network.sendToServer(new PacketPasteMaterials(holder.target, c));
    }

    private void chat(ITextComponent text, TextFormatting color) {
        text.getStyle().setColor(color);
        mc.player.sendMessage(text);
    }

    // ---------- Сохранение ----------

    private void save() {
        if (!ed.save(container().materialsEditable())) return;
        if (holder.picture != null) holder.picture.refresh(); // хитбокс сразу по новому размеру, не дожидаясь сервера
        MinePapers.network.sendToServer(new PacketSaveFrame(holder));
        // Файлы с этого компьютера другие игроки увидят, только когда они окажутся на сервере.
        ImageSync.offer(holder.target, tile);
    }

    // ---------- Ввод ----------

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.player.closeScreen(); // сохранение — в onGuiClosed
            return;
        }
        if (key == Keyboard.KEY_TAB) {
            focusNext();
            return;
        }
        for (int f = 0; f < FIELD_COUNT; f++) {
            GuiTextField field = fields[f];
            if (ed.fieldShown(f) && field.isFocused()) {
                if (FrameEditor.number(f) == null) {
                    field.textboxKeyTyped(c, key);
                } else {
                    numberKeyTyped(f, c, key);
                }
                return;
            }
        }
        super.keyTyped(c, key); // E закрывает окно, цифры — обмен с хотбаром, как в любом инвентаре
    }

    /** Числовое поле: стрелки вверх и вниз меняют число (влево и вправо, как обычно, двигают курсор); то, что не число (набранное или вставленное), не проходит. */
    private void numberKeyTyped(int f, char c, int key) {
        int dir = key == Keyboard.KEY_UP ? 1 : key == Keyboard.KEY_DOWN ? -1 : 0;
        if (dir != 0) {
            ed.step(f, dir);
            updateWidgets();
            return;
        }
        GuiTextField field = fields[f];
        String old = field.getText();
        int cursor = field.getCursorPosition(), selection = field.getSelectionEnd();
        field.textboxKeyTyped(c, key);
        if (!FrameEditor.number(f).accepts(field.getText())) {
            field.setText(old);
            field.setCursorPosition(cursor);
            field.setSelectionPos(selection);
        }
        if (f == F_MOLDING) {
            ed.moldingChanged();
            updateWidgets();
        }
    }

    private void focusNext() {
        List<GuiTextField> active = new ArrayList<GuiTextField>();
        for (int f = 0; f < FIELD_COUNT; f++) {
            if (ed.fieldShown(f)) active.add(fields[f]);
        }
        if (active.isEmpty()) return;
        int focused = -1;
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).isFocused()) focused = i;
            active.get(i).setFocused(false);
        }
        active.get((focused + 1) % active.size()).setFocused(true);
    }

    private boolean shown(GuiTextField field) {
        for (int f = 0; f < FIELD_COUNT; f++) {
            if (fields[f] == field) return ed.fieldShown(f);
        }
        return false;
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        String link = button == 0 ? linkAt(x, y) : null;
        if (link != null) {
            openLink(link);
            return;
        }
        dragField = null;
        for (int f = 0; f < FIELD_COUNT; f++) {
            if (!ed.fieldShown(f)) continue;
            GuiTextField field = fields[f];
            field.mouseClicked(x, y, button);
            if (button == 0 && field.isFocused() && !(field instanceof GuiTextArea)) dragField = field;
        }
        // Кнопки обрабатываем сами: вкладки лежат за краем окна, а клик туда с предметом в руке GuiContainer счёл бы выбросом.
        if (button == 0) {
            for (Object o : buttonList) {
                GuiButton b = (GuiButton) o;
                if (b.mousePressed(mc, x, y)) {
                    b.playPressSound(mc.getSoundHandler());
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
            if (shown(a) && a.mouseDragged(mouseX, mouseY)) return;
        }
        if (dragField != null && shown(dragField) && dragField.isFocused()) {
            selectTo(dragField, mouseX);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, button, sinceClick);
    }

    /** Выделение в однострочном поле перетаскиванием: курсор остаётся, где нажали, конец выделения — под мышью. Позицию под мышью считает само поле (клик), затем курсор возвращаем. */
    private void selectTo(GuiTextField field, int mouseX) {
        int[] r = fieldRect[Arrays.asList(fields).indexOf(field)];
        int anchor = field.getCursorPosition();
        field.mouseClicked(Math.max(r[0], Math.min(r[0] + r[2] - 1, mouseX)), r[1], 0);
        int pos = field.getCursorPosition();
        field.setCursorPosition(anchor);
        field.setSelectionPos(pos);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        for (GuiTextArea a : areas) a.mouseReleased();
        dragField = null;
        super.mouseReleased(mouseX, mouseY, button);
    }

    /** Колёсико над многострочным полем прокручивает его, над числовым (и без клика по нему) — меняет число. */
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        for (GuiTextArea a : areas) {
            if (shown(a) && a.contains(mouseX, mouseY)) a.scrollBy(wheel > 0 ? -1 : 1);
        }
        for (int f = 0; f < FIELD_COUNT; f++) {
            if (FrameEditor.number(f) != null && ed.fieldShown(f) && over(f, mouseX, mouseY)) {
                ed.step(f, wheel > 0 ? 1 : -1);
                updateWidgets();
            }
        }
        // Ползунок: колёсико на себя — вправо, от себя — влево.
        for (GuiWidgets.Slider s : sliders) {
            if (s.visible && s.hovered()) s.scroll(wheel < 0 ? 1 : -1);
        }
    }

    /** Shift+клик кладёт предмет в слот рамки — только когда этот слот виден. */
    @Override
    protected void handleMouseClick(Slot slot, int slotId, int button, ClickType type) {
        if (type == ClickType.QUICK_MOVE && ed.tab != TAB_FRAME && slot != null && !container().isMaterialSlot(slot.slotNumber)) return;
        super.handleMouseClick(slot, slotId, button, type);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (int f = 0; f < FIELD_COUNT; f++) {
            if (ed.fieldShown(f)) fields[f].updateCursorCounter();
        }
    }

    @Override
    public void onGuiClosed() {
        if (confirming) return; // ушли в вопрос о подписи — окно ещё вернётся
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        // Настройки применяются при любом закрытии окна: Esc, E, Enter или сервер закрыл его сам.
        if (!readOnly && mc.player != null && mc.getConnection() != null && holder.exists()) save();
    }

    // ---------- Отрисовка ----------

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
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
        if (!readOnly && ed.tab == TAB_FRAME) {
            for (int i = 0; i < tile.materials().length; i++) {
                GuiWidgets.slot(x + ContainerPictureFrame.MATERIAL_X - 1,
                    y + ContainerPictureFrame.MATERIAL_Y - 1 + i * ContainerPictureFrame.MATERIAL_STEP);
            }
        }
        for (int f = 0; f < FIELD_COUNT; f++) {
            if (ed.fieldShown(f)) fields[f].drawTextBox();
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
        int tab = ed.tab;
        if (tab != TAB_SIZE && tab != TAB_STYLE && tab != TAB_INFO) text(I18n.format("minepapers.gui.tab." + tab), 8, 6, GuiWidgets.TEXT);
        switch (tab) {
            case TAB_SOURCE: {
                text(I18n.format("minepapers.gui.path." + ed.source), 8, 42, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.interval"), 8, 119, GuiWidgets.TEXT);
                String status = ed.statusText();
                lines(status, 8, 133, 142, 2, ed.statusError ? GuiWidgets.ERROR : GuiWidgets.TEXT);
                break;
            }
            case TAB_SIZE:
                text(I18n.format("minepapers.gui.size"), 8, 6, GuiWidgets.TEXT);
                text("x", 53, 21, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.offset"), 8, 36, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.padding"), 8, 66, GuiWidgets.TEXT);
                text(I18n.format("minepapers.gui.zoom"), 84, 66, GuiWidgets.TEXT);
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
                        text(glow.isEmpty() ? I18n.format("minepapers.gui.glow.default") : PictureFrame.isGlowDust(glow)
                            ? I18n.format("minepapers.gui.glow.self") : I18n.format("minepapers.gui.glow.level", PictureFrame.lightOf(glow)),
                            30, ty + 8, GuiWidgets.HINT);
                    }
                    if (!ed.materialUsed(i)) {
                        GlStateManager.disableLighting();
                        GlStateManager.disableDepth();
                        drawRect(ContainerPictureFrame.MATERIAL_X, ty - 1, ContainerPictureFrame.MATERIAL_X + 16, ty + 15, 0xB0C6C6C6);
                        GlStateManager.enableDepth();
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
        fontRenderer.drawString(s, x, y, color);
    }

    /** Текст с переносом по ширине, не больше maxLines строк. Возвращает y под последней строкой. */
    @SuppressWarnings("unchecked")
    private int lines(String s, int x, int y, int width, int maxLines, int color) {
        List<String> list = fontRenderer.listFormattedStringToWidth(s, width);
        for (int i = 0; i < list.size() && i < maxLines; i++) {
            text(list.get(i), x, y, color);
            y += 9;
        }
        return y;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        if (mc.player.inventory.getItemStack().isEmpty()) drawTooltips(mouseX, mouseY);
        String link = linkAt(mouseX, mouseY);
        if (link != null) drawHoveringText(Arrays.asList(link), mouseX, mouseY, fontRenderer);
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
        for (String line : (List<String>) fontRenderer.listFormattedStringToWidth(paragraph, width)) {
            if (y > maxY) break;
            int start = paragraph.indexOf(line, from);
            if (start < 0) start = from;
            int end = Math.min(paragraph.length(), start + line.length());
            from = end;
            text(line, x, y, GuiWidgets.HINT);
            for (int[] s : spans) {
                int a = Math.max(start, s[0]), b = Math.min(end, s[1]);
                if (a >= b) continue;
                int x0 = x + fontRenderer.getStringWidth(paragraph.substring(start, a));
                int x1 = x + fontRenderer.getStringWidth(paragraph.substring(start, b));
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
            if (yes) ed.sign = true;
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
        for (GuiWidgets.Toggle t : ditherButtons) {
            if (t.hovered()) {
                String key = "minepapers.gui.ditherMode." + (t.id - ID_DITHER_MODE);
                titledTooltip(key, key + ".tip", mouseX, mouseY);
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
        if (ed.tab == TAB_FRAME && over(F_MOLDING, mouseX, mouseY)) {
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
            titledTooltip("minepapers.gui.nsfw", ed.nsfw ? "minepapers.gui.nsfw.on" : "minepapers.gui.nsfw.tip", mouseX, mouseY);
        } else if (btnSign.hovered()) {
            if (tile.isSigned()) {
                tooltip("minepapers.gui.signer", mouseX, mouseY, tile.signerName);
            } else if (ed.sign) {
                List<String> lines = new ArrayList<String>();
                lines.add(I18n.format("minepapers.gui.sign.pending", ed.myName));
                lines.add("§7" + I18n.format("minepapers.gui.sign.onClose"));
                drawHoveringText(lines, mouseX, mouseY, fontRenderer);
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
                drawHoveringText(lines, mouseX, mouseY, fontRenderer);
            }
        } else if (btnCopy.hovered()) {
            titledTooltip("minepapers.gui.copy", "minepapers.gui.copy.tip", mouseX, mouseY);
        } else if (btnPaste.hovered()) {
            titledTooltip("minepapers.gui.paste", "minepapers.gui.paste.tip", mouseX, mouseY);
        } else if (cbSharp.hovered()) {
            tooltip("minepapers.gui.sharp.tip", mouseX, mouseY);
        } else if (cbMapPalette.hovered()) {
            tooltip("minepapers.gui.mapPalette.tip", mouseX, mouseY);
        } else if (cbSizePx.hovered() || cbPosPx.hovered() || cbPadPx.hovered() || cbZoomPx.hovered()) {
            tooltip("minepapers.gui.pixels.tip", mouseX, mouseY);
        } else if (ed.tab == TAB_SIZE && over(F_WIDTH, mouseX, mouseY)) {
            tooltip("minepapers.gui.width", mouseX, mouseY);
        } else if (ed.tab == TAB_SIZE && over(F_HEIGHT, mouseX, mouseY)) {
            tooltip("minepapers.gui.height", mouseX, mouseY);
        } else if (ed.tab == TAB_SIZE && over(F_OFF_X, mouseX, mouseY)) {
            tooltip("minepapers.gui.shift.x", mouseX, mouseY);
        } else if (ed.tab == TAB_SIZE && over(F_OFF_Y, mouseX, mouseY)) {
            tooltip("minepapers.gui.shift.y", mouseX, mouseY);
        } else if (ed.tab == TAB_SIZE && over(F_PAD, mouseX, mouseY)) {
            tooltip("minepapers.gui.padding.tip", mouseX, mouseY);
        } else if (ed.tab == TAB_SIZE && over(F_ZOOM, mouseX, mouseY)) {
            tooltip("minepapers.gui.zoom.tip", mouseX, mouseY);
        } else if (btnReload.hovered()) {
            tooltip("minepapers.gui.reload", mouseX, mouseY);
        }
    }

    private boolean over(int f, int mouseX, int mouseY) {
        int[] r = fieldRect[f];
        return mouseX >= r[0] - 1 && mouseY >= r[1] - 1 && mouseX < r[0] + r[2] + 1 && mouseY < r[1] + r[3] + 1;
    }

    /** Название — заголовком, пояснение — серым под ним, как описание предмета. */
    private void titledTooltip(String titleKey, String tipKey, int x, int y) {
        List<String> lines = new ArrayList<String>();
        lines.add(I18n.format(titleKey));
        for (String s : I18n.format(tipKey).split("\\\\n")) lines.add("§7" + s);
        drawHoveringText(lines, x, y, fontRenderer);
    }

    private void tooltip(String key, int x, int y, Object... args) {
        List<String> lines = new ArrayList<String>(Arrays.asList(I18n.format(key, args).split("\\\\n")));
        drawHoveringText(lines, x, y, fontRenderer);
    }
}
