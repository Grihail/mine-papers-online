package com.grihail.minepapers.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketPasteMaterials;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.Numbers;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.FrameHolder;
import com.grihail.minepapers.picture.Links;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Окно настройки картины: вкладки слева, под настройками — инвентарь игрока. Настройки применяются при закрытии окна. */
public class GuiPictureFrame extends AbstractContainerScreen<ContainerPictureFrame> {

    private static final int TAB_SOURCE = 0, TAB_SIZE = 1, TAB_STYLE = 2, TAB_FRAME = 3, TAB_INFO = 4, TAB_COUNT = 5;

    /** Ширина — как у сундука; низ окна — часть его текстуры с инвентарём, слоты в ней на 14 пикселей ниже её верха. */
    private static final int WIDTH = 176,
        HEIGHT = ContainerPictureFrame.INVENTORY_Y - GuiWidgets.INVENTORY_PART_SLOTS_Y + GuiWidgets.INVENTORY_PART_HEIGHT;

    /** Левый край кнопок вида рамки и холста (глаз и две кнопки в ряд, до правого края панели). */
    private static final int LOOK_X = WIDTH - 8 - GuiWidgets.Eye.WIDTH - 2 - 46;

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
    private boolean saved;
    /** Замок закрыт (картина заприватится при закрытии окна). */
    private boolean locked;
    /** Подписать картину при закрытии окна (подпись ставит сервер, и только на картину без привата). */
    private boolean sign;
    /** Открыто ванильное окно "Подписать картину?": окно картины к нему вернётся, поэтому не сохраняется и не закрывается. */
    private boolean confirming;
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
    /** Источник и вписывание — ряды кнопок с картинками; название и пояснение — в подсказке. */
    private final List<GuiWidgets.Toggle> sourceButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> fitButtons = new ArrayList<GuiWidgets.Toggle>();
    /** Рамка и холст — ряды кнопок справа от их слотов на вкладке "Рамка". */
    private final List<GuiWidgets.Toggle> frameButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> canvasButtons = new ArrayList<GuiWidgets.Toggle>();
    private GuiWidgets.Slider slColors, slDither, slPpb;
    /** Вкладка, на которой лежит виджет (у самих вкладок записи нет — они видны всегда). */
    private final Map<AbstractWidget, Integer> widgetTab = new HashMap<AbstractWidget, Integer>();
    private GuiWidgets.Small btnPrev, btnNext, btnFolder, btnReload, btnCopy, btnPaste;
    private GuiWidgets.Check cbSizePx, cbPosPx, cbPadPx, cbSharp, cbMapPalette;

    /** Путь и ссылки — многострочные поля (текст переносится), остальное — обычные. */
    private MultiLineEditBox fPath, fUrl, fFolder, fImage, fDesc;
    private EditBox fTitle, fInterval, fWidth, fHeight, fOffX, fOffY, fPad;
    /** Ширина багета в пикселях: 0 — багета нет. */
    private EditBox fMolding;
    /** Все поля по порядку: значения переживают пересоздание виджетов при изменении размера окна. */
    private final List<AbstractWidget> fields = new ArrayList<AbstractWidget>();

    private boolean statusError;

    public GuiPictureFrame(ContainerPictureFrame menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.holder = menu.holder;
        this.tile = holder.frame;
        this.source = tile.source;
        this.fitMode = tile.fitMode;
        this.sharp = tile.sharpPixels;
        this.mapPalette = tile.mapPalette;
        this.ppb = 1 << PictureFrame.ppbStep(tile.pixelsPerBlock);
        this.colors = tile.colors;
        this.dither = tile.dither;
        this.frameShape = tile.getFrameShape();
        this.canvasKind = tile.getCanvasKind();
        this.sizePx = tile.sizeInPixels;
        this.posPx = tile.offsetInPixels;
        this.padPx = tile.paddingInPixels;
        this.me = inventory.player.getUUID();
        this.myName = inventory.player.getGameProfile().getName();
        this.locked = tile.isLocked();
        this.nsfw = tile.nsfw;
        this.readOnly = !tile.mayEdit(me);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        // При изменении размера окна init вызывается снова — сохраняем то, что уже введено.
        List<String> old = null;
        if (!fields.isEmpty()) {
            old = new ArrayList<String>();
            for (AbstractWidget f : fields) old.add(valueOf(f));
        }
        super.init();
        fields.clear();
        tabs.clear();
        sourceButtons.clear();
        fitButtons.clear();
        frameButtons.clear();
        canvasButtons.clear();
        widgetTab.clear();

        int x = leftPos, y = topPos;
        for (int i = 0; i < TAB_COUNT; i++) {
            int index = i;
            tabs.add(addRenderableWidget(new GuiWidgets.Tab(x - GuiWidgets.Tab.WIDTH + GuiWidgets.Tab.OVERLAP,
                y + 4 + i * (GuiWidgets.Tab.HEIGHT + 1), i, Component.translatable("minepapers.gui.tab." + i),
                () -> selectTab(index))));
            tabs.get(i).setTooltip(tip("minepapers.gui.tab." + i));
        }
        // Справа сверху, вне окна — копировать и вставить все настройки как JSON (PictureConfig).
        btnCopy = addRenderableWidget(new GuiWidgets.Small(x + imageWidth + 2, y + 4, 20, 20,
            Component.translatable("minepapers.gui.copy"), GuiWidgets.ICON_COPY_U, this::copyConfig));
        btnCopy.setTooltip(tip("minepapers.gui.copy", "minepapers.gui.copy.tip"));
        btnPaste = addRenderableWidget(new GuiWidgets.Small(x + imageWidth + 2, y + 26, 20, 20,
            Component.translatable("minepapers.gui.paste"), GuiWidgets.ICON_PASTE_U, this::pasteConfig));
        btnPaste.setTooltip(tip("minepapers.gui.paste", "minepapers.gui.paste.tip"));
        // Ниже — подпись и замок привата. Чужой замок не нажимается, только показывает владельца.
        // Над ними — пометка NSFW: ставит и снимает любой, кто может менять картину.
        btnNsfw = addRenderableWidget(new GuiWidgets.Nsfw(x + imageWidth + 2, y + 82, () -> {
            nsfw = !nsfw;
            updateWidgets();
        }));
        btnSign = addRenderableWidget(new GuiWidgets.Sign(x + imageWidth + 2, y + 104, this::confirmSign));
        btnLock = addRenderableWidget(new GuiWidgets.Lock(x + imageWidth + 2, y + 126, () -> {
            locked = !locked;
            updateWidgets();
        }));
        btnLock.active = !readOnly;

        // Источник
        for (int i = 0; i < PictureFrame.SOURCE_COUNT; i++) {
            int value = i;
            sourceButtons.add(add(new GuiWidgets.Toggle(x + 8 + i * 24, y + 17, i, i * 16, GuiWidgets.SOURCE_ICON_V,
                Component.translatable("minepapers.gui.source." + i), () -> {
                    source = value;
                    updateWidgets();
                }), TAB_SOURCE));
            sourceButtons.get(i).setTooltip(tip("minepapers.gui.source." + i, "minepapers.gui.path." + i + ".tip"));
        }
        fPath = area(TAB_SOURCE, x + 8, y + 52, 46, tile.localPath);
        fUrl = area(TAB_SOURCE, x + 8, y + 52, 46, tile.yandexUrl);
        fFolder = area(TAB_SOURCE, x + 8, y + 52, 46, tile.folderPath);
        fImage = area(TAB_SOURCE, x + 8, y + 52, 46, tile.imageUrl);
        btnPrev = small(x + 8, y + 100, 14, 14, Component.literal("<"), -1, () -> cycleLocal(-1));
        btnNext = small(x + 24, y + 100, 14, 14, Component.literal(">"), -1, () -> cycleLocal(1));
        Component open = Component.translatable("minepapers.gui.openFolder");
        btnFolder = small(x + 40, y + 100, font.width(open) + 12, 14, open, -1,
            () -> Util.getPlatform().openFile(MinePapers.picturesDir.toFile()));
        // Интервал и состояние — внизу вкладки, над инвентарём
        fInterval = field(TAB_SOURCE, x + 128, y + 116, 40, 6, String.valueOf(tile.interval), Numbers.INTERVAL);
        btnReload = small(x + 154, y + 131, 14, 14, Component.translatable("minepapers.gui.reload"),
            GuiWidgets.ICON_RELOAD_U, this::reload);
        btnReload.setTooltip(tip("minepapers.gui.reload"));

        // Размер и позиция
        fWidth = field(TAB_SIZE, x + 8, y + 18, 42, 10, Numbers.fmt(tile.width), Numbers.SIZE);
        fHeight = field(TAB_SIZE, x + 62, y + 18, 42, 10, Numbers.fmt(tile.height), Numbers.SIZE);
        cbSizePx = add(new GuiWidgets.Check(x + 112, y + 20, Component.translatable("minepapers.gui.pixels"), sizePx, () -> {
            sizePx = !sizePx;
            convert(fWidth, sizePx);
            convert(fHeight, sizePx);
            updateWidgets();
        }), TAB_SIZE);
        fOffX = field(TAB_SIZE, x + 8, y + 48, 42, 10, Numbers.fmt(tile.offsetX), Numbers.OFFSET);
        fOffY = field(TAB_SIZE, x + 62, y + 48, 42, 10, Numbers.fmt(tile.offsetY), Numbers.OFFSET);
        cbPosPx = add(new GuiWidgets.Check(x + 112, y + 50, Component.translatable("minepapers.gui.pixels"), posPx, () -> {
            posPx = !posPx;
            convert(fOffX, posPx);
            convert(fOffY, posPx);
            updateWidgets();
        }), TAB_SIZE);
        fPad = field(TAB_SIZE, x + 8, y + 78, 42, 10, Numbers.fmt(tile.padding), Numbers.PADDING);
        fWidth.setTooltip(tip("minepapers.gui.width"));
        fHeight.setTooltip(tip("minepapers.gui.height"));
        fOffX.setTooltip(tip("minepapers.gui.shift.x"));
        fOffY.setTooltip(tip("minepapers.gui.shift.y"));
        fPad.setTooltip(tip("minepapers.gui.padding.tip"));
        cbPadPx = add(new GuiWidgets.Check(x + 112, y + 80, Component.translatable("minepapers.gui.pixels"), padPx, () -> {
            padPx = !padPx;
            convert(fPad, padPx);
            updateWidgets();
        }), TAB_SIZE);
        for (int i = 0; i < PictureFrame.FIT_COUNT; i++) {
            int value = i;
            // Внизу вкладки, над инвентарём
            fitButtons.add(add(new GuiWidgets.Toggle(x + 8 + i * 24, y + 127, i, i * 16, GuiWidgets.FIT_ICON_V,
                Component.translatable("minepapers.gui.fit." + i), () -> {
                    fitMode = value;
                    updateWidgets();
                }), TAB_SIZE));
            fitButtons.get(i).setTooltip(tip("minepapers.gui.fit." + i, "minepapers.gui.fit." + i + ".tip"));
        }

        // Стиль: резкие пиксели и палитра карт, под ними ползунки — число цветов (с палитрой не действует), сила дизеринга и пикселей на блок (степени двойки)
        cbSharp = add(new GuiWidgets.Check(x + 8, y + 18, Component.translatable("minepapers.gui.sharp"), sharp, () -> {
            sharp = !sharp;
            updateWidgets();
        }), TAB_STYLE);
        cbSharp.setTooltip(tip("minepapers.gui.sharp.tip"));
        cbMapPalette = add(new GuiWidgets.Check(x + 8, y + 31, Component.translatable("minepapers.gui.mapPalette"), mapPalette, () -> {
            mapPalette = !mapPalette;
            updateWidgets();
        }), TAB_STYLE);
        cbMapPalette.setTooltip(tip("minepapers.gui.mapPalette.tip"));
        slColors = add(new GuiWidgets.Slider(x + 8, y + 44, 160, PictureFrame.MIN_COLORS, PictureFrame.MAX_COLORS, 1, colors,
            v -> Component.translatable("minepapers.gui.colors", v), v -> colors = v), TAB_STYLE);
        slColors.setTooltip(tip("minepapers.gui.colors.tip"));
        slDither = add(new GuiWidgets.Slider(x + 8, y + 68, 160, 0, PictureFrame.MAX_DITHER, 5, dither,
            v -> Component.translatable("minepapers.gui.dither", v), v -> dither = v), TAB_STYLE);
        slDither.setTooltip(tip("minepapers.gui.dither.tip"));
        slPpb = add(new GuiWidgets.Slider(x + 8, y + 92, 160, 0, PictureFrame.PPB_STEPS, 1, PictureFrame.ppbStep(ppb),
            v -> Component.translatable("minepapers.gui.ppb", 1 << v), v -> ppb = 1 << v), TAB_STYLE);
        slPpb.setTooltip(tip("minepapers.gui.ppb.tip"));
        for (GuiWidgets.Check c : new GuiWidgets.Check[] { cbSizePx, cbPosPx, cbPadPx }) c.setTooltip(tip("minepapers.gui.pixels.tip"));

        // Рамка: справа от слотов рамки и холста — их вид. У рамки кнопки "нет" и "обшивка", за ними — ширина багета.
        int lookY = y + ContainerPictureFrame.MATERIAL_Y - 3;
        lookButtons(frameButtons, "minepapers.gui.frameShape.", PictureFrame.FRAME_MOLDING, 0, x, lookY, this::chooseFrame);
        fMolding = field(TAB_FRAME, x + LOOK_X + GuiWidgets.Eye.WIDTH + 2 + 24, lookY + 4, 22, 3,
            tile.getFrameShape() == PictureFrame.FRAME_MOLDING ? String.valueOf(tile.moldingWidth) : "0", Numbers.MOLDING);
        fMolding.setResponder(value -> moldingChanged());
        fMolding.setTooltip(tip("minepapers.gui.frameShape.2", "minepapers.gui.frameShape.2.tip"));
        lookButtons(canvasButtons, "minepapers.gui.canvasKind.", PictureFrame.CANVAS_COUNT, 1, x,
            lookY + ContainerPictureFrame.MATERIAL_STEP, value -> {
                canvasKind = value;
                frameShape = PictureFrame.frameShapeOf(look()); // без холста обшивки нет
            });

        // Описание
        fTitle = field(TAB_INFO, x + 8, y + 17, 160, PictureFrame.MAX_TITLE, tile.title);
        fDesc = area(TAB_INFO, x + 8, y + 47, 94, tile.description, true);

        if (old != null && old.size() == fields.size()) {
            for (int i = 0; i < fields.size(); i++) setValue(fields.get(i), old.get(i));
        }
        updateWidgets();
    }

    /** Ряд кнопок вида: первый — глаз "нет" (значение 0), за ним кнопки с иконками из ряда LOOK_ICON_V, начиная с иконки firstIcon. */
    private void lookButtons(List<GuiWidgets.Toggle> list, String key, int count, int firstIcon, int x, int y, IntConsumer choose) {
        for (int i = 0; i < count; i++) {
            int value = i;
            Runnable action = () -> {
                choose.accept(value);
                updateWidgets();
            };
            Component name = Component.translatable(key + i);
            list.add(add(i == 0 ? new GuiWidgets.Eye(x + LOOK_X - 4, y, 22, name, action) // чуть в стороне от кнопок
                : new GuiWidgets.Toggle(x + LOOK_X + GuiWidgets.Eye.WIDTH + 2 + (i - 1) * 24, y, i, (firstIcon + i - 1) * 16,
                    GuiWidgets.LOOK_ICON_V, name, action), TAB_FRAME));
            list.get(i).setTooltip(tip(key + i, key + i + ".tip"));
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
        fMolding.setValue("0");
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
        Float v = Numbers.parse(fMolding.getValue());
        return v == null ? 0 : Math.round(v);
    }

    /** Нужен ли выбранному в окне виду материал index (в порядке PictureFrame.materials(): рамка, холст, свечение). Ненужные блоки вернутся игроку при закрытии окна. */
    private boolean materialUsed(int index) {
        if (index == 0) return frameShape != PictureFrame.FRAME_NONE;
        if (index == 1) return canvasKind == PictureFrame.CANVAS_BLOCK;
        return true;
    }

    private boolean hasUnusedMaterials() {
        PictureFrame.Material[] materials = tile.materials();
        for (int i = 0; i < materials.length; i++) {
            if (!materials[i].stack().isEmpty() && !materialUsed(i)) return true;
        }
        return false;
    }

    private <T extends AbstractWidget> T add(T widget, int onTab) {
        widgetTab.put(widget, onTab);
        return addRenderableWidget(widget);
    }

    private GuiWidgets.Small small(int x, int y, int w, int h, Component text, int iconU, Runnable action) {
        return add(new GuiWidgets.Small(x, y, w, h, text, iconU, action), TAB_SOURCE);
    }

    private EditBox field(int onTab, int x, int y, int w, int maxLength, String text) {
        return field(onTab, x, y, w, maxLength, text, null);
    }

    /** number — числовое поле: набрать можно только число, стрелки вверх и вниз в поле и колёсико над ним меняют его на 1. */
    private EditBox field(int onTab, int x, int y, int w, int maxLength, String text, Numbers.Field number) {
        // Перетаскивание мышью выделяет текст: курсор идёт за мышью, другой конец выделения остаётся, где нажали.
        EditBox f = new EditBox(font, x, y, w, 14, Component.empty()) {
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
                step(this, number, dir);
                return true;
            }

            /** Колёсико над полем — и без клика по нему. */
            @Override
            public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
                if (number == null || scrollY == 0 || !isMouseOver(mouseX, mouseY)) return false;
                step(this, number, scrollY > 0 ? 1 : -1);
                return true;
            }
        };
        f.setMaxLength(maxLength);
        f.setValue(text);
        if (number != null) f.setFilter(number::accepts);
        fields.add(f);
        return add(f, onTab);
    }

    /** Шаг числа стрелкой или колёсиком: размер и отступ — в единицах, выбранных галочкой "px". */
    private void step(EditBox f, Numbers.Field number, int dir) {
        boolean px = f == fWidth || f == fHeight ? sizePx : f == fPad && padPx;
        f.setValue(number.step(f.getValue(), dir, px ? 16f : 1f));
    }

    /** Многострочное поле для пути, ссылки или описания: длинный текст переносится, его можно выделять мышью. */
    private MultiLineEditBox area(int onTab, int x, int y, int h, String text) {
        return area(onTab, x, y, h, text, false);
    }

    /** links — подчёркивать внешние ссылки (описание). */
    private MultiLineEditBox area(int onTab, int x, int y, int h, String text, boolean links) {
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
            public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
                return visible && super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
        };
        // Без ограничения длины: иначе под полем появляется счётчик символов и занимает место.
        a.setValue(text);
        fields.add(a);
        return add(a, onTab);
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

    /** Путь или ссылка без переводов строк (их можно вставить через Ctrl+V). */
    private static String line(MultiLineEditBox a) {
        return a.getValue().replace("\r", "").replace("\n", "").trim();
    }

    private void selectTab(int index) {
        tab = lastTab = index;
        updateWidgets();
    }

    private void updateWidgets() {
        for (Map.Entry<AbstractWidget, Integer> e : widgetTab.entrySet()) {
            AbstractWidget w = e.getKey();
            w.visible = !readOnly && e.getValue() == tab && isActive(w);
            if (!w.visible && getFocused() == w) setFocused(null);
        }
        for (GuiWidgets.Tab t : tabs) {
            t.selected = t.index == tab;
            t.visible = !readOnly;
        }
        btnCopy.visible = btnPaste.visible = !readOnly;
        btnLock.owner = !locked ? null : tile.owner != null ? tile.owner : me;
        btnLock.ownerName = !locked ? "" : tile.owner != null ? tile.ownerName : myName;
        btnLock.mine = !readOnly;
        if (btnLock.owner == null) {
            btnLock.setTooltip(tip("minepapers.gui.lock", "minepapers.gui.lock.tip"));
        } else {
            // Ник владельца, а ему самому — как снять приват.
            MutableComponent owner = Component.translatable("minepapers.gui.owner", btnLock.ownerName);
            if (!readOnly) owner.append("\n").append(Component.translatable("minepapers.gui.unlock.tip").withStyle(ChatFormatting.GRAY));
            btnLock.setTooltip(Tooltip.create(owner));
        }
        // Подписать можно только одну картину без привата и только один раз; подтверждённую подпись уже не отменить (поставится при закрытии окна, даже если закрыть и замок). Чужим подпись видна, если она есть.
        btnSign.signer = tile.signer != null ? tile.signer : sign ? me : null;
        btnSign.signerName = tile.signer != null ? tile.signerName : myName;
        btnSign.active = !readOnly && !tile.isSigned() && !sign && !locked && holder.count() == 1;
        btnSign.visible = !readOnly || tile.isSigned();
        btnNsfw.marked = nsfw;
        btnNsfw.active = !readOnly;
        btnNsfw.visible = !readOnly || nsfw;
        btnNsfw.setTooltip(tip("minepapers.gui.nsfw", nsfw ? "minepapers.gui.nsfw.on" : "minepapers.gui.nsfw.tip"));
        if (tile.isSigned()) {
            btnSign.setTooltip(Tooltip.create(Component.translatable("minepapers.gui.signer", tile.signerName)));
        } else if (sign) {
            btnSign.setTooltip(Tooltip.create(Component.translatable("minepapers.gui.sign.pending", myName).append("\n")
                .append(Component.translatable("minepapers.gui.sign.onClose").withStyle(ChatFormatting.GRAY))));
        } else if (holder.count() > 1) {
            btnSign.setTooltip(tip("minepapers.gui.sign", "minepapers.gui.sign.stack"));
        } else {
            btnSign.setTooltip(tip("minepapers.gui.sign", "minepapers.gui.sign.tip"));
        }
        for (GuiWidgets.Toggle t : sourceButtons) t.checked = t.value == source;
        for (GuiWidgets.Toggle t : fitButtons) t.checked = t.value == fitMode;
        for (GuiWidgets.Toggle t : frameButtons) t.checked = t.value == frameShape;
        for (GuiWidgets.Toggle t : canvasButtons) t.checked = t.value == canvasKind;
        cbSharp.checked = sharp;
        slColors.setInt(colors);
        slDither.setInt(dither);
        slPpb.setInt(PictureFrame.ppbStep(ppb));
        cbMapPalette.checked = mapPalette;
        // С палитрой карт цвета задаёт она — ползунок их числа не действует.
        slColors.active = !mapPalette;
        cbSizePx.checked = sizePx;
        cbPosPx.checked = posPx;
        cbPadPx.checked = padPx;
        // Слоты материалов есть всегда (их содержимое синхронизирует сервер), но видны и кликаются только на своей вкладке.
        menu.showMaterials(!readOnly && tab == TAB_FRAME);
    }

    /** Поле пути показываем только для выбранного источника, кнопки config/minepapers — только для него. */
    private boolean isActive(AbstractWidget w) {
        boolean local = source == PictureFrame.SOURCE_LOCAL;
        if (w == fPath || w == btnPrev || w == btnNext || w == btnFolder) return local;
        if (w == fUrl) return source == PictureFrame.SOURCE_YANDEX;
        if (w == fFolder) return source == PictureFrame.SOURCE_FOLDER;
        if (w == fImage) return source == PictureFrame.SOURCE_URL;
        return true;
    }

    private String currentKey() {
        return PictureFrame.makeSourceKey(source, line(fPath), line(fUrl), line(fFolder), line(fImage));
    }

    /** Что на самом деле показывает рамка: если источник не меняли и картинки уже на сервере — их. */
    private String shownKey() {
        String key = currentKey();
        return key != null && key.equals(tile.getOriginKey()) && tile.hasSharedFiles() ? tile.getSourceKey() : key;
    }

    /** Подпись навсегда — сначала ванильный вопрос "Да / Нет" (Esc — нет). */
    private void confirmSign() {
        confirming = true;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) sign = true;
            confirming = false;
            minecraft.setScreen(this);
        }, Component.translatable("minepapers.gui.sign.confirm"), Component.translatable("minepapers.gui.sign.confirm.text", myName)));
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
    private void convert(EditBox f, boolean toPixels) {
        Float v = Numbers.parse(f.getValue());
        if (v != null) {
            f.setValue(Numbers.fmt(toPixels ? v * 16f : v / 16f));
        }
    }

    private void cycleLocal(int dir) {
        List<String> entries = PictureManager.listLocalEntries();
        if (entries.isEmpty()) return;
        int index = entries.indexOf(line(fPath));
        if (index < 0) {
            index = dir > 0 ? 0 : entries.size() - 1;
        } else {
            index = (index + dir + entries.size()) % entries.size();
        }
        fPath.setValue(entries.get(index));
    }

    // ---------- Копировать и вставить ----------

    private MultiLineEditBox pathField(int src) {
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
        c.minecraft = SharedConstants.getCurrentVersion().getName();
        c.title = fTitle.getValue().trim();
        c.description = fDesc.getValue().trim();
        c.nsfw = nsfw;
        c.source = source;
        c.path = line(pathField(source));
        Float interval = Numbers.parse(fInterval.getValue());
        if (interval != null) c.interval = Math.round(interval);
        c.width = Numbers.parse(fWidth.getValue());
        c.height = Numbers.parse(fHeight.getValue());
        c.sizePixels = sizePx;
        c.offsetRight = Numbers.parse(fOffX.getValue());
        c.offsetUp = Numbers.parse(fOffY.getValue());
        c.offsetPixels = posPx;
        c.padding = Numbers.parse(fPad.getValue());
        c.paddingPixels = padPx;
        c.fit = fitMode;
        c.look = PictureFrame.classicLook(look());
        c.frame = frameShape;
        if (frameShape == PictureFrame.FRAME_MOLDING) c.moldingWidth = moldingWidth();
        c.canvas = canvasKind;
        c.sharpPixels = sharp;
        c.colors = colors;
        c.dither = dither;
        c.mapPalette = mapPalette;
        c.style = PictureFrame.legacyStyle(mapPalette, dither);
        c.pixelsPerBlock = ppb;
        PictureFrame.Material[] materials = tile.materials();
        for (int i = 0; i < materials.length && i < PictureConfig.MATERIALS.length; i++) {
            c.hasMaterial[i] = true;
            c.materials[i] = materials[i].stack().isEmpty() ? null : new PictureConfig.Item(materials[i].item, -1);
        }
        minecraft.keyboardHandler.setClipboard(c.toJson());
        chat(Component.translatable("minepapers.chat.copied").withStyle(ChatFormatting.GRAY));
    }

    /** Настройки из буфера обмена — в окно (применятся при закрытии, как и введённые руками); материалы сервер берёт из инвентаря (PacketPasteMaterials). */
    private void pasteConfig() {
        PictureConfig c;
        try {
            c = PictureConfig.fromJson(minecraft.keyboardHandler.getClipboard());
        } catch (IllegalArgumentException e) {
            chat(Component.translatable("minepapers.chat.badConfig").withStyle(ChatFormatting.RED));
            return;
        }
        if (c.title != null) fTitle.setValue(c.title);
        if (c.description != null) fDesc.setValue(c.description);
        if (c.nsfw != null) nsfw = c.nsfw;
        if (c.source != null) source = c.source;
        if (c.path != null) pathField(source).setValue(c.path);
        if (c.interval != null) fInterval.setValue(String.valueOf(c.interval));
        if (c.sizePixels != null) sizePx = c.sizePixels;
        if (c.width != null) fWidth.setValue(Numbers.fmt(c.width));
        if (c.height != null) fHeight.setValue(Numbers.fmt(c.height));
        if (c.offsetPixels != null) posPx = c.offsetPixels;
        if (c.offsetRight != null) fOffX.setValue(Numbers.fmt(c.offsetRight));
        if (c.offsetUp != null) fOffY.setValue(Numbers.fmt(c.offsetUp));
        if (c.paddingPixels != null) padPx = c.paddingPixels;
        if (c.padding != null) fPad.setValue(Numbers.fmt(c.padding));
        if (c.fit != null) fitMode = c.fit;
        if (c.look != null) {
            frameShape = PictureFrame.frameShapeOf(c.look);
            canvasKind = PictureFrame.canvasKindOf(c.look);
        }
        if (c.frame != null) frameShape = c.frame;
        if (c.canvas != null) canvasKind = c.canvas;
        frameShape = PictureFrame.frameShapeOf(look());
        // Багет без ширины (конфиг прежних версий) — обычной ширины.
        fMolding.setValue(frameShape != PictureFrame.FRAME_MOLDING ? "0" : String.valueOf(
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
        // Конфиг из старых версий: предметы там названы иначе (grass вместо grass_block, wool с номером цвета) — переводим.
        for (int i = 0; i < c.materials.length; i++) {
            c.materials[i] = c.translate(c.materials[i], true, id -> {
                ResourceLocation key = ResourceLocation.tryParse(id);
                return key != null && BuiltInRegistries.ITEM.containsKey(key);
            });
        }
        for (boolean has : c.hasMaterial) {
            if (has && menu.materialsEditable()) {
                PacketDistributor.sendToServer(PacketPasteMaterials.of(holder.target, c));
                break;
            }
        }
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
        CompoundTag before = new CompoundTag();
        tile.writeSettings(before);

        tile.title = fTitle.getValue().trim();
        tile.description = fDesc.getValue().trim();
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
        tile.localPath = line(fPath);
        tile.yandexUrl = line(fUrl);
        tile.folderPath = line(fFolder);
        tile.imageUrl = line(fImage);
        Float interval = Numbers.parse(fInterval.getValue());
        if (interval != null) tile.interval = Math.round(interval);
        tile.sizeInPixels = sizePx;
        float[] size = resolveSize(Numbers.parse(fWidth.getValue()), Numbers.parse(fHeight.getValue()));
        tile.width = size[0];
        tile.height = size[1];
        tile.offsetInPixels = posPx;
        tile.offsetX = Numbers.orDefault(Numbers.parse(fOffX.getValue()), tile.offsetX);
        tile.offsetY = Numbers.orDefault(Numbers.parse(fOffY.getValue()), tile.offsetY);
        tile.paddingInPixels = padPx;
        tile.padding = Numbers.orDefault(Numbers.parse(fPad.getValue()), tile.padding);
        tile.fitMode = fitMode;
        tile.sharpPixels = sharp;
        tile.mapPalette = mapPalette;
        tile.colors = colors;
        tile.dither = dither;
        tile.mode = look();
        if (frameShape == PictureFrame.FRAME_MOLDING) tile.moldingWidth = moldingWidth();
        tile.pixelsPerBlock = ppb;

        // Прогоняем через те же ограничения, что и сервер.
        CompoundTag tag = new CompoundTag();
        tile.writeSettings(tag);
        tile.readSettings(tag);
        CompoundTag after = new CompoundTag();
        tile.writeSettings(after);
        // Окно открыли и закрыли — серверу отправлять нечего, если только не надо вернуть ненужные виду блоки.
        if (after.equals(before) && !(menu.materialsEditable() && hasUnusedMaterials())) return;

        if (holder.picture != null) holder.picture.refresh(); // хитбокс сразу по новому размеру, не дожидаясь сервера
        PacketDistributor.sendToServer(PacketSaveFrame.of(holder));
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
            PictureManager.Picture p = s.slide(minecraft.level.getGameTime(), tile.interval, 0, tile.getSlideSeed());
            if (p != null && p.request() && p.getOrigWidth() > 0) {
                return (double) p.getOrigHeight() / p.getOrigWidth();
            }
        }
        return 1.0;
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
            && (fields.contains(w) || w instanceof GuiWidgets.Slider) && w.visible
            && focused.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** Shift+клик кладёт предмет в слот материала — только когда эти слоты видны. */
    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ClickType type) {
        if (type == ClickType.QUICK_MOVE && tab != TAB_FRAME && slot != null
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
        if (tab == TAB_FRAME) {
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
        if (tab != TAB_SIZE && tab != TAB_STYLE && tab != TAB_INFO) text(g, I18n.get("minepapers.gui.tab." + tab), 8, 6);
        switch (tab) {
            case TAB_SOURCE: {
                text(g, I18n.get("minepapers.gui.path." + source), 8, 42);
                text(g, I18n.get("minepapers.gui.interval"), 8, 119);
                String status = statusText();
                int y = 133;
                for (FormattedCharSequence line : font.split(Component.literal(status), 142)) {
                    if (y > 142) break;
                    g.drawString(font, line, 8, y, statusError ? GuiWidgets.ERROR : GuiWidgets.TEXT, false);
                    y += 9;
                }
                break;
            }
            case TAB_SIZE:
                text(g, I18n.get("minepapers.gui.size"), 8, 6);
                text(g, "x", 53, 21);
                text(g, I18n.get("minepapers.gui.offset"), 8, 36);
                text(g, I18n.get("minepapers.gui.padding"), 8, 66);
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
                    if (!materialUsed(i)) {
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
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
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
    private static Tooltip tip(String key) {
        return Tooltip.create(Component.translatable(key));
    }

    /** Название — заголовком, пояснение — серым под ним, как описание предмета. */
    private static Tooltip tip(String titleKey, String tipKey) {
        return Tooltip.create(Component.translatable(titleKey).append("\n")
            .append(Component.translatable(tipKey).withStyle(ChatFormatting.GRAY)));
    }

    private String statusText() {
        statusError = false;
        String key = currentKey();
        if (key == null) return I18n.get("minepapers.gui.status.none");
        int[] job = ImageSync.jobStatus(key);
        if (job != null) {
            String stage = job[0] == ImageSync.STAGE_PREPARING ? "preparing" : "uploading";
            return I18n.get("minepapers.gui.status." + stage, job[1], job[2]);
        }
        key = shownKey();
        PictureManager.Source s = PictureManager.peek(key);
        if (s == null) return I18n.get("minepapers.gui.status.unknown");
        if (s.isLoading()) return I18n.get("minepapers.gui.status.loading");
        if (s.getErrorKey() != null || s.getErrorDetail() != null) {
            statusError = true;
            String detail = s.getErrorKey() != null ? I18n.get(s.getErrorKey()) : s.getErrorDetail();
            return I18n.get("minepapers.gui.status.error", detail);
        }
        return I18n.get(key.startsWith("shared:") ? "minepapers.gui.status.shared" : "minepapers.gui.status.count",
            s.count());
    }
}
