package com.grihail.minepapers.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketPasteMaterials;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.ContainerPictureFrame;
import com.grihail.minepapers.picture.PictureConfig;
import com.grihail.minepapers.picture.EntityPicture;
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
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

    /** Последняя открытая вкладка — с неё окно откроется в следующий раз. */
    private static int lastTab = TAB_SOURCE;

    private final EntityPicture picture;
    private final PictureFrame tile;

    private int tab = lastTab;
    private int source, fitMode, style, mode;
    private boolean sizePx, posPx, padPx;
    private boolean saved;

    private final List<GuiWidgets.Tab> tabs = new ArrayList<GuiWidgets.Tab>();
    /** Источник и вписывание — ряды кнопок с картинками; название и пояснение — в подсказке. */
    private final List<GuiWidgets.Toggle> sourceButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Toggle> fitButtons = new ArrayList<GuiWidgets.Toggle>();
    private final List<GuiWidgets.Radio> modeRadios = new ArrayList<GuiWidgets.Radio>();
    private final List<GuiWidgets.Radio> styleRadios = new ArrayList<GuiWidgets.Radio>();
    /** Вкладка, на которой лежит виджет (у самих вкладок записи нет — они видны всегда). */
    private final Map<AbstractWidget, Integer> widgetTab = new HashMap<AbstractWidget, Integer>();
    private GuiWidgets.Small btnPrev, btnNext, btnFolder, btnReload, btnCopy, btnPaste;
    private GuiWidgets.Check cbSizePx, cbPosPx, cbPadPx;

    /** Путь и ссылки — многострочные поля (текст переносится), остальное — обычные. */
    private MultiLineEditBox fPath, fUrl, fFolder, fImage, fDesc;
    private EditBox fTitle, fInterval, fWidth, fHeight, fOffX, fOffY, fPad, fPpb;
    /** Все поля по порядку: значения переживают пересоздание виджетов при изменении размера окна. */
    private final List<AbstractWidget> fields = new ArrayList<AbstractWidget>();

    private boolean statusError;

    public GuiPictureFrame(ContainerPictureFrame menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.picture = menu.picture;
        this.tile = picture.frame;
        this.source = tile.source;
        this.fitMode = tile.fitMode;
        this.style = tile.style;
        this.mode = tile.mode;
        this.sizePx = tile.sizeInPixels;
        this.posPx = tile.offsetInPixels;
        this.padPx = tile.paddingInPixels;
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
        modeRadios.clear();
        styleRadios.clear();
        widgetTab.clear();

        int x = leftPos, y = topPos;
        for (int i = 0; i < TAB_COUNT; i++) {
            int index = i;
            tabs.add(addRenderableWidget(new GuiWidgets.Tab(x - GuiWidgets.Tab.WIDTH + GuiWidgets.Tab.OVERLAP,
                y + 4 + i * (GuiWidgets.Tab.HEIGHT + 1), i, Component.translatable("minepapers.gui.tab." + i),
                () -> selectTab(index))));
        }
        // Справа сверху, вне окна — копировать и вставить все настройки как JSON (PictureConfig).
        btnCopy = addRenderableWidget(new GuiWidgets.Small(x + imageWidth + 2, y + 4, 20, 20,
            Component.translatable("minepapers.gui.copy"), GuiWidgets.ICON_COPY_U, this::copyConfig));
        btnPaste = addRenderableWidget(new GuiWidgets.Small(x + imageWidth + 2, y + 26, 20, 20,
            Component.translatable("minepapers.gui.paste"), GuiWidgets.ICON_PASTE_U, this::pasteConfig));

        // Источник
        for (int i = 0; i < PictureFrame.SOURCE_COUNT; i++) {
            int value = i;
            sourceButtons.add(add(new GuiWidgets.Toggle(x + 8 + i * 24, y + 17, i, i * 16, GuiWidgets.SOURCE_ICON_V,
                Component.translatable("minepapers.gui.source." + i), () -> {
                    source = value;
                    updateWidgets();
                }), TAB_SOURCE));
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
        fInterval = field(TAB_SOURCE, x + 128, y + 116, 40, 6, String.valueOf(tile.interval));
        btnReload = small(x + 154, y + 131, 14, 14, Component.translatable("minepapers.gui.reload"),
            GuiWidgets.ICON_RELOAD_U, this::reload);

        // Размер и позиция
        fWidth = field(TAB_SIZE, x + 8, y + 30, 42, 10, fmt(tile.width));
        fHeight = field(TAB_SIZE, x + 62, y + 30, 42, 10, fmt(tile.height));
        cbSizePx = add(new GuiWidgets.Check(x + 112, y + 32, Component.translatable("minepapers.gui.pixels"), sizePx, () -> {
            sizePx = !sizePx;
            convert(fWidth, sizePx);
            convert(fHeight, sizePx);
            updateWidgets();
        }), TAB_SIZE);
        fOffX = field(TAB_SIZE, x + 8, y + 60, 42, 10, fmt(tile.offsetX));
        fOffY = field(TAB_SIZE, x + 62, y + 60, 42, 10, fmt(tile.offsetY));
        cbPosPx = add(new GuiWidgets.Check(x + 112, y + 62, Component.translatable("minepapers.gui.pixels"), posPx, () -> {
            posPx = !posPx;
            convert(fOffX, posPx);
            convert(fOffY, posPx);
            updateWidgets();
        }), TAB_SIZE);
        fPad = field(TAB_SIZE, x + 8, y + 90, 42, 10, fmt(tile.padding));
        cbPadPx = add(new GuiWidgets.Check(x + 112, y + 92, Component.translatable("minepapers.gui.pixels"), padPx, () -> {
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
        }

        // Вид и стиль
        for (int i = 0; i < PictureFrame.MODE_COUNT; i++) {
            int value = i;
            modeRadios.add(add(new GuiWidgets.Radio(x + 8, y + 29 + i * 11, 160, i,
                Component.translatable("minepapers.gui.mode." + i), () -> {
                    mode = value;
                    updateWidgets();
                }), TAB_STYLE));
        }
        for (int i = 0; i < PictureFrame.STYLE_COUNT; i++) {
            int value = i;
            styleRadios.add(add(new GuiWidgets.Radio(x + 8, y + 86 + i * 11, 160, i,
                Component.translatable("minepapers.gui.style." + i), () -> {
                    style = value;
                    updateWidgets();
                }), TAB_STYLE));
        }
        fPpb = field(TAB_STYLE, x + 128, y + 132, 40, 3, String.valueOf(tile.pixelsPerBlock));

        // Описание
        fTitle = field(TAB_INFO, x + 8, y + 29, 160, PictureFrame.MAX_TITLE, tile.title);
        fDesc = area(TAB_INFO, x + 8, y + 59, 82, tile.description);

        if (old != null && old.size() == fields.size()) {
            for (int i = 0; i < fields.size(); i++) setValue(fields.get(i), old.get(i));
        }
        updateWidgets();
    }

    private <T extends AbstractWidget> T add(T widget, int onTab) {
        widgetTab.put(widget, onTab);
        return addRenderableWidget(widget);
    }

    private GuiWidgets.Small small(int x, int y, int w, int h, Component text, int iconU, Runnable action) {
        return add(new GuiWidgets.Small(x, y, w, h, text, iconU, action), TAB_SOURCE);
    }

    private EditBox field(int onTab, int x, int y, int w, int maxLength, String text) {
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
        };
        f.setMaxLength(maxLength);
        f.setValue(text);
        fields.add(f);
        return add(f, onTab);
    }

    /** Многострочное поле для пути, ссылки или описания: длинный текст переносится, его можно выделять мышью. */
    private MultiLineEditBox area(int onTab, int x, int y, int h, String text) {
        // Поля всех источников лежат друг на друге, а ванильное поле принимает мышь и скрытым — отключаем это.
        MultiLineEditBox a = new MultiLineEditBox(font, x, y, 160, h, Component.empty(), Component.empty()) {
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
        // Без ограничения длины: иначе под полем появляется счётчик символов и занимает место. Длину обрезает сама картина (PictureFrame.MAX_STRING).
        a.setValue(text);
        fields.add(a);
        return add(a, onTab);
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
            w.visible = e.getValue() == tab && isActive(w);
            if (!w.visible && getFocused() == w) setFocused(null);
        }
        for (GuiWidgets.Tab t : tabs) t.selected = t.index == tab;
        for (GuiWidgets.Toggle t : sourceButtons) t.checked = t.value == source;
        for (GuiWidgets.Toggle t : fitButtons) t.checked = t.value == fitMode;
        for (GuiWidgets.Radio r : modeRadios) r.checked = r.value == mode;
        for (GuiWidgets.Radio r : styleRadios) r.checked = r.value == style;
        cbSizePx.checked = sizePx;
        cbPosPx.checked = posPx;
        cbPadPx.checked = padPx;
        // Слоты материалов есть всегда (их содержимое синхронизирует сервер), но видны и кликаются только на своей вкладке.
        menu.showMaterials(tab == TAB_FRAME);
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

    private void reload() {
        String key = currentKey();
        if (key == null) return;
        PictureManager.reload(key);
        PictureManager.get(key);
        // Перечитать папку и выложить на сервер то, что в ней появилось.
        if (key.equals(tile.getOriginKey())) {
            if (tile.hasSharedFiles()) PictureManager.reload(tile.getSourceKey());
            ImageSync.offer(picture);
        }
    }

    /** Переключение блоки <-> пиксели: пересчитываем значение, чтобы картинка не "прыгала". */
    private void convert(EditBox f, boolean toPixels) {
        Float v = parse(f.getValue());
        if (v != null) {
            f.setValue(fmt(toPixels ? v * 16f : v / 16f));
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
        c.source = source;
        c.path = line(pathField(source));
        Float interval = parse(fInterval.getValue());
        if (interval != null) c.interval = Math.round(interval);
        c.width = parse(fWidth.getValue());
        c.height = parse(fHeight.getValue());
        c.sizePixels = sizePx;
        c.offsetRight = parse(fOffX.getValue());
        c.offsetUp = parse(fOffY.getValue());
        c.offsetPixels = posPx;
        c.padding = parse(fPad.getValue());
        c.paddingPixels = padPx;
        c.fit = fitMode;
        c.look = mode;
        c.style = style;
        Float ppb = parse(fPpb.getValue());
        if (ppb != null) c.pixelsPerBlock = Math.round(ppb);
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
        if (c.source != null) source = c.source;
        if (c.path != null) pathField(source).setValue(c.path);
        if (c.interval != null) fInterval.setValue(String.valueOf(c.interval));
        if (c.sizePixels != null) sizePx = c.sizePixels;
        if (c.width != null) fWidth.setValue(fmt(c.width));
        if (c.height != null) fHeight.setValue(fmt(c.height));
        if (c.offsetPixels != null) posPx = c.offsetPixels;
        if (c.offsetRight != null) fOffX.setValue(fmt(c.offsetRight));
        if (c.offsetUp != null) fOffY.setValue(fmt(c.offsetUp));
        if (c.paddingPixels != null) padPx = c.paddingPixels;
        if (c.padding != null) fPad.setValue(fmt(c.padding));
        if (c.fit != null) fitMode = c.fit;
        if (c.look != null) mode = c.look;
        if (c.style != null) style = c.style;
        if (c.pixelsPerBlock != null) fPpb.setValue(String.valueOf(c.pixelsPerBlock));
        updateWidgets();
        // Конфиг из старых версий: предметы там названы иначе (grass вместо grass_block, wool с номером цвета) — переводим.
        for (int i = 0; i < c.materials.length; i++) {
            c.materials[i] = c.translate(c.materials[i], true, id -> {
                ResourceLocation key = ResourceLocation.tryParse(id);
                return key != null && BuiltInRegistries.ITEM.containsKey(key);
            });
        }
        for (boolean has : c.hasMaterial) {
            if (has) {
                PacketDistributor.sendToServer(PacketPasteMaterials.of(picture.getId(), c));
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
        if (!saved && minecraft.getConnection() != null && !picture.isRemoved()) save();
        saved = true;
        super.removed();
    }

    private void save() {
        CompoundTag before = new CompoundTag();
        tile.writeSettings(before);

        tile.title = fTitle.getValue().trim();
        tile.description = fDesc.getValue().trim();
        tile.source = source;
        tile.localPath = line(fPath);
        tile.yandexUrl = line(fUrl);
        tile.folderPath = line(fFolder);
        tile.imageUrl = line(fImage);
        Float interval = parse(fInterval.getValue());
        if (interval != null) tile.interval = Math.round(interval);
        tile.sizeInPixels = sizePx;
        float[] size = resolveSize(parse(fWidth.getValue()), parse(fHeight.getValue()));
        tile.width = size[0];
        tile.height = size[1];
        tile.offsetInPixels = posPx;
        tile.offsetX = orDefault(parse(fOffX.getValue()), tile.offsetX);
        tile.offsetY = orDefault(parse(fOffY.getValue()), tile.offsetY);
        tile.paddingInPixels = padPx;
        tile.padding = orDefault(parse(fPad.getValue()), tile.padding);
        tile.fitMode = fitMode;
        tile.style = style;
        tile.mode = mode;
        Float ppb = parse(fPpb.getValue());
        if (ppb != null) tile.pixelsPerBlock = Math.round(ppb);

        // Прогоняем через те же ограничения, что и сервер.
        CompoundTag tag = new CompoundTag();
        tile.writeSettings(tag);
        tile.readSettings(tag);
        CompoundTag after = new CompoundTag();
        tile.writeSettings(after);
        if (after.equals(before)) return; // окно открыли и закрыли — серверу отправлять нечего

        picture.refresh(); // хитбокс сразу по новому размеру, не дожидаясь сервера
        PacketDistributor.sendToServer(PacketSaveFrame.of(picture));
        // Файлы с этого компьютера другие игроки увидят, только когда они окажутся на сервере.
        ImageSync.offer(picture);
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
        return new float[] { snap(w, sizePx), snap(h, sizePx) };
    }

    private static float snap(float v, boolean px) {
        float pixels = Math.max(1, Math.round(px ? v : v * 16));
        return px ? pixels : pixels / 16f;
    }

    /** Высота / ширина картинки, которая сейчас показывается в рамке (1, если неизвестно). */
    private double currentAspect() {
        PictureManager.Source s = PictureManager.peek(shownKey());
        if (s != null) {
            PictureManager.Picture p = s.slide(picture.level().getGameTime(), tile.interval, 0, tile.getSlideSeed());
            if (p != null && p.request() && p.getOrigWidth() > 0) {
                return (double) p.getOrigHeight() / p.getOrigWidth();
            }
        }
        return 1.0;
    }

    private static float orDefault(Float v, float def) {
        return v != null ? v : def;
    }

    private static Float parse(String s) {
        try {
            float v = Float.parseFloat(s.trim().replace(',', '.'));
            return Float.isNaN(v) || Float.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(float v) {
        if (v == Math.round(v)) return String.valueOf(Math.round(v));
        String s = String.format(Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
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

    /** Перетаскивание мыши после клика в поле — выделение текста, а не раскладка предметов по слотам. */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        GuiEventListener focused = getFocused();
        if (isDragging() && button == 0 && focused instanceof AbstractWidget w && fields.contains(w) && w.visible
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
        text(g, I18n.get("minepapers.gui.tab." + tab), 8, 6);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GuiWidgets.TEXT, false);
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
                text(g, I18n.get("minepapers.gui.size"), 8, 18);
                text(g, "x", 53, 33);
                text(g, I18n.get("minepapers.gui.offset"), 8, 48);
                text(g, I18n.get("minepapers.gui.padding"), 8, 78);
                text(g, I18n.get("minepapers.gui.fit"), 8, 116);
                break;
            case TAB_STYLE:
                text(g, I18n.get("minepapers.gui.mode"), 8, 18);
                text(g, I18n.get("minepapers.gui.style"), 8, 75);
                text(g, I18n.get("minepapers.gui.ppb"), 8, 135);
                break;
            case TAB_INFO:
                text(g, I18n.get("minepapers.gui.title"), 8, 18);
                text(g, I18n.get("minepapers.gui.description"), 8, 48);
                break;
            case TAB_FRAME: {
                // Список материалов: слот, справа — что это и какой блок в нём лежит.
                PictureFrame.Material[] materials = tile.materials();
                for (int i = 0; i < materials.length; i++) {
                    String key = "minepapers.gui." + materials[i].key.toLowerCase(Locale.ROOT);
                    ItemStack stack = materials[i].stack();
                    String name = !stack.isEmpty() ? stack.getHoverName().getString() : I18n.get(key + ".default");
                    if (materials[i].light && !stack.isEmpty()) {
                        name += ", " + (PictureFrame.isGlowDust(stack) ? I18n.get("minepapers.gui.glow.self")
                            : I18n.get("minepapers.gui.glow.level", PictureFrame.lightOf(stack)));
                    }
                    int ty = ContainerPictureFrame.MATERIAL_Y + 1 + i * ContainerPictureFrame.MATERIAL_STEP;
                    text(g, I18n.get(key), 30, ty - 1);
                    g.drawString(font, font.plainSubstrByWidth(name, 138), 30, ty + 8, GuiWidgets.HINT, false);
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
        if (menu.getCarried().isEmpty() && hoveredSlot == null) drawTooltips(g, mouseX, mouseY);
    }

    private void drawTooltips(GuiGraphics g, int mouseX, int mouseY) {
        for (GuiWidgets.Tab t : tabs) {
            if (t.isHovered()) {
                tooltip(g, "minepapers.gui.tab." + t.index, mouseX, mouseY);
                return;
            }
        }
        for (GuiWidgets.Toggle t : sourceButtons) {
            if (t.visible && t.isHovered()) {
                titledTooltip(g, "minepapers.gui.source." + t.value, "minepapers.gui.path." + t.value + ".tip", mouseX, mouseY);
                return;
            }
        }
        for (GuiWidgets.Toggle t : fitButtons) {
            if (t.visible && t.isHovered()) {
                titledTooltip(g, "minepapers.gui.fit." + t.value, "minepapers.gui.fit." + t.value + ".tip", mouseX, mouseY);
                return;
            }
        }
        for (GuiWidgets.Radio r : modeRadios) {
            if (r.visible && r.isHovered()) {
                tooltip(g, "minepapers.gui.mode." + r.value + ".tip", mouseX, mouseY);
                return;
            }
        }
        for (GuiWidgets.Radio r : styleRadios) {
            if (r.visible && r.isHovered()) {
                tooltip(g, "minepapers.gui.style.tip", mouseX, mouseY);
                return;
            }
        }
        if (btnCopy.isHovered()) {
            titledTooltip(g, "minepapers.gui.copy", "minepapers.gui.copy.tip", mouseX, mouseY);
        } else if (btnPaste.isHovered()) {
            titledTooltip(g, "minepapers.gui.paste", "minepapers.gui.paste.tip", mouseX, mouseY);
        } else if (hovered(cbSizePx) || hovered(cbPosPx) || hovered(cbPadPx)) {
            tooltip(g, "minepapers.gui.pixels.tip", mouseX, mouseY);
        } else if (btnReload.visible && btnReload.isHovered()) {
            tooltip(g, "minepapers.gui.reload", mouseX, mouseY);
        } else if (hovered(fWidth)) {
            tooltip(g, "minepapers.gui.width", mouseX, mouseY);
        } else if (hovered(fHeight)) {
            tooltip(g, "minepapers.gui.height", mouseX, mouseY);
        } else if (hovered(fOffX)) {
            tooltip(g, "minepapers.gui.shift.x", mouseX, mouseY);
        } else if (hovered(fOffY)) {
            tooltip(g, "minepapers.gui.shift.y", mouseX, mouseY);
        } else if (hovered(fPad)) {
            tooltip(g, "minepapers.gui.padding.tip", mouseX, mouseY);
        } else if (hovered(fPpb)) {
            tooltip(g, "minepapers.gui.ppb.tip", mouseX, mouseY);
        }
    }

    private static boolean hovered(AbstractWidget w) {
        return w.visible && w.isHovered();
    }

    /** Строки перевода: перенос строки в словарях записан как \n. */
    private static String[] lines(String key) {
        return I18n.get(key).split("\\\\n|\n");
    }

    private void tooltip(GuiGraphics g, String key, int x, int y) {
        List<Component> lines = new ArrayList<Component>();
        for (String line : lines(key)) lines.add(Component.literal(line));
        g.renderComponentTooltip(font, lines, x, y);
    }

    /** Название — заголовком, пояснение — серым под ним, как описание предмета. */
    private void titledTooltip(GuiGraphics g, String titleKey, String tipKey, int x, int y) {
        List<Component> lines = new ArrayList<Component>();
        lines.add(Component.translatable(titleKey));
        for (String line : lines(tipKey)) lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        g.renderComponentTooltip(font, lines, x, y);
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
