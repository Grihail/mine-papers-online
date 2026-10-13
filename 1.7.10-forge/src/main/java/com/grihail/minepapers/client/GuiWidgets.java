package com.grihail.minepapers.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

/** Элементы окна настройки картины. Панель, слоты, вкладки и кнопки — ванильные текстуры (сундук, вкладки творческого режима, кнопки), поэтому окно выглядит как родное и меняется вместе с ресурспаком. Из своей текстуры textures/gui/picture_frame.png — только иконки. */
final class GuiWidgets {

    /** Свои иконки: вкладки 16x16 в ряд с (0, 0) (см. TAB_ICON_U), обновление 10x10 в (64, 0), радио и галочки 10x10 с (0, 16), вписывание 16x16 с (0, 32). */
    static final ResourceLocation ICONS = new ResourceLocation(MinePapers.MODID, "textures/gui/picture_frame.png");
    private static final ResourceLocation CHEST = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final ResourceLocation TABS = new ResourceLocation("textures/gui/container/creative_inventory/tabs.png");
    private static final ResourceLocation BUTTONS = new ResourceLocation("textures/gui/widgets.png");
    private static final ResourceLocation BEACON = new ResourceLocation("textures/gui/container/beacon.png");

    /** Иконки вписывания и источников 16x16 — в ряд с (0, 32) и (0, 48), по порядку PictureFrame.FIT_* и SOURCE_*; с (0, 64) — рамки (обшивка, багет), за ними холсты (из блока, сама картинка). "Нет" у обоих — глаз (Eye). */
    /** Копировать, вставить, открытый и закрытый замок 10x10 — в ряду иконок (v = 0). */
    static final int ICON_COPY_U = 134, ICON_PASTE_U = 144, ICON_UNLOCKED_U = 176, ICON_LOCKED_U = 186,
        ICON_EYE_OPEN_U = 196, ICON_EYE_CLOSED_U = 206;
    static final int ICON_RELOAD_U = 64, RADIO_V = 16, FIT_ICON_V = 32, SOURCE_ICON_V = 48,
        LOOK_ICON_V = 64;
    /** Иконки вкладок по порядку (v = 0): первые четыре подряд, "Описание" — после копировать/вставить. */
    static final int[] TAB_ICON_U = { 0, 16, 32, 48, 160 };
    /** Цвета текста на панели (TEXT — как у подписей "Инвентарь" в ванили). */
    static final int TEXT = 0x404040, HINT = 0x707070, HOVER = 0x1E50A0, ERROR = 0xA00000;

    /** Нижняя часть текстуры сундука: подпись "Инвентарь" и слоты игрока, 176x96. Слоты в ней начинаются с y = 14. */
    static final int INVENTORY_PART_HEIGHT = 96, INVENTORY_PART_SLOTS_Y = 14;

    private GuiWidgets() {}

    private static void bind(ResourceLocation texture) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** Кусок текстуры 256x256 (u, v, uw x vh), растянутый на w x h. transpose — отражение по диагонали: верх текстуры уходит влево, левый край — вверх. */
    private static void blit(int x, int y, int w, int h, int u, int v, int uw, int vh, boolean transpose) {
        float f = 1f / 256f;
        float u0 = u * f, v0 = v * f, u1 = (u + uw) * f, v1 = (v + vh) * f;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        if (transpose) {
            t.addVertexWithUV(x, y + h, 0, u1, v0);
            t.addVertexWithUV(x + w, y + h, 0, u1, v1);
            t.addVertexWithUV(x + w, y, 0, u0, v1);
            t.addVertexWithUV(x, y, 0, u0, v0);
        } else {
            t.addVertexWithUV(x, y + h, 0, u0, v1);
            t.addVertexWithUV(x + w, y + h, 0, u1, v1);
            t.addVertexWithUV(x + w, y, 0, u1, v0);
            t.addVertexWithUV(x, y, 0, u0, v0);
        }
        t.draw();
    }

    private static void blit(int x, int y, int u, int v, int w, int h) {
        blit(x, y, w, h, u, v, w, h, false);
    }

    /** Окно высотой height: верх и боковины из текстуры сундука, снизу — его же часть с инвентарём игрока (её верх — на height - 96). */
    static void panel(int x, int y, int width, int height) {
        bind(CHEST);
        int top = height - INVENTORY_PART_HEIGHT;
        blit(x, y, 0, 0, width, 4);
        // Строка 5 текстуры — боковины и заливка без слотов (полоса заголовка); тянем её вниз.
        blit(x, y + 4, width, top - 4, 0, 5, width, 1, false);
        blit(x, y + top, 0, 126, width, INVENTORY_PART_HEIGHT);
    }

    /** Отдельный слот 18x18 из текстуры сундука; (x, y) — левый верхний угол ячейки, предмет — в (x + 1, y + 1). */
    static void slot(int x, int y) {
        bind(CHEST);
        blit(x, y, 7, 17, 18, 18);
    }

    /** Своя иконка из ICONS. */
    static void icon(int x, int y, int u, int v, int w, int h) {
        bind(ICONS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        blit(x, y, u, v, w, h);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /** Основа для своих кнопок: наведение считаем сами и сбрасываем у скрытых, чтобы подсказки не "залипали". */
    abstract static class Widget extends GuiButton {

        Widget(int id, int x, int y, int w, int h, String text) {
            super(id, x, y, w, h, text);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            field_146123_n = visible && mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width && mouseY < yPosition + height;
            if (visible) draw(mc, field_146123_n);
        }

        void draw(Minecraft mc, boolean hovered) {}

        boolean hovered() {
            return field_146123_n;
        }
    }

    /** Вкладка слева от окна — ванильная вкладка творческого режима, повёрнутая набок. Рисуется окном (drawTabs), а кнопка — только область клика. */
    static class Tab extends Widget {

        /** Вкладка 32x28 заходит на окно на 4 пикселя, как вкладки творческого режима. */
        static final int WIDTH = 32, HEIGHT = 28, OVERLAP = 4;

        final int icon;
        boolean selected;

        Tab(int id, int x, int y, int icon) {
            super(id, x, y, WIDTH - OVERLAP, HEIGHT, "");
            this.icon = icon;
        }

        /** Невыбранные рисуются до окна (окно закрывает их край), выбранная — после, сливаясь с ним. */
        void drawBody() {
            if (!visible) return;
            bind(TABS);
            // Средняя вкладка верхнего ряда (u = 28): v = 0 — невыбранная, v = 32 — выбранная.
            blit(xPosition, yPosition, WIDTH, HEIGHT, 28, selected ? 32 : 0, 28, 32, true);
        }

        void drawIcon() {
            if (visible) icon(xPosition + 8, yPosition + 6, TAB_ICON_U[icon], 0, 16, 16);
        }
    }

    /** Кнопка-переключатель 20x20 с иконкой 16x16 — ванильная кнопка маяка (22x22, режем по центру). Выбранная выглядит нажатой, выключенная — тёмной, с бледной иконкой. */
    static class Toggle extends Widget {

        final int iconU, iconV;
        boolean checked;

        Toggle(int id, int x, int y, int iconU, int iconV) {
            this(id, x, y, 20, 20, iconU, iconV);
        }

        Toggle(int id, int x, int y, int w, int h, int iconU, int iconV) {
            super(id, x, y, w, h, "");
            this.iconU = iconU;
            this.iconV = iconV;
        }

        @Override
        void draw(Minecraft mc, boolean hovered) {
            bind(BEACON);
            // В текстуре маяка с (0, 219): обычная, нажатая, выключенная, под курсором.
            int u = !enabled ? 44 : checked ? 22 : hovered ? 66 : 0;
            int x = xPosition, y = yPosition;
            blit(x, y, u, 219, 10, 10);
            blit(x + 10, y, u + 12, 219, 10, 10);
            blit(x, y + 10, u, 231, 10, 10);
            blit(x + 10, y + 10, u + 12, 231, 10, 10);
            icon(x + 2, y + 2, iconU, iconV, 16, 16);
            if (!enabled) drawRect(x + 2, y + 2, x + 18, y + 18, 0xA0303030);
        }
    }

    /** Переключатель "выключить": глаз 10x10 без кнопки. Выбран — глаз закрыт и ярок; нет — открыт и бледен (под курсором — ярче). */
    static class Eye extends Toggle {

        static final int WIDTH = 12;

        Eye(int id, int x, int y) {
            super(id, x, y, WIDTH, 20, -1, 0);
        }

        @Override
        void draw(Minecraft mc, boolean hovered) {
            bind(ICONS);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, checked ? 1f : hovered ? 0.7f : 0.35f);
            blit(xPosition + (width - 10) / 2, yPosition + (height - 10) / 2, checked ? ICON_EYE_CLOSED_U : ICON_EYE_OPEN_U, 0, 10, 10);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    /** Радиокнопка с подписью. */
    static class Radio extends Widget {

        boolean checked;

        Radio(int id, int x, int y, int w, String text) {
            super(id, x, y, w, 10, text);
        }

        @Override
        void draw(Minecraft mc, boolean hovered) {
            icon(xPosition, yPosition, checked ? 10 : 0, RADIO_V, 10, 10);
            mc.fontRenderer.drawString(displayString, xPosition + 13, yPosition + 1, hovered ? HOVER : TEXT);
        }
    }

    /** Галочка с подписью. */
    static class Check extends Widget {

        boolean checked;

        Check(int id, int x, int y, String text, boolean checked) {
            super(id, x, y, 13 + Minecraft.getMinecraft().fontRenderer.getStringWidth(text), 10, text);
            this.checked = checked;
        }

        @Override
        void draw(Minecraft mc, boolean hovered) {
            icon(xPosition, yPosition, checked ? 30 : 20, RADIO_V, 10, 10);
            mc.fontRenderer.drawString(displayString, xPosition + 13, yPosition + 1, hovered ? HOVER : TEXT);
        }
    }

    /** Ванильная кнопка меньше стандартных 20 пикселей: режем текстуру на левую/правую половины и верх/низ (нижние 3 строки — кромка). С текстом или с иконкой 10x10 (iconU >= 0). */
    static class Small extends Widget {

        final int iconU;

        Small(int id, int x, int y, int w, int h, String text, int iconU) {
            super(id, x, y, w, h, text);
            this.iconU = iconU;
        }

        @Override
        void draw(Minecraft mc, boolean hovered) {
            bind(BUTTONS);
            int v = !enabled ? 46 : hovered ? 86 : 66;
            int x = xPosition, y = yPosition, w = width, h = height;
            int left = w / 2, right = w - left, top = h - 3;
            blit(x, y, 0, v, left, top);
            blit(x + left, y, 200 - right, v, right, top);
            blit(x, y + top, 0, v + 17, left, 3);
            blit(x + left, y + top, 200 - right, v + 17, right, 3);
            if (iconU >= 0) {
                icon(x + (w - 10) / 2, y + (h - 10) / 2, iconU, 0, 10, 10);
            } else {
                int tw = mc.fontRenderer.getStringWidth(displayString);
                mc.fontRenderer.drawStringWithShadow(displayString, x + (w - tw) / 2, y + (h - 8) / 2,
                    hovered ? 0xFFFFA0 : 0xE0E0E0);
            }
        }
    }

    /** Замок привата 20x20: открыт — открытый замок; закрыт — у владельца закрытый замок (его картина), у остальных — голова владельца (лицо с его скина). */
    static class Lock extends Small {

        /** Владелец закрытого замка; null — открыт. */
        UUID owner;
        String ownerName = "";
        /** Смотрит сам владелец. */
        boolean mine;

        Lock(int id, int x, int y) {
            super(id, x, y, 20, 20, "", -1);
        }

        @Override
        void draw(Minecraft mc, boolean hovered) {
            super.draw(mc, hovered);
            if (owner == null || mine) {
                icon(xPosition + 5, yPosition + 5, owner == null ? ICON_UNLOCKED_U : ICON_LOCKED_U, 0, 10, 10);
            } else {
                face(skin(owner, ownerName), xPosition + 2, yPosition + 2, 16);
            }
        }
    }

    /** Лицо со скина 64x32 (8x8 в (8, 8)) и поверх — слой шапки (40, 8). */
    private static void face(ResourceLocation skin, int x, int y, int size) {
        bind(skin);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        skinRect(x, y, size, 8);
        skinRect(x, y, size, 40);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static void skinRect(int x, int y, int size, int u) {
        double u0 = u / 64.0, u1 = (u + 8) / 64.0, v0 = 8 / 32.0, v1 = 16 / 32.0;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + size, 0, u0, v1);
        t.addVertexWithUV(x + size, y + size, 0, u1, v1);
        t.addVertexWithUV(x + size, y, 0, u1, v0);
        t.addVertexWithUV(x, y, 0, u0, v0);
        t.draw();
    }

    /** Скины владельцев: пока настоящий не загружен — Стив. */
    private static final Map<UUID, ResourceLocation> SKINS = new HashMap<UUID, ResourceLocation>();

    /** Скин игрока: если он рядом — его, иначе профиль запрашивается у сервиса Mojang (в фоне, как у голов игроков) и скин подгружается. */
    static ResourceLocation skin(final UUID id, String name) {
        final Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer near = mc.theWorld != null ? mc.theWorld.func_152378_a(id) : null;
        if (near instanceof AbstractClientPlayer) return ((AbstractClientPlayer) near).getLocationSkin();
        ResourceLocation known = SKINS.get(id);
        if (known != null) return known;
        SKINS.put(id, AbstractClientPlayer.locationStevePng);
        final GameProfile bare = new GameProfile(id, name);
        Thread thread = new Thread(() -> {
            GameProfile profile = mc.func_152347_ac().fillProfileProperties(bare, false);
            mc.func_152344_a(() -> mc.func_152342_ad().func_152790_a(profile, (type, location) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) SKINS.put(id, location);
            }, false));
        }, "MinePapers skin");
        thread.setDaemon(true);
        thread.start();
        return AbstractClientPlayer.locationStevePng;
    }
}
