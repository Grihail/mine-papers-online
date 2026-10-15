package com.grihail.minepapers.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/** Элементы окна настройки картины. Панель, слоты, вкладки и кнопки — ванильные текстуры (сундук, вкладки творческого режима, кнопки), поэтому окно выглядит как родное и меняется вместе с ресурспаком. Из своей текстуры textures/gui/picture_frame.png — только иконки. */
final class GuiWidgets {

    /** Свои иконки: вкладки 16x16 в ряд с (0, 0) (см. TAB_ICON_U), обновление 10x10 в (64, 0), галочки 10x10 с (20, 16), вписывание 16x16 с (0, 32). */
    static final ResourceLocation ICONS = new ResourceLocation(MinePapers.MODID, "textures/gui/picture_frame.png");
    private static final ResourceLocation CHEST = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final ResourceLocation TABS = new ResourceLocation("textures/gui/container/creative_inventory/tabs.png");
    private static final ResourceLocation BUTTONS = new ResourceLocation("textures/gui/widgets.png");
    private static final ResourceLocation BEACON = new ResourceLocation("textures/gui/container/beacon.png");

    /** Иконки вписывания и источников 16x16 — в ряд с (0, 32) и (0, 48), по порядку PictureFrame.FIT_* и SOURCE_*; с (0, 64) — обшивка рамки, за ней холсты (из блока, сама картинка). "Нет" у обоих — крест (NONE_ICON_U, NONE_ICON_V). Глаз 10x10 в (196, 0) и (206, 0) в текстуре остался, но не используется. */
    /** Копировать, вставить, открытый и закрытый замок 10x10 — в ряду иконок (v = 0). */
    static final int ICON_COPY_U = 134, ICON_PASTE_U = 144, ICON_UNLOCKED_U = 176, ICON_LOCKED_U = 186;
    /** Крест 16x16 — кнопка "нет" у рамки и холста. */
    static final int NONE_ICON_U = 144, NONE_ICON_V = 64;
    /** Перо 16x16 (контур ванильного пера) — на кнопке подписи. */
    static final int PEN_U = 64, PEN_V = 64;
    /** "18+" 16x16 — на кнопке пометки NSFW: серая и красная (картина помечена). */
    static final int NSFW_U = 80, NSFW_ON_U = 96, NSFW_V = 64;
    /** Виды дизеринга 16x16 — градиент, продизеренный каждым видом, по порядку PictureFrame.DITHER_*. */
    static final int DITHER_ICON_U = 112, DITHER_ICON_V = 64;
    static final int ICON_RELOAD_U = 64, CHECK_V = 16, FIT_ICON_V = 32, SOURCE_ICON_V = 48,
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
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Кусок текстуры 256x256 (u, v, uw x vh), растянутый на w x h. transpose — отражение по диагонали: верх текстуры уходит влево, левый край — вверх. */
    private static void blit(int x, int y, int w, int h, int u, int v, int uw, int vh, boolean transpose) {
        float f = 1f / 256f;
        float u0 = u * f, v0 = v * f, u1 = (u + uw) * f, v1 = (v + vh) * f;
        if (transpose) {
            quad(x, y, x + w, y + h, u1, v0, u1, v1, u0, v1, u0, v0);
        } else {
            quad(x, y, x + w, y + h, u0, v1, u1, v1, u1, v0, u0, v0);
        }
    }

    private static void blit(int x, int y, int u, int v, int w, int h) {
        blit(x, y, w, h, u, v, w, h, false);
    }

    /** Прямоугольник (x0, y0)–(x1, y1) с текстурой; uv — у углов по порядку: левый нижний, правый нижний, правый верхний, левый верхний. */
    private static void quad(double x0, double y0, double x1, double y1, double ua, double va, double ub, double vb,
                             double uc, double vc, double ud, double vd) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x0, y1, 0).tex(ua, va).endVertex();
        b.pos(x1, y1, 0).tex(ub, vb).endVertex();
        b.pos(x1, y0, 0).tex(uc, vc).endVertex();
        b.pos(x0, y0, 0).tex(ud, vd).endVertex();
        t.draw();
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
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        blit(x, y, u, v, w, h);
        GlStateManager.disableBlend();
    }

    /** Основа для своих кнопок: наведение считаем сами и сбрасываем у скрытых, чтобы подсказки не "залипали". */
    abstract static class Widget extends GuiButton {

        Widget(int id, int x, int y, int w, int h, String text) {
            super(id, x, y, w, h, text);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            hovered = visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            if (visible) draw(mc, hovered);
        }

        void draw(Minecraft mc, boolean hovered) {}

        boolean hovered() {
            return hovered;
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
            blit(x, y, WIDTH, HEIGHT, 28, selected ? 32 : 0, 28, 32, true);
        }

        void drawIcon() {
            if (visible) icon(x + 8, y + 6, TAB_ICON_U[icon], 0, 16, 16);
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
            int x = this.x, y = this.y;
            blit(x, y, u, 219, 10, 10);
            blit(x + 10, y, u + 12, 219, 10, 10);
            blit(x, y + 10, u, 231, 10, 10);
            blit(x + 10, y + 10, u + 12, 231, 10, 10);
            icon(x + 2, y + 2, iconU, iconV, 16, 16);
            if (!enabled) drawRect(x + 2, y + 2, x + 18, y + 18, 0xA0303030);
        }
    }

    /** Ползунок высотой 20, как ванильный в настройках (GuiOptionSlider), с целым значением от min до max; подпись — label от значения, change — новое значение. Тянется, пока после клика по нему зажата кнопка мыши. Колёсико над ним (окно зовёт scroll) меняет значение функцией wheel (значение, направление: 1 — вправо, -1 — влево). */
    static class Slider extends Widget {

        private final int min, max;
        private final IntBinaryOperator wheel;
        private final IntFunction<String> label;
        private final IntConsumer change;
        private int value;
        private boolean dragging;

        Slider(int id, int x, int y, int w, int min, int max, IntBinaryOperator wheel, int value, IntFunction<String> label, IntConsumer change) {
            super(id, x, y, w, 20, "");
            this.min = min;
            this.max = max;
            this.wheel = wheel;
            this.label = label;
            this.change = change;
            setInt(value);
        }

        /** Поставить значение без change (окно и так его знает). */
        void setInt(int v) {
            value = Math.max(min, Math.min(max, v));
            displayString = label.apply(value);
        }

        /** dir > 0 — вправо. Выключенный ползунок не двигается. */
        void scroll(int dir) {
            if (enabled) change(wheel.applyAsInt(value, dir));
        }

        boolean isDragging() {
            return dragging;
        }

        private void change(int v) {
            int old = value;
            setInt(v);
            if (value != old) change.accept(value);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (!super.mousePressed(mc, mouseX, mouseY)) return false;
            dragging = true;
            follow(mouseX);
            return true;
        }

        private void follow(int mouseX) {
            double t = (mouseX - (x + 4)) / (double) (width - 8);
            change(min + (int) Math.round(Math.max(0, Math.min(1, t)) * (max - min)));
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (dragging) {
                if (visible && Mouse.isButtonDown(0)) {
                    follow(mouseX);
                } else {
                    dragging = false;
                }
            }
            super.drawButton(mc, mouseX, mouseY, partialTicks);
        }

        @Override
        void draw(Minecraft mc, boolean hovered) {
            bind(BUTTONS);
            // Подложка — как у выключенной кнопки, ручка 8x20 — края обычной кнопки.
            int half = width / 2, knob = x + (int) Math.round((value - min) / (double) (max - min) * (width - 8));
            blit(x, y, 0, 46, half, 20);
            blit(x + half, y, 200 - (width - half), 46, width - half, 20);
            blit(knob, y, 0, 66, 4, 20);
            blit(knob + 4, y, 196, 66, 4, 20);
            int color = !enabled ? 0xA0A0A0 : hovered ? 0xFFFFA0 : 0xE0E0E0; // как у ванильных кнопок
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + 6, color);
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
            icon(x, y, checked ? 30 : 20, CHECK_V, 10, 10);
            mc.fontRenderer.drawString(displayString, x + 13, y + 1, hovered ? HOVER : TEXT);
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
            int x = this.x, y = this.y, w = width, h = height;
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
                icon(x + 5, y + 5, owner == null ? ICON_UNLOCKED_U : ICON_LOCKED_U, 0, 10, 10);
            } else {
                face(skin(owner, ownerName), x + 2, y + 2, 16);
            }
        }
    }

    /** Подпись 20x20: не подписана — перо; подписана (или подпишется при закрытии окна) — голова подписавшего. */
    static class Sign extends Small {

        /** Кто подписал; null — никто. */
        UUID signer;
        String signerName = "";

        Sign(int id, int x, int y) {
            super(id, x, y, 20, 20, "", -1);
        }

        @Override
        void draw(Minecraft mc, boolean hovered) {
            super.draw(mc, hovered);
            if (signer == null) {
                icon(x + 2, y + 2, PEN_U, PEN_V, 16, 16);
            } else {
                face(skin(signer, signerName), x + 2, y + 2, 16);
            }
        }
    }

    /** Пометка NSFW 20x20: "18+" — серая, у помеченной картины — красная. */
    static class Nsfw extends Small {

        boolean marked;

        Nsfw(int id, int x, int y) {
            super(id, x, y, 20, 20, "", -1);
        }

        @Override
        void draw(Minecraft mc, boolean hovered) {
            super.draw(mc, hovered);
            icon(x + 2, y + 2, marked ? NSFW_ON_U : NSFW_U, NSFW_V, 16, 16);
        }
    }

    /** Лицо со скина 64x64 (8x8 в (8, 8)) и поверх — слой шапки (40, 8). */
    private static void face(ResourceLocation skin, int x, int y, int size) {
        bind(skin);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        skinRect(x, y, size, 8);
        skinRect(x, y, size, 40);
        GlStateManager.disableBlend();
    }

    private static void skinRect(int x, int y, int size, int u) {
        double u0 = u / 64.0, u1 = (u + 8) / 64.0, v0 = 8 / 64.0, v1 = 16 / 64.0;
        quad(x, y, x + size, y + size, u0, v1, u1, v1, u1, v0, u0, v0);
    }

    /** Скины владельцев: пока настоящий не загружен — Стив. */
    private static final Map<UUID, ResourceLocation> SKINS = new HashMap<UUID, ResourceLocation>();

    /** Скин игрока: если он рядом — его, иначе профиль запрашивается у сервиса Mojang (в фоне, как у голов игроков) и скин подгружается. Не нашлось (офлайн-сервер, лаунчер с прокси) — прямо у Mojang, см. MojangProfiles. */
    static ResourceLocation skin(final UUID id, final String name) {
        final Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer near = mc.world != null ? mc.world.getPlayerEntityByUUID(id) : null;
        if (near instanceof AbstractClientPlayer) return ((AbstractClientPlayer) near).getLocationSkin();
        ResourceLocation known = SKINS.get(id);
        if (known != null) return known;
        SKINS.put(id, DefaultPlayerSkin.getDefaultSkin(id));
        final GameProfile bare = new GameProfile(id, name);
        Thread thread = new Thread(() -> {
            GameProfile profile = mc.getSessionService().fillProfileProperties(bare, false);
            if (!profile.getProperties().containsKey("textures")) {
                String[] textures = MojangProfiles.textures(id, name);
                if (textures != null) {
                    profile = new GameProfile(id, name);
                    profile.getProperties().put("textures", new Property("textures", textures[0], textures[1]));
                }
            }
            final GameProfile found = profile;
            mc.addScheduledTask(() -> mc.getSkinManager().loadProfileTextures(found, (type, location, texture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) SKINS.put(id, location);
            }, false));
        }, "MinePapers skin");
        thread.setDaemon(true);
        thread.start();
        return DefaultPlayerSkin.getDefaultSkin(id);
    }
}
