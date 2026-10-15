package com.grihail.minepapers.client;

import static com.grihail.minepapers.client.FrameEditor.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketPasteMaterials;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.Links;
import com.grihail.minepapers.picture.Numbers;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Окно настройки картины: вкладки слева, под настройками — инвентарь игрока. Что выбрано и что с этим делать — в общем FrameEditor, здесь виджеты 1.21. Настройки применяются при закрытии окна. */
public class GuiPictureFrame extends AbstractContainerScreen<ContainerPictureFrame> {

    /** Ширина — как у сундука; низ окна — часть его текстуры с инвентарём, слоты в ней на 14 пикселей ниже её верха. */
    private static final int WIDTH = 176,
        HEIGHT = ContainerPictureFrame.INVENTORY_Y - GuiWidgets.INVENTORY_PART_SLOTS_Y + GuiWidgets.INVENTORY_PART_HEIGHT;

    /** Левый край кнопок вида рамки и холста (три кнопки в ряд, до правого края панели). */
    private static final int LOOK_X = WIDTH - 8 - 70;

    /** Картина-сущность или картина-предмет в руке. */
    private final FrameHolder holder;
    private final PictureFrame tile;
    private final Editor ed;
    private final boolean readOnly;

    private boolean saved;
    /** Открыто ванильное окно "Подписать картину?": окно картины к нему вернётся, поэтому не сохраняется и не закрывается. */
    private boolean confirming;
    private GuiWidgets.Lock btnLock;
    private GuiWidgets.Sign btnSign;
    private GuiWidgets.Nsfw btnNsfw;

    private final List<GuiWidgets.Tab> tabs = new ArrayList<GuiWidgets.Tab>();
    /** Источник и вписывание — ряды кнопок с картинками; название и пояснение — в подсказке. */
    private final List<GuiWidgets.Toggle> sourceButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> fitButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Вид дизеринга на вкладке "Стиль" — кнопки с иконками, по порядку PictureFrame.DITHER_*. */
    private final List<GuiWidgets.Toggle> ditherButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Рамка и холст — ряды кнопок справа от их слотов на вкладке "Рамка". */
    private final List<GuiWidgets.Toggle> frameButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> canvasButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Что светится — ряд кнопок справа от слота свечения: всё, только рамка, только картинка (value — PictureFrame.GLOW_*). */
    private final List<GuiWidgets.Toggle> glowButtons = new ArrayList<GuiWidgets.Toggle>();
    private GuiWidgets.Slider slColors, slDither, slPpb;
    /** Вкладка, на которой лежит виджет (у самих вкладок записи нет — они видны всегда). */
    private final Map<AbstractWidget, Integer> widgetTab = new HashMap<AbstractWidget, Integer>();
    private GuiWidgets.Small btnPrev, btnNext, btnFolder, btnReload, btnCopy, btnPaste;
    private GuiWidgets.Check cbSizePx, cbPosPx, cbPadPx, cbZoomPx, cbImagePx, cbSharp, cbMapPalette;

    /** Вкладка размера — ряды по одной настройке: подпись, одно или два поля, галочка "px". Верх полей рядов по порядку ROW_*; между отступом и масштабом — разделитель на SEPARATOR_Y: выше — область картинки на холсте, ниже — сама картинка в ней. */
    private static final int ROW_SIZE = 17, ROW_OFFSET = 33, ROW_PAD = 49, SEPARATOR_Y = 66, ROW_ZOOM = 70, ROW_IMAGE = 86;
    private static final String[] ROW_LABELS = SIZE_ROW_LABELS;
    private static final int[] ROW_Y = { ROW_SIZE, ROW_OFFSET, ROW_PAD, ROW_ZOOM, ROW_IMAGE };
    /** Левый край первого поля и ширина поля в рядах (от ширины подписей на текущем языке). */
    private int rowFieldX, rowFieldW;

    /** Поля ввода по номерам FrameEditor.F_*: путь, ссылки и описание — многострочные (текст переносится), остальные — обычные. */
    private final AbstractWidget[] fields = new AbstractWidget[FIELD_COUNT];
    private MultiLineEditBox fDesc;

    public GuiPictureFrame(ContainerPictureFrame menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.holder = menu.holder;
        this.tile = holder.frame;
        this.ed = new Editor(inventory);
        this.readOnly = ed.readOnly;
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = imageHeight - 94;
    }

    /** Общая логика окна с доступом к виджетам 1.21. */
    private final class Editor extends FrameEditor {

        Editor(Inventory inventory) {
            super(holder.frame, holder.target, inventory.player.getUUID(), inventory.player.getGameProfile().getName(),
                SharedConstants.getCurrentVersion().getName());
        }

        @Override
        protected String text(int field) {
            return valueOf(fields[field]);
        }

        @Override
        protected void setText(int field, String value) {
            setValue(fields[field], value);
        }

        @Override
        protected String tr(String key, Object... args) {
            return I18n.get(key, args);
        }

        @Override
        protected int count() {
            return holder.count();
        }

        @Override
        protected PictureConfig.Item material(int index) {
            PictureFrame.Material m = holder.frame.materials()[index];
            return m.stack().isEmpty() ? null : new PictureConfig.Item(m.item, -1);
        }

        @Override
        protected boolean itemExists(String id) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            return key != null && BuiltInRegistries.ITEM.containsKey(key);
        }

        @Override
        protected long worldTime() {
            return GuiPictureFrame.this.minecraft.level.getGameTime();
        }
    }

    @Override
    protected void init() {
        // При изменении размера окна init вызывается снова — сохраняем то, что уже введено.
        String[] old = null;
        if (fields[0] != null) {
            old = new String[FIELD_COUNT];
            for (int i = 0; i < FIELD_COUNT; i++) old[i] = valueOf(fields[i]);
        }
        super.init();
        tabs.clear();
        sourceButtons.clear();
        fitButtons.clear();
        ditherButtons.clear();
        frameButtons.clear();
        canvasButtons.clear();
        glowButtons.clear();
        widgetTab.clear();

        int x = leftPos, y = topPos;
        for (int i = 0; i < TAB_COUNT; i++) {
            int index = i;
            tabs.add(addRenderableWidget(new GuiWidgets.Tab(x - GuiWidgets.Tab.WIDTH + GuiWidgets.Tab.OVERLAP,
                y + 4 + i * (GuiWidgets.Tab.HEIGHT + 1), i, Component.translatable("minepapers.gui.tab." + i),
                () -> {
                    ed.selectTab(index);
                    updateWidgets();
                })));
            tabs.get(i).setTooltip(tip("minepapers.gui.tab." + i));
        }
        // Справа сверху, вне окна — копировать и вставить все настройки как JSON (PictureConfig).
        btnCopy = addRenderableWidget(new GuiWidgets.Small(x + imageWidth + 2, y + 4, 20, 20,
            Component.translatable("minepapers.gui.copy"), GuiWidgets.ICON_COPY_U, this::copyConfig));
        btnCopy.setTooltip(tip("minepapers.gui.copy", "minepapers.gui.copy.tip"));
        btnPaste = addRenderableWidget(new GuiWidgets.Small(x + imageWidth + 2, y + 26, 20, 20,
            Component.translatable("minepapers.gui.paste"), GuiWidgets.ICON_PASTE_U, this::pasteConfig));
        btnPaste.setTooltip(tip("minepapers.gui.paste", "minepapers.gui.paste.tip"));
        // Ниже — пометка NSFW (ставит и снимает любой, кто может менять картину), подпись и замок привата. Чужой замок не нажимается, только показывает владельца.
        btnNsfw = addRenderableWidget(new GuiWidgets.Nsfw(x + imageWidth + 2, y + 82, () -> {
            ed.nsfw = !ed.nsfw;
            updateWidgets();
        }));
        btnSign = addRenderableWidget(new GuiWidgets.Sign(x + imageWidth + 2, y + 104, this::confirmSign));
        btnLock = addRenderableWidget(new GuiWidgets.Lock(x + imageWidth + 2, y + 126, () -> {
            ed.locked = !ed.locked;
            updateWidgets();
        }));
        btnLock.active = !readOnly;

        // Источник
        for (int i = 0; i < PictureFrame.SOURCE_COUNT; i++) {
            int value = i;
            sourceButtons.add(add(new GuiWidgets.Toggle(x + 8 + i * 24, y + 17, i, i * 16, GuiWidgets.SOURCE_ICON_V,
                Component.translatable("minepapers.gui.source." + i), () -> {
                    ed.source = value;
                    updateWidgets();
                }), TAB_SOURCE));
            sourceButtons.get(i).setTooltip(tip("minepapers.gui.source." + i, "minepapers.gui.path." + i + ".tip"));
        }
        for (int f = F_PATH; f <= F_IMAGE; f++) area(f, x + 8, y + 52, 46, false);
        btnPrev = small(x + 8, y + 100, 14, 14, Component.literal("<"), -1, () -> ed.cycleLocal(-1));
        btnNext = small(x + 24, y + 100, 14, 14, Component.literal(">"), -1, () -> ed.cycleLocal(1));
        Component open = Component.translatable("minepapers.gui.openFolder");
        btnFolder = small(x + 40, y + 100, font.width(open) + 12, 14, open, -1,
            () -> Util.getPlatform().openFile(MinePapers.picturesDir.toFile()));
        // Интервал и состояние — внизу вкладки, над инвентарём
        field(F_INTERVAL, x + 128, y + 116, 40, 6);
        btnReload = small(x + 154, y + 131, 14, 14, Component.translatable("minepapers.gui.reload"),
            GuiWidgets.ICON_RELOAD_U, ed::reload);
        btnReload.setTooltip(tip("minepapers.gui.reload"));

        // Размер, смещение и отступ холста, масштаб и сдвиг картинки — по ряду на каждый: подписи слева, поля и галочки "px" — столбцами.
        int labelW = 0;
        for (String key : ROW_LABELS) labelW = Math.max(labelW, font.width(I18n.get(key)));
        int checkX = WIDTH - 8 - (13 + font.width(I18n.get("minepapers.gui.pixels")));
        rowFieldX = 8 + labelW + 4;
        rowFieldW = (checkX - 4 - rowFieldX - 10) / 2;
        int fx = x + rowFieldX, fx2 = fx + rowFieldW + 10;
        field(F_WIDTH, fx, y + ROW_SIZE, rowFieldW, 10);
        field(F_HEIGHT, fx2, y + ROW_SIZE, rowFieldW, 10);
        cbSizePx = pixels(x + checkX, y + ROW_SIZE + 2, ed::toggleSizePx);
        field(F_OFF_X, fx, y + ROW_OFFSET, rowFieldW, 10);
        field(F_OFF_Y, fx2, y + ROW_OFFSET, rowFieldW, 10);
        cbPosPx = pixels(x + checkX, y + ROW_OFFSET + 2, ed::togglePosPx);
        field(F_PAD, fx, y + ROW_PAD, rowFieldW, 10);
        cbPadPx = pixels(x + checkX, y + ROW_PAD + 2, ed::togglePadPx);
        field(F_ZOOM, fx, y + ROW_ZOOM, rowFieldW, 10);
        cbZoomPx = pixels(x + checkX, y + ROW_ZOOM + 2, ed::toggleZoomPx);
        field(F_IMAGE_X, fx, y + ROW_IMAGE, rowFieldW, 10);
        field(F_IMAGE_Y, fx2, y + ROW_IMAGE, rowFieldW, 10);
        cbImagePx = pixels(x + checkX, y + ROW_IMAGE + 2, ed::toggleImagePx);
        for (int f = 0; f < FIELD_COUNT; f++) {
            String arrows = FrameEditor.fieldArrows(f);
            if (arrows != null) fields[f].setTooltip(Tooltip.create(Component.literal(arrows)));
        }
        for (int i = 0; i < PictureFrame.FIT_COUNT; i++) {
            int value = i;
            // Внизу вкладки, над инвентарём
            fitButtons.add(add(new GuiWidgets.Toggle(x + 8 + i * 24, y + 127, i, i * 16, GuiWidgets.FIT_ICON_V,
                Component.translatable("minepapers.gui.fit." + i), () -> {
                    ed.fitMode = value;
                    updateWidgets();
                }), TAB_SIZE));
            fitButtons.get(i).setTooltip(tip("minepapers.gui.fit." + i, "minepapers.gui.fit." + i + ".tip"));
        }

        // Стиль: резкие пиксели и палитра карт, под ними ползунки — число цветов (с палитрой не действует), сила дизеринга и пикселей на блок
        cbSharp = add(new GuiWidgets.Check(x + 8, y + 18, Component.translatable("minepapers.gui.sharp"), ed.sharp, () -> {
            ed.sharp = !ed.sharp;
            updateWidgets();
        }), TAB_STYLE);
        cbSharp.setTooltip(tip("minepapers.gui.sharp.tip"));
        cbMapPalette = add(new GuiWidgets.Check(x + 8, y + 31, Component.translatable("minepapers.gui.mapPalette"), ed.mapPalette, () -> {
            ed.mapPalette = !ed.mapPalette;
            updateWidgets();
        }), TAB_STYLE);
        cbMapPalette.setTooltip(tip("minepapers.gui.mapPalette.tip"));
        slColors = add(new GuiWidgets.Slider(x + 8, y + 44, 160, PictureFrame.MIN_COLORS, PictureFrame.MAX_COLORS, (v, d) -> v + d, ed.colors,
            v -> Component.translatable("minepapers.gui.colors", v), v -> ed.colors = v), TAB_STYLE);
        slColors.setTooltip(tip("minepapers.gui.colors.tip"));
        for (int i = 0; i < PictureFrame.DITHER_MODE_COUNT; i++) {
            int value = i;
            ditherButtons.add(add(new GuiWidgets.Toggle(x + 8 + i * 24, y + 68, i, GuiWidgets.DITHER_ICON_U + i * 16,
                GuiWidgets.DITHER_ICON_V, Component.translatable("minepapers.gui.ditherMode." + i), () -> {
                    ed.ditherMode = value;
                    updateWidgets();
                }), TAB_STYLE));
            ditherButtons.get(i).setTooltip(tip("minepapers.gui.ditherMode." + i, "minepapers.gui.ditherMode." + i + ".tip"));
        }
        slDither = add(new GuiWidgets.Slider(x + 8, y + 92, 160, 0, PictureFrame.MAX_DITHER, (v, d) -> v + 5 * d, ed.dither,
            v -> Component.translatable("minepapers.gui.dither", v), v -> ed.dither = v), TAB_STYLE);
        slDither.setTooltip(tip("minepapers.gui.dither.tip"));
        slPpb = add(new GuiWidgets.Slider(x + 8, y + 116, 160, PictureFrame.MIN_PPB, PictureFrame.MAX_PPB, PictureFrame::ppbWheel, ed.ppb,
            v -> Component.translatable("minepapers.gui.ppb", v), v -> ed.ppb = v), TAB_STYLE);
        slPpb.setTooltip(tip("minepapers.gui.ppb.tip"));
        for (GuiWidgets.Check c : new GuiWidgets.Check[] { cbSizePx, cbPosPx, cbPadPx, cbZoomPx, cbImagePx }) c.setTooltip(tip("minepapers.gui.pixels.tip"));

        // Рамка: справа от слотов рамки и холста — их вид. У рамки кнопки "нет" и "обшивка", за ними — ширина багета.
        int lookY = y + ContainerPictureFrame.MATERIAL_Y - 3;
        lookButtons(frameButtons, "minepapers.gui.frameShape.", PictureFrame.FRAME_MOLDING, 0, x, lookY, ed::chooseFrame);
        EditBox molding = field(F_MOLDING, x + LOOK_X + 2 * 24, lookY + 4, 22, 3);
        molding.setResponder(value -> {
            ed.moldingChanged();
            updateWidgets();
        });
        molding.setTooltip(tip("minepapers.gui.frameShape.2", "minepapers.gui.frameShape.2.tip"));
        lookButtons(canvasButtons, "minepapers.gui.canvasKind.", PictureFrame.CANVAS_COUNT, 1, x,
            lookY + ContainerPictureFrame.MATERIAL_STEP, ed::chooseCanvas);
        for (int i = 0; i < PictureFrame.GLOW_PART_COUNT; i++) {
            int value = PictureFrame.GLOW_PART_COUNT - 1 - i;
            String key = "minepapers.gui.glowPart." + value;
            GuiWidgets.Toggle t = add(new GuiWidgets.Toggle(x + LOOK_X + i * 24, lookY + 2 * ContainerPictureFrame.MATERIAL_STEP, value,
                GuiWidgets.GLOW_ICON_U + i * 16, GuiWidgets.LOOK_ICON_V, Component.translatable(key), () -> {
                    ed.glowPart = value;
                    updateWidgets();
                }), TAB_FRAME);
            t.setTooltip(tip(key, key + ".tip"));
            glowButtons.add(t);
        }

        // Описание
        field(F_TITLE, x + 8, y + 17, 160, PictureFrame.MAX_TITLE);
        fDesc = area(F_DESC, x + 8, y + 47, 94, true);

        if (old != null) {
            for (int i = 0; i < FIELD_COUNT; i++) setValue(fields[i], old[i]);
        }
        updateWidgets();
    }

    /** Ряд кнопок вида: первая — крест "нет" (значение 0), за ним кнопки с иконками из ряда LOOK_ICON_V, начиная с иконки firstIcon. */
    private void lookButtons(List<GuiWidgets.Toggle> list, String key, int count, int firstIcon, int x, int y, IntConsumer choose) {
        for (int i = 0; i < count; i++) {
            int value = i;
            Runnable action = () -> {
                choose.accept(value);
                updateWidgets();
            };
            Component name = Component.translatable(key + i);
            list.add(add(i == 0 ? new GuiWidgets.Toggle(x + LOOK_X, y, i, GuiWidgets.NONE_ICON_U, GuiWidgets.NONE_ICON_V, name, action)
                : new GuiWidgets.Toggle(x + LOOK_X + i * 24, y, i, (firstIcon + i - 1) * 16, GuiWidgets.LOOK_ICON_V, name, action), TAB_FRAME));
            list.get(i).setTooltip(tip(key + i, key + i + ".tip"));
        }
    }

    private <T extends AbstractWidget> T add(T widget, int onTab) {
        widgetTab.put(widget, onTab);
        return addRenderableWidget(widget);
    }

    private GuiWidgets.Small small(int x, int y, int w, int h, Component text, int iconU, Runnable action) {
        return add(new GuiWidgets.Small(x, y, w, h, text, iconU, action), TAB_SOURCE);
    }

    /** Галочка "px" на вкладке размера. */
    private GuiWidgets.Check pixels(int x, int y, Runnable toggle) {
        return add(new GuiWidgets.Check(x, y, Component.translatable("minepapers.gui.pixels"), false, () -> {
            toggle.run();
            updateWidgets();
        }), TAB_SIZE);
    }

    /** Однострочное поле f. Числовое (FrameEditor.number): набрать можно только число, стрелки вверх и вниз в поле и колёсико над ним меняют его на шаг. */
    private EditBox field(int f, int x, int y, int w, int maxLength) {
        Numbers.Field number = FrameEditor.number(f);
        // Перетаскивание мышью выделяет текст: курсор идёт за мышью, другой конец выделения остаётся, где нажали.
        EditBox box = new EditBox(font, x, y, w, 14, Component.empty()) {
            private int anchor;

            @Override
            public void onClick(double mouseX, double mouseY) {
                super.onClick(mouseX, mouseY);
                if (!Screen.hasShiftDown()) anchor = getCursorPosition();
            }

            @Override
            protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
                super.onClick(Math.max(getX(), Math.min(getX() + getWidth() - 1, mouseX)), mouseY);
                setHighlightPos(anchor);
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                int dir = keyCode == GLFW.GLFW_KEY_UP ? 1 : keyCode == GLFW.GLFW_KEY_DOWN ? -1 : 0;
                if (number == null || dir == 0 || !canConsumeInput()) return super.keyPressed(keyCode, scanCode, modifiers);
                ed.step(f, dir);
                return true;
            }

            /** Колёсико над полем — и без клика по нему. */
            @Override
            //? if >=1.20.2 {
            public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            //?} else {
            /*public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
            *///?}
                if (number == null || scrollY == 0 || !isMouseOver(mouseX, mouseY)) return false;
                ed.step(f, scrollY > 0 ? 1 : -1);
                return true;
            }
        };
        box.setMaxLength(maxLength);
        box.setValue(ed.initialText(f));
        if (number != null) box.setFilter(number::accepts);
        fields[f] = box;
        return add(box, fieldTab(f));
    }

    /** Многострочное поле f для пути, ссылки или описания: длинный текст переносится, его можно выделять мышью. links — подчёркивать внешние ссылки (описание). */
    private MultiLineEditBox area(int f, int x, int y, int h, boolean links) {
        // Поля всех источников лежат друг на друге, а ванильное поле принимает мышь и скрытым — отключаем это.
        MultiLineEditBox a = new MultiLineEditBox(font, x, y, 160, h, Component.empty(), Component.empty()) {
            @Override
            protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
                super.renderContents(g, mouseX, mouseY, partialTick);
                if (links) underlineLinks(g, getValue(), getX() + innerPadding(), getY() + innerPadding(),
                    width - totalInnerPadding());
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return visible && super.mouseClicked(mouseX, mouseY, button);
            }

            @Override
            public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
                return visible && super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }

            @Override
            //? if >=1.20.2 {
            public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            //?} else {
            /*public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
            *///?}
                //? if >=1.20.2 {
                return visible && super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
                //?} else {
                /*return visible && super.mouseScrolled(mouseX, mouseY, scrollY);
                *///?}
            }
        };
        // Без ограничения длины: иначе под полем появляется счётчик символов и занимает место.
        a.setValue(ed.initialText(f));
        fields[f] = a;
        return add(a, fieldTab(f));
    }

    /** Подчёркивание ссылок поверх текста многострочного поля: строки переносятся так же, как в самом поле (MultilineTextField), и идут с шагом 9 пикселей. */
    private void underlineLinks(GuiGraphics g, String value, int x, int y, int width) {
        List<int[]> spans = Links.find(value);
        if (spans.isEmpty()) return;
        List<int[]> lines = new ArrayList<int[]>();
        font.getSplitter().splitLines(value, width, Style.EMPTY, false, (style, start, end) -> lines.add(new int[] { start, end }));
        for (int i = 0; i < lines.size(); i++) {
            int start = lines.get(i)[0], end = lines.get(i)[1];
            for (int[] s : spans) {
                int a = Math.max(start, s[0]), b = Math.min(end, s[1]);
                if (a >= b) continue;
                int x0 = x + font.width(value.substring(start, a)), x1 = x + font.width(value.substring(start, b));
                g.fill(x0, y + i * 9 + 8, x1, y + i * 9 + 9, 0xFFE0E0E0);
            }
        }
    }

    private static String valueOf(AbstractWidget f) {
        return f instanceof MultiLineEditBox a ? a.getValue() : ((EditBox) f).getValue();
    }

    private static void setValue(AbstractWidget f, String value) {
        if (f instanceof MultiLineEditBox a) {
            a.setValue(value);
        } else {
            ((EditBox) f).setValue(value);
        }
    }

    private int fieldIndex(AbstractWidget w) {
        for (int f = 0; f < FIELD_COUNT; f++) {
            if (fields[f] == w) return f;
        }
        return -1;
    }

    private void updateWidgets() {
        for (Map.Entry<AbstractWidget, Integer> e : widgetTab.entrySet()) {
            AbstractWidget w = e.getKey();
            w.visible = !readOnly && e.getValue() == ed.tab && isActive(w);
            if (!w.visible && getFocused() == w) setFocused(null);
        }
        for (GuiWidgets.Tab t : tabs) {
            t.selected = t.index == ed.tab;
            t.visible = !readOnly;
        }
        btnCopy.visible = btnPaste.visible = !readOnly;
        btnLock.owner = ed.lockOwner();
        btnLock.ownerName = ed.lockOwnerName();
        btnLock.mine = !readOnly;
        if (btnLock.owner == null) {
            btnLock.setTooltip(tip("minepapers.gui.lock", "minepapers.gui.lock.tip"));
        } else {
            // Ник владельца, а ему самому — как снять приват.
            MutableComponent owner = Component.translatable("minepapers.gui.owner", btnLock.ownerName);
            if (!readOnly) owner.append("\n").append(Component.translatable("minepapers.gui.unlock.tip").withStyle(ChatFormatting.GRAY));
            btnLock.setTooltip(Tooltip.create(owner));
        }
        // Чужим подпись видна, если она есть.
        btnSign.signer = ed.signer();
        btnSign.signerName = ed.signerName();
        btnSign.active = ed.canSign();
        btnSign.visible = !readOnly || tile.isSigned();
        btnNsfw.marked = ed.nsfw;
        btnNsfw.active = !readOnly;
        btnNsfw.visible = !readOnly || ed.nsfw;
        btnNsfw.setTooltip(tip("minepapers.gui.nsfw", ed.nsfw ? "minepapers.gui.nsfw.on" : "minepapers.gui.nsfw.tip"));
        if (tile.isSigned()) {
            btnSign.setTooltip(Tooltip.create(Component.translatable("minepapers.gui.signer", tile.signerName)));
        } else if (ed.sign) {
            btnSign.setTooltip(Tooltip.create(Component.translatable("minepapers.gui.sign.pending", ed.myName).append("\n")
                .append(Component.translatable("minepapers.gui.sign.onClose").withStyle(ChatFormatting.GRAY))));
        } else if (holder.count() > 1) {
            btnSign.setTooltip(tip("minepapers.gui.sign", "minepapers.gui.sign.stack"));
        } else {
            btnSign.setTooltip(tip("minepapers.gui.sign", "minepapers.gui.sign.tip"));
        }
        for (GuiWidgets.Toggle t : sourceButtons) t.checked = t.value == ed.source;
        for (GuiWidgets.Toggle t : fitButtons) t.checked = t.value == ed.fitMode;
        for (GuiWidgets.Toggle t : ditherButtons) t.checked = t.value == ed.ditherMode;
        for (GuiWidgets.Toggle t : frameButtons) t.checked = t.value == ed.frameShape;
        for (GuiWidgets.Toggle t : canvasButtons) t.checked = t.value == ed.canvasKind;
        for (GuiWidgets.Toggle t : glowButtons) t.checked = t.value == ed.glowPart;
        cbSharp.checked = ed.sharp;
        cbMapPalette.checked = ed.mapPalette;
        slColors.setInt(ed.colors);
        slColors.active = !ed.mapPalette; // с палитрой карт цвета задаёт она
        slDither.setInt(ed.dither);
        slPpb.setInt(ed.ppb);
        cbSizePx.checked = ed.sizePx;
        cbPosPx.checked = ed.posPx;
        cbPadPx.checked = ed.padPx;
        cbZoomPx.checked = ed.zoomPx;
        cbImagePx.checked = ed.imagePx;
        // Слоты материалов есть всегда (их содержимое синхронизирует сервер), но видны и кликаются только на своей вкладке.
        menu.showMaterials(!readOnly && ed.tab == TAB_FRAME);
    }

    /** Поле пути — только для выбранного источника, кнопки config/minepapers — только для него. */
    private boolean isActive(AbstractWidget w) {
        int f = fieldIndex(w);
        if (f >= 0) return ed.fieldShown(f);
        if (w == btnPrev || w == btnNext || w == btnFolder) return ed.source == PictureFrame.SOURCE_LOCAL;
        return true;
    }

    /** Подпись навсегда — сначала ванильный вопрос "Да / Нет" (Esc — нет). */
    private void confirmSign() {
        confirming = true;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) ed.sign = true;
            confirming = false;
            minecraft.setScreen(this);
        }, Component.translatable("minepapers.gui.sign.confirm"), Component.translatable("minepapers.gui.sign.confirm.text", ed.myName)));
    }

    // ---------- Копировать и вставить ----------

    /** Все настройки окна (как они введены сейчас) и материалы картины — в буфер обмена JSON-ом. */
    private void copyConfig() {
        minecraft.keyboardHandler.setClipboard(ed.copyConfig().toJson());
        chat(Component.translatable("minepapers.chat.copied").withStyle(ChatFormatting.GRAY));
    }

    /** Настройки из буфера обмена — в окно (применятся при закрытии, как и введённые руками); материалы сервер берёт из инвентаря (PacketPasteMaterials). */
    private void pasteConfig() {
        PictureConfig c = ed.paste(minecraft.keyboardHandler.getClipboard());
        if (c == null) {
            chat(Component.translatable("minepapers.chat.badConfig").withStyle(ChatFormatting.RED));
            return;
        }
        updateWidgets();
        if (c.anyMaterial() && menu.materialsEditable()) MinePapers.platform.sendToServer(PacketPasteMaterials.of(holder.target, c));
    }

    private void chat(Component text) {
        minecraft.player.displayClientMessage(text, false);
    }

    // ---------- Сохранение ----------

    /** Настройки применяются при любом закрытии окна: Esc, E, Enter или сервер закрыл его сам. */
    @Override
    public void removed() {
        if (confirming) return; // ушли в вопрос о подписи — окно ещё вернётся
        if (!saved && !readOnly && minecraft.getConnection() != null && holder.exists()) save();
        saved = true;
        super.removed();
    }

    private void save() {
        if (!ed.save(menu.materialsEditable())) return;
        if (holder.picture != null) holder.picture.refresh(); // хитбокс сразу по новому размеру, не дожидаясь сервера
        MinePapers.platform.sendToServer(PacketSaveFrame.of(holder));
        // Файлы с этого компьютера другие игроки увидят, только когда они окажутся на сервере.
        ImageSync.offer(holder.target, tile);
    }

    // ---------- Ввод ----------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose(); // сохранение — в removed()
            return true;
        }
        // Enter окно не закрывает: в описании он переносит строку, в остальных полях ничего не делает.
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            && !(getFocused() == fDesc && fDesc.visible && fDesc.isFocused())) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focusNext();
            return true;
        }
        // Пока набирают текст, E и цифры не должны закрывать окно и перекладывать предметы.
        GuiEventListener focused = getFocused();
        if (focused instanceof EditBox f && f.isVisible() && f.canConsumeInput()) {
            f.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (focused instanceof MultiLineEditBox a && a.visible && a.isFocused()) {
            a.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // E закрывает окно, цифры — обмен с хотбаром
    }

    /** Tab переключает только видимые поля ввода. */
    private void focusNext() {
        List<AbstractWidget> visible = new ArrayList<AbstractWidget>();
        for (AbstractWidget f : fields) {
            if (f.visible) visible.add(f);
        }
        if (visible.isEmpty()) return;
        int focused = visible.indexOf(getFocused());
        setFocused(visible.get((focused + 1) % visible.size()));
    }

    /** Перетаскивание мыши после клика в поле — выделение текста, по ползунку — его движение, а не раскладка предметов по слотам. */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        GuiEventListener focused = getFocused();
        if (isDragging() && button == 0 && focused instanceof AbstractWidget w
            && (fieldIndex(w) >= 0 || w instanceof GuiWidgets.Slider) && w.visible
            && focused.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** Shift+клик кладёт предмет в слот материала — только когда эти слоты видны. */
    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ClickType type) {
        if (type == ClickType.QUICK_MOVE && ed.tab != TAB_FRAME && slot != null
            && !ContainerPictureFrame.isMaterialSlot(slotId)) return;
        super.slotClicked(slot, slotId, button, type);
    }

    // ---------- Отрисовка ----------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (readOnly) {
            GuiWidgets.panel(g, leftPos, topPos, imageWidth, imageHeight);
            return;
        }
        // Как в творческом режиме: невыбранные вкладки уходят под окно, выбранная лежит поверх и сливается с ним.
        for (GuiWidgets.Tab t : tabs) {
            if (!t.selected) t.drawBody(g);
        }
        GuiWidgets.panel(g, leftPos, topPos, imageWidth, imageHeight); // слоты инвентаря — часть ванильной текстуры
        for (GuiWidgets.Tab t : tabs) {
            if (t.selected) t.drawBody(g);
        }
        for (GuiWidgets.Tab t : tabs) t.drawIcon(g);
        if (ed.tab == TAB_FRAME) {
            for (int i = 0; i < tile.materials().length; i++) {
                GuiWidgets.slot(g, leftPos + ContainerPictureFrame.MATERIAL_X - 1,
                    topPos + ContainerPictureFrame.MATERIAL_Y - 1 + i * ContainerPictureFrame.MATERIAL_STEP);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiWidgets.TEXT, false);
        if (readOnly) {
            // Чужая заприваченная картина: заголовок и под ним описание, только для чтения.
            int y = 6;
            String title = tile.title.isEmpty() ? I18n.get("minepapers.gui.untitled") : tile.title;
            for (FormattedCharSequence line : font.split(Component.literal(title), 160)) {
                g.drawString(font, line, 8, y, GuiWidgets.TEXT, false);
                y += 10;
            }
            y += 4;
            descY = y;
            descLines.clear();
            for (FormattedCharSequence line : font.split(withLinks(tile.description), 160)) {
                if (y > inventoryLabelY - 12) break;
                g.drawString(font, line, 8, y, GuiWidgets.HINT, false);
                descLines.add(line);
                y += 10;
            }
            return;
        }
        int tab = ed.tab;
        if (tab != TAB_SIZE && tab != TAB_STYLE && tab != TAB_INFO) text(g, I18n.get("minepapers.gui.tab." + tab), 8, 6);
        switch (tab) {
            case TAB_SOURCE: {
                text(g, I18n.get("minepapers.gui.path." + ed.source), 8, 42);
                text(g, I18n.get("minepapers.gui.interval"), 8, 119);
                String status = ed.statusText();
                int y = 133;
                for (FormattedCharSequence line : font.split(Component.literal(status), 142)) {
                    if (y > 142) break;
                    g.drawString(font, line, 8, y, ed.statusError ? GuiWidgets.ERROR : GuiWidgets.TEXT, false);
                    y += 9;
                }
                break;
            }
            case TAB_SIZE:
                text(g, I18n.get("minepapers.gui.tab." + tab), 8, 6);
                for (int i = 0; i < ROW_LABELS.length; i++) text(g, I18n.get(ROW_LABELS[i]), 8, ROW_Y[i] + 3);
                text(g, "x", rowFieldX + rowFieldW + 3, ROW_SIZE + 3);
                g.fill(8, SEPARATOR_Y, WIDTH - 8, SEPARATOR_Y + 1, 0xFF8B8B8B);
                text(g, I18n.get("minepapers.gui.fit"), 8, 116);
                break;
            case TAB_STYLE:
                text(g, I18n.get("minepapers.gui.style"), 8, 6);
                break;
            case TAB_INFO:
                text(g, I18n.get("minepapers.gui.titleLabel"), 8, 6);
                text(g, I18n.get("minepapers.gui.description"), 8, 36);
                break;
            case TAB_FRAME: {
                // Список материалов: слот и что это (какой блок в слоте — в подсказке самого блока), у свечения — сила света. Слот, ненужный выбранному виду, бледный: его блок вернётся при закрытии окна.
                PictureFrame.Material[] materials = tile.materials();
                for (int i = 0; i < materials.length; i++) {
                    int ty = ContainerPictureFrame.MATERIAL_Y + 1 + i * ContainerPictureFrame.MATERIAL_STEP;
                    String label = I18n.get("minepapers.gui.row." + materials[i].key.toLowerCase(Locale.ROOT));
                    if (!materials[i].light) {
                        text(g, label, 30, ty + 3);
                    } else {
                        ItemStack glow = materials[i].stack();
                        text(g, label, 30, ty - 1);
                        g.drawString(font, glow.isEmpty() ? I18n.get("minepapers.gui.glow.default") : PictureFrame.isGlowDust(glow)
                            ? I18n.get("minepapers.gui.glow.self") : I18n.get("minepapers.gui.glow.level", PictureFrame.lightOf(glow)),
                            30, ty + 8, GuiWidgets.HINT, false);
                    }
                    if (!ed.materialUsed(i)) {
                        g.fill(RenderType.guiOverlay(), ContainerPictureFrame.MATERIAL_X, ty - 1, ContainerPictureFrame.MATERIAL_X + 16,
                            ty + 15, 0xB0C6C6C6);
                    }
                }
                if (!menu.materialsEditable()) {
                    int y = ContainerPictureFrame.MATERIAL_Y + materials.length * ContainerPictureFrame.MATERIAL_STEP + 2;
                    for (FormattedCharSequence line : font.split(Component.translatable("minepapers.gui.materials.stack"), 160)) {
                        g.drawString(font, line, 8, y, GuiWidgets.HINT, false);
                        y += 10;
                    }
                }
                break;
            }
        }
    }

    private void text(GuiGraphics g, String s, int x, int y) {
        g.drawString(font, s, x, y, GuiWidgets.TEXT, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if <1.20.2 {
        /*renderBackground(g); // с 1.20.2 затемнение рисует сам super.render
        *///?}
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (!readOnly && ed.tab == TAB_SIZE) sizeLabelTooltip(g, mouseX, mouseY);
        Style link = linkAt(mouseX, mouseY);
        if (link != null) g.renderComponentHoverEffect(font, link, mouseX, mouseY);
    }

    // ---------- Ссылки в описании ----------

    /** Строки описания в окне чужой картины (для кликов по ссылкам) и y первой из них в окне. */
    private final List<FormattedCharSequence> descLines = new ArrayList<FormattedCharSequence>();
    private int descY;

    /** Описание, в котором ссылки подчёркнуты и кликабельны: клик открывает ванильный вопрос "Открыть ссылку?" (как в чате). */
    private static Component withLinks(String text) {
        MutableComponent result = Component.empty();
        int at = 0;
        for (int[] s : Links.find(text)) {
            result.append(text.substring(at, s[0]));
            String url = text.substring(s[0], s[1]);
            result.append(Component.literal(url).withStyle(style -> style.withUnderlined(true)
                .withColor(GuiWidgets.HOVER)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url)))));
            at = s[1];
        }
        return result.append(text.substring(at));
    }

    /** Ссылка под мышью (только в окне чужой картины) или null. */
    private Style linkAt(double mouseX, double mouseY) {
        if (!readOnly) return null;
        int row = (int) Math.floor((mouseY - topPos - descY) / 10);
        if (row < 0 || row >= descLines.size() || mouseY - topPos - descY - row * 10 >= 9) return null;
        Style style = font.getSplitter().componentStyleAtWidth(descLines.get(row), (int) (mouseX - leftPos - 8));
        return style != null && style.getClickEvent() != null ? style : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Style link = button == 0 ? linkAt(mouseX, mouseY) : null;
        if (link != null && handleComponentClicked(link)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Ванильная подсказка виджета. */
    /** Подсказка подписи ряда на вкладке размера: как работает параметр. */
    private void sizeLabelTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int mx = mouseX - leftPos, my = mouseY - topPos;
        for (int i = 0; i < ROW_LABELS.length; i++) {
            if (mx >= 8 && mx < 8 + font.width(I18n.get(ROW_LABELS[i])) && my >= ROW_Y[i] + 2 && my < ROW_Y[i] + 12) {
                List<Component> lines = new ArrayList<Component>();
                lines.add(Component.translatable(ROW_LABELS[i]));
                for (String s : I18n.get(ROW_LABELS[i] + ".tip").split("\n")) lines.add(Component.literal(s).withStyle(ChatFormatting.GRAY));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    private static Tooltip tip(String key) {
        return Tooltip.create(Component.translatable(key));
    }

    /** Название — заголовком, пояснение — серым под ним, как описание предмета. */
    private static Tooltip tip(String titleKey, String tipKey) {
        return Tooltip.create(Component.translatable(titleKey).append("\n")
            .append(Component.translatable(tipKey).withStyle(ChatFormatting.GRAY)));
    }
}
