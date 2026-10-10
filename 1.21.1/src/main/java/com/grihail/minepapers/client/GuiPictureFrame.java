package com.grihail.minepapers.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.item.ItemPictureFrame;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class GuiPictureFrame extends Screen {

    /** Кнопка вида вместе с квадратной кнопкой текстуры рамки справа от неё. */
    private static final int MODE_BUTTON = 130;

    private static final int GUI_WIDTH = 360;
    private static final int COLUMN = 175;

    private final EntityPicture picture;
    private final PictureFrame tile;

    private int source, fitMode, style, mode;
    private String borderItem;
    private Button btnSource, btnPrev, btnNext, btnFolder, btnFit, btnStyle, btnMode, btnBorder;
    private Checkbox cbSizePx, cbPosPx;
    private EditBox fPath, fUrl, fFolder, fImage, fInterval, fWidth, fHeight, fOffX, fOffY, fPpb;
    private final List<EditBox> fields = new ArrayList<EditBox>();
    private int left, right, top;

    public GuiPictureFrame(EntityPicture picture) {
        super(Component.translatable("minepapers.gui.title"));
        this.picture = picture;
        this.tile = picture.frame;
        this.source = tile.source;
        this.fitMode = tile.fitMode;
        this.style = tile.style;
        this.mode = tile.mode;
        this.borderItem = tile.borderItem;
    }

    @Override
    protected void init() {
        // При изменении размера окна init вызывается снова — сохраняем то, что уже введено.
        List<String> old = null;
        if (!fields.isEmpty()) {
            old = new ArrayList<String>();
            for (EditBox f : fields) old.add(f.getValue());
        }
        boolean sizePx = cbSizePx != null ? cbSizePx.selected() : tile.sizeInPixels;
        boolean posPx = cbPosPx != null ? cbPosPx.selected() : tile.offsetInPixels;
        fields.clear();

        left = width / 2 - GUI_WIDTH / 2;
        right = left + GUI_WIDTH - COLUMN;
        top = height / 2 - 106;

        btnSource = button(left, top, GUI_WIDTH - MODE_BUTTON - 4, 20, Component.empty(), b -> {
            source = (source + 1) % PictureFrame.SOURCE_COUNT;
            updateWidgets();
        });
        btnMode = button(left + GUI_WIDTH - MODE_BUTTON, top, MODE_BUTTON - 24, 20, Component.empty(), b -> {
            mode = (mode + 1) % PictureFrame.MODE_COUNT;
            updateWidgets();
        });
        btnBorder = button(left + GUI_WIDTH - 20, top, 20, 20, Component.empty(), b -> toggleBorder());

        // Путь / ссылка (видно только поле текущего источника)
        fPath = field(left, top + 35, GUI_WIDTH - 46, 512, tile.localPath);
        btnPrev = button(left + GUI_WIDTH - 42, top + 34, 20, 20, Component.literal("<"), b -> cycleLocal(-1));
        btnNext = button(left + GUI_WIDTH - 20, top + 34, 20, 20, Component.literal(">"), b -> cycleLocal(1));
        fUrl = field(left, top + 35, GUI_WIDTH, 512, tile.yandexUrl);
        fFolder = field(left, top + 35, GUI_WIDTH, 512, tile.folderPath);
        fImage = field(left, top + 35, GUI_WIDTH, 512, tile.imageUrl);
        btnFolder = button(left, top + 57, 120, 20, Component.translatable("minepapers.gui.openFolder"),
            b -> Util.getPlatform().openFile(MinePapers.picturesDir.toFile()));

        // Левая колонка: размер, смещение, интервал
        fWidth = field(left, top + 95, 48, 10, fmt(tile.width));
        fHeight = field(left + 52, top + 95, 48, 10, fmt(tile.height));
        cbSizePx = addRenderableWidget(Checkbox.builder(Component.translatable("minepapers.gui.pixels"), font)
            .pos(left + 106, top + 95).selected(sizePx).onValueChange((cb, px) -> {
                convert(fWidth, px);
                convert(fHeight, px);
            }).build());
        fOffX = field(left, top + 131, 48, 10, fmt(tile.offsetX));
        fOffY = field(left + 52, top + 131, 48, 10, fmt(tile.offsetY));
        cbPosPx = addRenderableWidget(Checkbox.builder(Component.translatable("minepapers.gui.pixels"), font)
            .pos(left + 106, top + 131).selected(posPx).onValueChange((cb, px) -> {
                convert(fOffX, px);
                convert(fOffY, px);
            }).build());
        fInterval = field(left + COLUMN - 50, top + 153, 50, 6, String.valueOf(tile.interval));

        // Правая колонка: вписывание, стиль, разрешение
        btnFit = button(right, top + 94, COLUMN, 20, Component.empty(), b -> {
            fitMode = (fitMode + 1) % PictureFrame.FIT_COUNT;
            updateWidgets();
        });
        btnStyle = button(right, top + 130, COLUMN, 20, Component.empty(), b -> {
            style = (style + 1) % PictureFrame.STYLE_COUNT;
            updateWidgets();
        });
        fPpb = field(right + COLUMN - 50, top + 153, 50, 3, String.valueOf(tile.pixelsPerBlock));

        int bw = (GUI_WIDTH - 12) / 3;
        button(left, top + 192, bw, 20, Component.translatable("minepapers.gui.reload"), b -> reload());
        button(left + bw + 6, top + 192, bw, 20, Component.translatable("gui.done"), b -> save());
        button(left + 2 * (bw + 6), top + 192, bw, 20, Component.translatable("gui.cancel"), b -> onClose());

        if (old != null && old.size() == fields.size()) {
            for (int i = 0; i < fields.size(); i++) fields.get(i).setValue(old.get(i));
        }
        updateWidgets();
    }

    private Button button(int x, int y, int w, int h, Component text, Button.OnPress action) {
        return addRenderableWidget(Button.builder(text, action).bounds(x, y, w, h).build());
    }

    private EditBox field(int x, int y, int w, int maxLength, String text) {
        EditBox f = new EditBox(font, x, y, w, 18, Component.empty());
        f.setMaxLength(maxLength);
        f.setValue(text);
        fields.add(f);
        return addRenderableWidget(f);
    }

    private void updateWidgets() {
        boolean local = source == PictureFrame.SOURCE_LOCAL;
        btnSource.setMessage(Component.translatable("minepapers.gui.source." + source));
        btnPrev.visible = local;
        btnNext.visible = local;
        btnFolder.visible = local;
        btnFit.setMessage(Component.translatable("minepapers.gui.fit." + fitMode));
        btnStyle.setMessage(Component.translatable("minepapers.gui.style." + style));
        btnMode.setMessage(Component.translatable("minepapers.gui.mode." + mode));
        for (EditBox f : fields) {
            boolean active = isActive(f);
            f.setVisible(active);
            if (!active && getFocused() == f) setFocused(null);
        }
    }

    private boolean isActive(EditBox f) {
        if (f == fPath) return source == PictureFrame.SOURCE_LOCAL;
        if (f == fUrl) return source == PictureFrame.SOURCE_YANDEX;
        if (f == fFolder) return source == PictureFrame.SOURCE_FOLDER;
        if (f == fImage) return source == PictureFrame.SOURCE_URL;
        return true;
    }

    private String currentKey() {
        return PictureFrame.makeSourceKey(source, fPath.getValue(), fUrl.getValue(), fFolder.getValue(), fImage.getValue());
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
        int index = entries.indexOf(fPath.getValue().trim());
        if (index < 0) {
            index = dir > 0 ? 0 : entries.size() - 1;
        } else {
            index = (index + dir + entries.size()) % entries.size();
        }
        fPath.setValue(entries.get(index));
    }

    /** Текстура рамки: с предмета в руке, а повторное нажатие возвращает обычную. */
    private void toggleBorder() {
        if (!borderItem.isEmpty()) {
            borderItem = "";
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) return;
        borderItem = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
    }

    private void save() {
        tile.source = source;
        tile.localPath = fPath.getValue().trim();
        tile.yandexUrl = fUrl.getValue().trim();
        tile.folderPath = fFolder.getValue().trim();
        tile.imageUrl = fImage.getValue().trim();
        Float interval = parse(fInterval.getValue());
        if (interval != null) tile.interval = Math.round(interval);
        tile.sizeInPixels = cbSizePx.selected();
        float[] size = resolveSize(parse(fWidth.getValue()), parse(fHeight.getValue()));
        tile.width = size[0];
        tile.height = size[1];
        tile.offsetInPixels = cbPosPx.selected();
        tile.offsetX = orDefault(parse(fOffX.getValue()), tile.offsetX);
        tile.offsetY = orDefault(parse(fOffY.getValue()), tile.offsetY);
        tile.fitMode = fitMode;
        tile.style = style;
        tile.mode = mode;
        tile.borderItem = borderItem;
        Float ppb = parse(fPpb.getValue());
        if (ppb != null) tile.pixelsPerBlock = Math.round(ppb);

        // Прогоняем через те же ограничения, что и сервер.
        CompoundTag tag = new CompoundTag();
        tile.writeSettings(tag);
        tile.readSettings(tag);

        picture.refresh(); // хитбокс сразу по новому размеру, не дожидаясь сервера
        PacketDistributor.sendToServer(PacketSaveFrame.of(picture));
        // Файлы с этого компьютера другие игроки увидят, только когда они окажутся на сервере.
        ImageSync.offer(picture);
        onClose();
    }

    /**
     * Хитбокс должен быть известен серверу, поэтому "0 = авто" превращаем в конкретный размер
     * по пропорциям текущей картинки (если она ещё не загружена — квадрат). Округляем до пикселя.
     */
    private float[] resolveSize(Float w, Float h) {
        boolean px = cbSizePx.selected();
        float unit = px ? 16f : 1f;
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
        return new float[] { snap(w, px), snap(h, px) };
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
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focusNext();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Tab переключает только поля ввода текущего источника, как раньше. */
    private void focusNext() {
        List<EditBox> active = new ArrayList<EditBox>();
        for (EditBox f : fields) {
            if (isActive(f)) active.add(f);
        }
        int focused = active.indexOf(getFocused());
        setFocused(active.get((focused + 1) % active.size()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------- Отрисовка ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, top - 14, 0xFFFFFF);

        label(g, I18n.get("minepapers.gui.path." + source), left, top + 24);
        if (source == PictureFrame.SOURCE_YANDEX || source == PictureFrame.SOURCE_URL) {
            g.drawString(font, I18n.get("minepapers.gui.hint." + source), left, top + 60, 0x808080);
        }

        label(g, I18n.get("minepapers.gui.size"), left, top + 84);
        label(g, I18n.get("minepapers.gui.offset"), left, top + 120);
        label(g, I18n.get("minepapers.gui.interval"), left, top + 158);
        label(g, I18n.get("minepapers.gui.fit"), right, top + 84);
        label(g, I18n.get("minepapers.gui.style"), right, top + 120);
        label(g, I18n.get("minepapers.gui.ppb"), right, top + 158);

        g.drawString(font, font.plainSubstrByWidth(statusText(), GUI_WIDTH), left, top + 179, 0xFFFF80);
        drawBorderIcon(g);

        // Подсказки
        if (btnBorder.isHovered()) {
            borderTooltip(g, mouseX, mouseY);
        } else if (btnMode.isHovered()) {
            tooltip(g, "minepapers.gui.mode." + mode + ".tip", mouseX, mouseY);
        } else if (btnFit.isHovered()) {
            tooltip(g, "minepapers.gui.fit." + fitMode + ".tip", mouseX, mouseY);
        } else if (btnStyle.isHovered()) {
            tooltip(g, "minepapers.gui.style.tip", mouseX, mouseY);
        } else if (mouseX >= right && mouseX < right + COLUMN && mouseY >= top + 152 && mouseY < top + 171) {
            tooltip(g, "minepapers.gui.ppb.tip", mouseX, mouseY);
        } else if (hovered(cbSizePx) || hovered(cbPosPx)) {
            tooltip(g, "minepapers.gui.pixels.tip", mouseX, mouseY);
        }
    }

    private static boolean hovered(AbstractWidget widget) {
        return widget.visible && widget.isHovered();
    }

    private void tooltip(GuiGraphics g, String key, int x, int y, Object... args) {
        List<Component> lines = new ArrayList<Component>();
        for (String line : I18n.get(key, args).split("\n")) lines.add(Component.literal(line));
        g.renderComponentTooltip(font, lines, x, y);
    }

    /** На кнопке — предмет, из которого сделана рамка (или обычная рамка). */
    private void drawBorderIcon(GuiGraphics g) {
        ItemStack stack = ItemPictureFrame.borderStack(borderItem);
        if (stack == null) stack = new ItemStack(MinePapers.FRAME.get());
        try {
            g.renderItem(stack, btnBorder.getX() + 2, btnBorder.getY() + 2);
        } catch (Throwable ignored) {
            // Чужой предмет может не уметь рисоваться в интерфейсе — кнопка просто останется пустой.
        }
    }

    private void borderTooltip(GuiGraphics g, int x, int y) {
        ItemStack custom = ItemPictureFrame.borderStack(borderItem);
        if (!borderItem.isEmpty()) {
            tooltip(g, "minepapers.gui.border.custom.tip", x, y, custom != null ? name(custom) : borderItem);
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            tooltip(g, "minepapers.gui.border.empty.tip", x, y);
        } else {
            tooltip(g, "minepapers.gui.border.default.tip", x, y, name(held));
        }
    }

    private static String name(ItemStack stack) {
        try {
            return stack.getHoverName().getString();
        } catch (Throwable t) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
    }

    private void label(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x, y, 0xA0A0A0);
    }

    private String statusText() {
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
        if (s.getErrorKey() != null) return I18n.get("minepapers.gui.status.error", I18n.get(s.getErrorKey()));
        if (s.getErrorDetail() != null) return I18n.get("minepapers.gui.status.error", s.getErrorDetail());
        return I18n.get(key.startsWith("shared:") ? "minepapers.gui.status.shared" : "minepapers.gui.status.count",
            s.count());
    }
}
