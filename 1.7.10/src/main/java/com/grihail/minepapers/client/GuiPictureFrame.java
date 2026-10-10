package com.grihail.minepapers.client;

import java.awt.Desktop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.network.PacketSaveFrame;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.PictureFrame;

import cpw.mods.fml.client.config.GuiCheckBox;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class GuiPictureFrame extends GuiScreen {

    private static final int ID_SOURCE = 0, ID_PREV = 1, ID_NEXT = 2, ID_FOLDER = 3, ID_RELOAD = 4,
        ID_DONE = 5, ID_CANCEL = 6, ID_SIZE_PX = 7, ID_POS_PX = 8, ID_FIT = 9, ID_STYLE = 10, ID_MODE = 11,
        ID_BORDER = 12;
    /** Кнопка вида вместе с квадратной кнопкой текстуры рамки справа от неё. */
    private static final int MODE_BUTTON = 130;

    private static final int GUI_WIDTH = 360;
    private static final int COLUMN = 175;

    private final EntityPicture picture;
    private final PictureFrame tile;

    private int source, fitMode, style, mode;
    private String borderItem;
    private int borderMeta;
    private GuiButton btnSource, btnPrev, btnNext, btnFolder, btnFit, btnStyle, btnMode, btnBorder;
    private GuiCheckBox cbSizePx, cbPosPx;
    private GuiTextField fPath, fUrl, fFolder, fImage, fInterval, fWidth, fHeight, fOffX, fOffY, fPpb;
    private final List<GuiTextField> fields = new ArrayList<GuiTextField>();
    private int left, right, top;

    public GuiPictureFrame(EntityPicture picture) {
        this.picture = picture;
        this.tile = picture.frame;
        this.source = tile.source;
        this.fitMode = tile.fitMode;
        this.style = tile.style;
        this.mode = tile.mode;
        this.borderItem = tile.borderItem;
        this.borderMeta = tile.borderMeta;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        // При изменении размера окна initGui вызывается снова — сохраняем то, что уже введено.
        List<String> old = null;
        if (!fields.isEmpty()) {
            old = new ArrayList<String>();
            for (GuiTextField f : fields) old.add(f.getText());
        }
        boolean sizePx = cbSizePx != null ? cbSizePx.isChecked() : tile.sizeInPixels;
        boolean posPx = cbPosPx != null ? cbPosPx.isChecked() : tile.offsetInPixels;
        fields.clear();

        left = width / 2 - GUI_WIDTH / 2;
        right = left + GUI_WIDTH - COLUMN;
        top = height / 2 - 106;

        btnSource = addButton(new GuiButton(ID_SOURCE, left, top, GUI_WIDTH - MODE_BUTTON - 4, 20, ""));
        btnMode = addButton(new GuiButton(ID_MODE, left + GUI_WIDTH - MODE_BUTTON, top, MODE_BUTTON - 24, 20, ""));
        btnBorder = addButton(new GuiButton(ID_BORDER, left + GUI_WIDTH - 20, top, 20, 20, ""));

        // Путь / ссылка (видно только поле текущего источника)
        fPath = field(left, top + 35, GUI_WIDTH - 46, 512, tile.localPath);
        btnPrev = addButton(new GuiButton(ID_PREV, left + GUI_WIDTH - 42, top + 34, 20, 20, "<"));
        btnNext = addButton(new GuiButton(ID_NEXT, left + GUI_WIDTH - 20, top + 34, 20, 20, ">"));
        fUrl = field(left, top + 35, GUI_WIDTH, 512, tile.yandexUrl);
        fFolder = field(left, top + 35, GUI_WIDTH, 512, tile.folderPath);
        fImage = field(left, top + 35, GUI_WIDTH, 512, tile.imageUrl);
        btnFolder = addButton(new GuiButton(ID_FOLDER, left, top + 57, 120, 20, I18n.format("minepapers.gui.openFolder")));

        // Левая колонка: размер, смещение, интервал
        fWidth = field(left, top + 95, 48, 10, fmt(tile.width));
        fHeight = field(left + 52, top + 95, 48, 10, fmt(tile.height));
        cbSizePx = (GuiCheckBox) addButton(new GuiCheckBox(ID_SIZE_PX, left + 106, top + 99,
            I18n.format("minepapers.gui.pixels"), sizePx));
        fOffX = field(left, top + 131, 48, 10, fmt(tile.offsetX));
        fOffY = field(left + 52, top + 131, 48, 10, fmt(tile.offsetY));
        cbPosPx = (GuiCheckBox) addButton(new GuiCheckBox(ID_POS_PX, left + 106, top + 135,
            I18n.format("minepapers.gui.pixels"), posPx));
        fInterval = field(left + COLUMN - 50, top + 153, 50, 6, String.valueOf(tile.interval));

        // Правая колонка: вписывание, стиль, разрешение
        btnFit = addButton(new GuiButton(ID_FIT, right, top + 94, COLUMN, 20, ""));
        btnStyle = addButton(new GuiButton(ID_STYLE, right, top + 130, COLUMN, 20, ""));
        fPpb = field(right + COLUMN - 50, top + 153, 50, 3, String.valueOf(tile.pixelsPerBlock));

        int bw = (GUI_WIDTH - 12) / 3;
        addButton(new GuiButton(ID_RELOAD, left, top + 192, bw, 20, I18n.format("minepapers.gui.reload")));
        addButton(new GuiButton(ID_DONE, left + bw + 6, top + 192, bw, 20, I18n.format("gui.done")));
        addButton(new GuiButton(ID_CANCEL, left + 2 * (bw + 6), top + 192, bw, 20, I18n.format("gui.cancel")));

        if (old != null && old.size() == fields.size()) {
            for (int i = 0; i < fields.size(); i++) fields.get(i).setText(old.get(i));
        }
        updateWidgets();
    }

    @SuppressWarnings("unchecked")
    private GuiButton addButton(GuiButton button) {
        buttonList.add(button);
        return button;
    }

    private GuiTextField field(int x, int y, int w, int maxLength, String text) {
        GuiTextField f = new GuiTextField(fontRendererObj, x, y, w, 18);
        f.setMaxStringLength(maxLength);
        f.setText(text);
        fields.add(f);
        return f;
    }

    private void updateWidgets() {
        boolean local = source == PictureFrame.SOURCE_LOCAL;
        btnSource.displayString = I18n.format("minepapers.gui.source." + source);
        btnPrev.visible = local;
        btnNext.visible = local;
        btnFolder.visible = local;
        btnFit.displayString = I18n.format("minepapers.gui.fit." + fitMode);
        btnStyle.displayString = I18n.format("minepapers.gui.style." + style);
        btnMode.displayString = I18n.format("minepapers.gui.mode." + mode);
        for (GuiTextField f : fields) {
            if (!isActive(f)) f.setFocused(false);
        }
    }

    private boolean isActive(GuiTextField f) {
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
        switch (button.id) {
            case ID_SOURCE:
                source = (source + 1) % PictureFrame.SOURCE_COUNT;
                updateWidgets();
                break;
            case ID_FIT:
                fitMode = (fitMode + 1) % PictureFrame.FIT_COUNT;
                updateWidgets();
                break;
            case ID_STYLE:
                style = (style + 1) % PictureFrame.STYLE_COUNT;
                updateWidgets();
                break;
            case ID_MODE:
                mode = (mode + 1) % PictureFrame.MODE_COUNT;
                updateWidgets();
                break;
            case ID_BORDER:
                toggleBorder();
                break;
            case ID_PREV:
            case ID_NEXT:
                cycleLocal(button.id == ID_NEXT ? 1 : -1);
                break;
            case ID_FOLDER:
                openFolder();
                break;
            case ID_RELOAD: {
                String key = currentKey();
                if (key != null) {
                    PictureManager.reload(key);
                    PictureManager.get(key);
                    // Перечитать папку и выложить на сервер то, что в ней появилось.
                    if (key.equals(tile.getOriginKey())) {
                        if (tile.hasSharedFiles()) PictureManager.reload(tile.getSourceKey());
                        ImageSync.offer(picture);
                    }
                }
                break;
            }
            case ID_SIZE_PX:
                convert(fWidth, cbSizePx.isChecked());
                convert(fHeight, cbSizePx.isChecked());
                break;
            case ID_POS_PX:
                convert(fOffX, cbPosPx.isChecked());
                convert(fOffY, cbPosPx.isChecked());
                break;
            case ID_DONE:
                save();
                break;
            case ID_CANCEL:
                mc.displayGuiScreen(null);
                break;
        }
    }

    /** Переключение блоки <-> пиксели: пересчитываем значение, чтобы картинка не "прыгала". */
    private void convert(GuiTextField f, boolean toPixels) {
        Float v = parse(f.getText());
        if (v != null) {
            f.setText(fmt(toPixels ? v * 16f : v / 16f));
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

    /** Текстура рамки: с предмета в руке, а повторное нажатие возвращает обычную. */
    private void toggleBorder() {
        if (!borderItem.isEmpty()) {
            borderItem = "";
            borderMeta = 0;
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return;
        String name = Item.itemRegistry.getNameForObject(held.getItem());
        if (name == null) return;
        borderItem = name;
        borderMeta = held.getItemDamage();
    }

    private void openFolder() {
        try {
            Desktop.getDesktop().open(MinePapers.picturesDir);
        } catch (Throwable t) {
            Sys.openURL("file://" + MinePapers.picturesDir.getAbsolutePath());
        }
    }

    private void save() {
        tile.source = source;
        tile.localPath = fPath.getText().trim();
        tile.yandexUrl = fUrl.getText().trim();
        tile.folderPath = fFolder.getText().trim();
        tile.imageUrl = fImage.getText().trim();
        Float interval = parse(fInterval.getText());
        if (interval != null) tile.interval = Math.round(interval);
        tile.sizeInPixels = cbSizePx.isChecked();
        float[] size = resolveSize(parse(fWidth.getText()), parse(fHeight.getText()));
        tile.width = size[0];
        tile.height = size[1];
        tile.offsetInPixels = cbPosPx.isChecked();
        tile.offsetX = orDefault(parse(fOffX.getText()), tile.offsetX);
        tile.offsetY = orDefault(parse(fOffY.getText()), tile.offsetY);
        tile.fitMode = fitMode;
        tile.style = style;
        tile.mode = mode;
        tile.borderItem = borderItem;
        tile.borderMeta = borderMeta;
        Float ppb = parse(fPpb.getText());
        if (ppb != null) tile.pixelsPerBlock = Math.round(ppb);

        // Прогоняем через те же ограничения, что и сервер.
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeSettings(tag);
        tile.readSettings(tag);

        picture.refresh(); // хитбокс сразу по новому размеру, не дожидаясь сервера
        MinePapers.network.sendToServer(new PacketSaveFrame(picture));
        // Файлы с этого компьютера другие игроки увидят, только когда они окажутся на сервере.
        ImageSync.offer(picture);
        mc.displayGuiScreen(null);
    }

    /**
     * Хитбокс должен быть известен серверу, поэтому "0 = авто" превращаем в конкретный размер
     * по пропорциям текущей картинки (если она ещё не загружена — квадрат). Округляем до пикселя.
     */
    private float[] resolveSize(Float w, Float h) {
        boolean px = cbSizePx.isChecked();
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
            PictureManager.Picture p = s.slide(picture.worldObj.getTotalWorldTime(), tile.interval, 0, tile.getSlideSeed());
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
        String s = String.format(java.util.Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    // ---------- Ввод ----------

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            save();
            return;
        }
        if (key == Keyboard.KEY_TAB) {
            focusNext();
            return;
        }
        for (GuiTextField f : fields) {
            if (isActive(f) && f.isFocused()) {
                f.textboxKeyTyped(c, key);
            }
        }
    }

    private void focusNext() {
        List<GuiTextField> active = new ArrayList<GuiTextField>();
        for (GuiTextField f : fields) {
            if (isActive(f)) active.add(f);
        }
        int focused = -1;
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).isFocused()) focused = i;
            active.get(i).setFocused(false);
        }
        active.get((focused + 1) % active.size()).setFocused(true);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        for (GuiTextField f : fields) {
            if (isActive(f)) f.mouseClicked(x, y, button);
        }
    }

    @Override
    public void updateScreen() {
        for (GuiTextField f : fields) {
            if (isActive(f)) f.updateCursorCounter();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---------- Отрисовка ----------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("minepapers.gui.title"), width / 2, top - 14, 0xFFFFFF);

        label(I18n.format("minepapers.gui.path." + source), left, top + 24);
        if (source == PictureFrame.SOURCE_YANDEX || source == PictureFrame.SOURCE_URL) {
            fontRendererObj.drawString(I18n.format("minepapers.gui.hint." + source), left, top + 60, 0x808080);
        }

        label(I18n.format("minepapers.gui.size"), left, top + 84);
        label(I18n.format("minepapers.gui.offset"), left, top + 120);
        label(I18n.format("minepapers.gui.interval"), left, top + 158);
        label(I18n.format("minepapers.gui.fit"), right, top + 84);
        label(I18n.format("minepapers.gui.style"), right, top + 120);
        label(I18n.format("minepapers.gui.ppb"), right, top + 158);

        for (GuiTextField f : fields) {
            if (isActive(f)) f.drawTextBox();
        }

        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(statusText(), GUI_WIDTH), left, top + 179, 0xFFFF80);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawBorderIcon();

        // Подсказки
        if (btnBorder.func_146115_a()) {
            borderTooltip(mouseX, mouseY);
        } else if (btnMode.func_146115_a()) {
            tooltip("minepapers.gui.mode." + mode + ".tip", mouseX, mouseY);
        } else if (btnFit.func_146115_a()) {
            tooltip("minepapers.gui.fit." + fitMode + ".tip", mouseX, mouseY);
        } else if (btnStyle.func_146115_a()) {
            tooltip("minepapers.gui.style.tip", mouseX, mouseY);
        } else if (mouseX >= right && mouseX < right + COLUMN && mouseY >= top + 152 && mouseY < top + 171) {
            tooltip("minepapers.gui.ppb.tip", mouseX, mouseY);
        } else if (cbSizePx.func_146115_a() || cbPosPx.func_146115_a()) {
            tooltip("minepapers.gui.pixels.tip", mouseX, mouseY);
        }
    }

    private void tooltip(String key, int x, int y, Object... args) {
        List<String> lines = new ArrayList<String>(Arrays.asList(I18n.format(key, args).split("\\\\n")));
        drawHoveringText(lines, x, y, fontRendererObj);
    }

    /** На кнопке — предмет, из которого сделана рамка (или обычная рамка). */
    private void drawBorderIcon() {
        ItemStack stack = FrameSkin.stack(borderItem, borderMeta);
        if (stack == null) stack = new ItemStack(MinePapers.frame);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.zLevel = 100f;
        try {
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack,
                btnBorder.xPosition + 2, btnBorder.yPosition + 2);
        } catch (Throwable ignored) {
            // Чужой предмет может не уметь рисоваться в интерфейсе — кнопка просто останется пустой.
        }
        itemRender.zLevel = 0f;
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
    }

    private void borderTooltip(int x, int y) {
        ItemStack custom = FrameSkin.stack(borderItem, borderMeta);
        if (!borderItem.isEmpty()) {
            tooltip("minepapers.gui.border.custom.tip", x, y, custom != null ? name(custom) : borderItem);
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) {
            tooltip("minepapers.gui.border.empty.tip", x, y);
        } else {
            tooltip("minepapers.gui.border.default.tip", x, y, name(held));
        }
    }

    private static String name(ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (Throwable t) {
            return String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));
        }
    }

    private void label(String text, int x, int y) {
        fontRendererObj.drawString(text, x, y, 0xA0A0A0);
    }

    private String statusText() {
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
        if (s.getErrorKey() != null) return I18n.format("minepapers.gui.status.error", I18n.format(s.getErrorKey()));
        if (s.getErrorDetail() != null) return I18n.format("minepapers.gui.status.error", s.getErrorDetail());
        return I18n.format(key.startsWith("shared:") ? "minepapers.gui.status.shared" : "minepapers.gui.status.count",
            s.count());
    }
}
