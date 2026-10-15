package com.grihail.minepapers.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import com.grihail.minepapers.picture.Links;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ChatAllowedCharacters;

/** Многострочное поле для длинных путей и ссылок (и описания — с переводами строк по Enter): текст переносится по символам (в ссылках нет пробелов), выделение мышью и Shift+стрелками, двойной клик — выделить всё, Ctrl+A/C/X/V, прокрутка колёсиком. Наследует GuiTextField, чтобы окно работало с ним как с обычным полем; всё состояние — своё. */
class GuiTextArea extends GuiTextField {

    private static final int PAD_X = 4, PAD_Y = 3, LINE = 10, DOUBLE_CLICK_MS = 250;

    private final FontRenderer font;
    private final int x, y, w, h;

    private String text = "";
    private int maxLength = 32;
    /** Enter переносит строку (для описания); у путей и ссылок переводов строк нет. */
    private boolean multiline;
    /** Курсор и другой конец выделения (равны — выделения нет). */
    private int cursor, anchor;
    /** Первая видимая строка. */
    private int scroll;
    private int ticks;
    private boolean dragging;
    private long lastClick;
    private int lastClickIndex = -1;

    GuiTextArea(FontRenderer font, int x, int y, int w, int h) {
        super(font, x, y, w, h);
        this.font = font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    GuiTextArea multiline() {
        multiline = true;
        return this;
    }

    // ---------- Текст ----------

    /** Допустимые символы; переводы строк — только в многострочном режиме. */
    private String filter(String s) {
        if (!multiline) return ChatAllowedCharacters.filerAllowedCharacters(s);
        StringBuilder b = new StringBuilder();
        String[] lines = s.replace("\r", "").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) b.append('\n');
            b.append(ChatAllowedCharacters.filerAllowedCharacters(lines[i]));
        }
        return b.toString();
    }

    @Override
    public void setText(String s) {
        text = filter(s);
        if (text.length() > maxLength) text = text.substring(0, maxLength);
        cursor = anchor = text.length();
        scroll = 0;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setMaxStringLength(int length) {
        maxLength = length;
        if (text.length() > length) setText(text);
    }

    @Override
    public int getMaxStringLength() {
        return maxLength;
    }

    @Override
    public String getSelectedText() {
        return text.substring(selStart(), selEnd());
    }

    private int selStart() {
        return Math.min(cursor, anchor);
    }

    private int selEnd() {
        return Math.max(cursor, anchor);
    }

    /** Заменить выделение строкой (пустая — просто удалить выделенное). */
    private void insert(String s) {
        s = filter(s);
        int a = selStart(), b = selEnd();
        int room = maxLength - (text.length() - (b - a));
        if (s.length() > room) s = s.substring(0, Math.max(0, room));
        text = text.substring(0, a) + s + text.substring(b);
        cursor = anchor = a + s.length();
    }

    // ---------- Строки ----------

    /** Начала строк при переносе по ширине поля и после переводов строк. */
    private List<Integer> lineStarts() {
        List<Integer> starts = new ArrayList<Integer>();
        starts.add(0);
        int width = 0, start = 0, max = w - 2 * PAD_X;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
                start = i + 1;
                width = 0;
                continue;
            }
            int cw = font.getCharWidth(text.charAt(i));
            if (width + cw > max && i > start) {
                starts.add(i);
                start = i;
                width = 0;
            }
            width += cw;
        }
        return starts;
    }

    private static int lineOf(List<Integer> starts, int index) {
        int line = 0;
        for (int i = 1; i < starts.size(); i++) {
            if (starts.get(i) <= index) line = i;
        }
        return line;
    }

    /** Конец строки — без завершающего перевода строки. */
    private int lineEnd(List<Integer> starts, int line) {
        if (line + 1 >= starts.size()) return text.length();
        int next = starts.get(line + 1);
        return text.charAt(next - 1) == '\n' ? next - 1 : next;
    }

    private int visibleLines() {
        return Math.max(1, (h - 2 * PAD_Y + 2) / LINE);
    }

    private int width(int from, int to) {
        return font.getStringWidth(text.substring(from, to));
    }

    /** Ближайшая к пикселю px позиция в строке. */
    private int indexInLine(List<Integer> starts, int line, int px) {
        int start = starts.get(line), end = lineEnd(starts, line), width = 0;
        for (int i = start; i < end; i++) {
            int cw = font.getCharWidth(text.charAt(i));
            if (px < width + cw / 2) return i;
            width += cw;
        }
        return end;
    }

    private int indexAt(int mouseX, int mouseY) {
        List<Integer> starts = lineStarts();
        int line = scroll + Math.floorDiv(mouseY - (y + PAD_Y), LINE);
        line = Math.max(0, Math.min(starts.size() - 1, line));
        return indexInLine(starts, line, mouseX - (x + PAD_X));
    }

    /** Прокрутить так, чтобы курсор был виден. */
    private void showCursor() {
        List<Integer> starts = lineStarts();
        int line = lineOf(starts, cursor), visible = visibleLines();
        if (line < scroll) scroll = line;
        if (line >= scroll + visible) scroll = line - visible + 1;
        clampScroll(starts);
    }

    private void clampScroll(List<Integer> starts) {
        scroll = Math.max(0, Math.min(scroll, starts.size() - visibleLines()));
    }

    void scrollBy(int lines) {
        scroll += lines;
        clampScroll(lineStarts());
    }

    boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    // ---------- Клавиатура ----------

    @Override
    public boolean textboxKeyTyped(char c, int key) {
        if (!isFocused()) return false;
        boolean shift = GuiScreen.isShiftKeyDown(), ctrl = GuiScreen.isCtrlKeyDown();
        if (ctrl && key == Keyboard.KEY_A) {
            anchor = 0;
            cursor = text.length();
        } else if (ctrl && key == Keyboard.KEY_C) {
            GuiScreen.setClipboardString(getSelectedText());
        } else if (ctrl && key == Keyboard.KEY_X) {
            GuiScreen.setClipboardString(getSelectedText());
            insert("");
        } else if (ctrl && key == Keyboard.KEY_V) {
            insert(GuiScreen.getClipboardString());
        } else {
            switch (key) {
                case Keyboard.KEY_BACK:
                    if (cursor == anchor && cursor > 0) anchor = ctrl ? wordLeft(cursor) : cursor - 1;
                    insert("");
                    break;
                case Keyboard.KEY_DELETE:
                    if (cursor == anchor && cursor < text.length()) anchor = ctrl ? wordRight(cursor) : cursor + 1;
                    insert("");
                    break;
                case Keyboard.KEY_RETURN:
                case Keyboard.KEY_NUMPADENTER:
                    if (!multiline) return false;
                    insert("\n");
                    break;
                case Keyboard.KEY_LEFT:
                    move(cursor != anchor && !shift ? selStart() : ctrl ? wordLeft(cursor) : cursor - 1, shift);
                    break;
                case Keyboard.KEY_RIGHT:
                    move(cursor != anchor && !shift ? selEnd() : ctrl ? wordRight(cursor) : cursor + 1, shift);
                    break;
                case Keyboard.KEY_UP:
                case Keyboard.KEY_DOWN: {
                    List<Integer> starts = lineStarts();
                    int line = lineOf(starts, cursor), px = width(starts.get(line), cursor);
                    int target = line + (key == Keyboard.KEY_UP ? -1 : 1);
                    if (target < 0) move(0, shift);
                    else if (target >= starts.size()) move(text.length(), shift);
                    else move(indexInLine(starts, target, px), shift);
                    break;
                }
                case Keyboard.KEY_HOME: {
                    List<Integer> starts = lineStarts();
                    move(ctrl ? 0 : starts.get(lineOf(starts, cursor)), shift);
                    break;
                }
                case Keyboard.KEY_END: {
                    List<Integer> starts = lineStarts();
                    move(ctrl ? text.length() : lineEnd(starts, lineOf(starts, cursor)), shift);
                    break;
                }
                default:
                    if (!ChatAllowedCharacters.isAllowedCharacter(c)) return false;
                    insert(Character.toString(c));
            }
        }
        ticks = 0;
        showCursor();
        return true;
    }

    private void move(int to, boolean select) {
        cursor = Math.max(0, Math.min(text.length(), to));
        if (!select) anchor = cursor;
    }

    /** Части пути и ссылки разделены знаками (/ . ? & = - _ ...), по ним и прыгает Ctrl+стрелка. */
    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    private int wordLeft(int from) {
        int i = from;
        while (i > 0 && !isWordChar(text.charAt(i - 1))) i--;
        while (i > 0 && isWordChar(text.charAt(i - 1))) i--;
        return i;
    }

    private int wordRight(int from) {
        int i = from;
        while (i < text.length() && !isWordChar(text.charAt(i))) i++;
        while (i < text.length() && isWordChar(text.charAt(i))) i++;
        return i;
    }

    // ---------- Мышь ----------

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        boolean inside = contains(mouseX, mouseY);
        setFocused(inside);
        if (!inside || button != 0) return;
        int index = indexAt(mouseX, mouseY);
        long now = Minecraft.getSystemTime();
        if (index == lastClickIndex && now - lastClick < DOUBLE_CLICK_MS) {
            anchor = 0; // двойной клик — выделить всё, чтобы сразу скопировать или заменить ссылку
            cursor = text.length();
            dragging = false;
        } else {
            cursor = index;
            if (!GuiScreen.isShiftKeyDown()) anchor = index;
            dragging = true;
        }
        lastClick = now;
        lastClickIndex = index;
        ticks = 0;
    }

    /** Перетаскивание с зажатой кнопкой — выделение; за краем поля текст прокручивается. */
    boolean mouseDragged(int mouseX, int mouseY) {
        if (!dragging) return false;
        if (mouseY < y) scrollBy(-1);
        else if (mouseY >= y + h) scrollBy(1);
        cursor = indexAt(mouseX, Math.max(y, Math.min(y + h - 1, mouseY)));
        return true;
    }

    void mouseReleased() {
        dragging = false;
    }

    // ---------- Отрисовка ----------

    @Override
    public void setFocused(boolean focused) {
        if (focused && !isFocused()) ticks = 0;
        super.setFocused(focused);
    }

    @Override
    public void updateCursorCounter() {
        ticks++;
    }

    @Override
    public void drawTextBox() {
        // Рамка и фон — как у ванильного поля ввода.
        drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFA0A0A0);
        drawRect(x, y, x + w, y + h, 0xFF000000);
        List<Integer> starts = lineStarts();
        int a = selStart(), b = selEnd(), visible = visibleLines();
        clampScroll(starts);
        // В описании внешние ссылки подчёркнуты.
        List<int[]> links = multiline ? Links.find(text) : java.util.Collections.<int[]>emptyList();
        for (int line = scroll; line < starts.size() && line < scroll + visible; line++) {
            int start = starts.get(line), end = lineEnd(starts, line);
            int lx = x + PAD_X, ly = y + PAD_Y + (line - scroll) * LINE;
            font.drawStringWithShadow(text.substring(start, end), lx, ly, 0xE0E0E0);
            for (int[] link : links) {
                int ls = Math.max(link[0], start), le = Math.min(link[1], end);
                if (ls < le) drawRect(lx + width(start, ls), ly + 8, lx + width(start, le), ly + 9, 0xFFE0E0E0);
            }
            int s = Math.max(a, start), e = Math.min(b, end);
            if (s < e) highlight(lx + width(start, s), ly - 1, lx + width(start, e), ly + font.FONT_HEIGHT);
        }
        if (isFocused() && ticks / 6 % 2 == 0) {
            int line = lineOf(starts, cursor);
            if (line >= scroll && line < scroll + visible) {
                int cx = x + PAD_X + width(starts.get(line), cursor), cy = y + PAD_Y + (line - scroll) * LINE;
                drawRect(cx, cy - 1, cx + 1, cy + font.FONT_HEIGHT, 0xFFD0D0D0);
            }
        }
        // Полоса прокрутки, если строк больше, чем помещается.
        if (starts.size() > visible) {
            int track = h - 2, thumb = Math.max(4, track * visible / starts.size());
            int top = y + 1 + (track - thumb) * scroll / (starts.size() - visible);
            drawRect(x + w - 3, top, x + w - 1, top + thumb, 0xFF808080);
        }
    }

    /** Выделение — инверсией цвета, как у ванильного поля. */
    private static void highlight(int x1, int y1, int x2, int y2) {
        Tessellator t = Tessellator.instance;
        GL11.glColor4f(0f, 0f, 1f, 1f);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glLogicOp(GL11.GL_OR_REVERSE);
        t.startDrawingQuads();
        t.addVertex(x1, y2, 0);
        t.addVertex(x2, y2, 0);
        t.addVertex(x2, y1, 0);
        t.addVertex(x1, y1, 0);
        t.draw();
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
