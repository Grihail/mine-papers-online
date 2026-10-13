package com.grihail.minepapers.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.joml.Matrix4f;

import com.grihail.minepapers.MinePapers;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Элементы окна настройки картины. Панель, слоты, вкладки и кнопки — ванильные текстуры и спрайты (сундук, вкладки творческого режима, кнопки, кнопки маяка), поэтому окно выглядит как родное и меняется вместе с ресурспаком. Из своей текстуры textures/gui/picture_frame.png — только иконки. */
final class GuiWidgets {

    /** Свои иконки: вкладки 16x16 в ряд с (0, 0) (см. TAB_ICON_U), обновление 10x10 в (64, 0), радио и галочки 10x10 с (0, 16), вписывание 16x16 с (0, 32), источники 16x16 с (0, 48), с (0, 64) — рамки (обшивка, багет), за ними холсты (из блока, сама картинка). "Нет" у обоих — глаз (Eye). */
    static final ResourceLocation ICONS = MinePapers.id("textures/gui/picture_frame.png");
    /** Копировать, вставить и открытый замок 10x10 — в ряду иконок (v = 0). */
    static final int ICON_COPY_U = 134, ICON_PASTE_U = 144, ICON_UNLOCKED_U = 176, ICON_LOCKED_U = 186,
        ICON_EYE_OPEN_U = 196, ICON_EYE_CLOSED_U = 206;
    static final int ICON_RELOAD_U = 64, RADIO_V = 16, FIT_ICON_V = 32, SOURCE_ICON_V = 48, LOOK_ICON_V = 64;
    /** Иконки вкладок по порядку (v = 0): первые четыре подряд, "Описание" — после копировать/вставить. */
    static final int[] TAB_ICON_U = { 0, 16, 32, 48, 160 };

    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation TAB_SELECTED = vanilla("container/creative_inventory/tab_top_selected_2");
    private static final ResourceLocation TAB_UNSELECTED = vanilla("container/creative_inventory/tab_top_unselected_2");
    private static final ResourceLocation BEACON_BUTTON = vanilla("container/beacon/button");
    private static final ResourceLocation BEACON_SELECTED = vanilla("container/beacon/button_selected");
    private static final ResourceLocation BEACON_HIGHLIGHTED = vanilla("container/beacon/button_highlighted");
    private static final ResourceLocation BEACON_DISABLED = vanilla("container/beacon/button_disabled");
    private static final WidgetSprites BUTTON = new WidgetSprites(vanilla("widget/button"), vanilla("widget/button_disabled"),
        vanilla("widget/button_highlighted"));

    /** Цвета текста на панели (TEXT — как у подписи "Инвентарь" в ванили). */
    static final int TEXT = 0x404040, HINT = 0x707070, HOVER = 0x1E50A0, ERROR = 0xA00000;

    /** Нижняя часть текстуры сундука: подпись "Инвентарь" и слоты игрока, 176x96. Слоты в ней начинаются с y = 14. */
    static final int INVENTORY_PART_HEIGHT = 96, INVENTORY_PART_SLOTS_Y = 14;

    private GuiWidgets() {}

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    /** Окно высотой height: верх и боковины из текстуры сундука, снизу — его же часть с инвентарём игрока. */
    static void panel(GuiGraphics g, int x, int y, int width, int height) {
        int top = height - INVENTORY_PART_HEIGHT;
        g.blit(CHEST, x, y, 0, 0, width, 4);
        // Строка 5 текстуры — боковины и заливка без слотов (полоса заголовка); тянем её вниз.
        g.blit(CHEST, x, y + 4, width, top - 4, 0, 5, width, 1, 256, 256);
        g.blit(CHEST, x, y + top, 0, 126, width, INVENTORY_PART_HEIGHT);
    }

    /** Отдельный слот 18x18 из текстуры сундука; (x, y) — левый верхний угол ячейки. */
    static void slot(GuiGraphics g, int x, int y) {
        g.blit(CHEST, x, y, 7, 17, 18, 18);
    }

    /** Своя иконка из ICONS. */
    static void icon(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        RenderSystem.enableBlend();
        g.blit(ICONS, x, y, u, v, w, h);
        RenderSystem.disableBlend();
    }

    /** Спрайт интерфейса, отражённый по диагонали: верх спрайта уходит влево, левый край — вверх (вкладка сверху становится вкладкой слева, свет по-прежнему сверху-слева). */
    static void spriteTransposed(GuiGraphics g, ResourceLocation id, int x, int y, int w, int h) {
        TextureAtlasSprite s = Minecraft.getInstance().getGuiSprites().getSprite(id);
        RenderSystem.setShaderTexture(0, s.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, x, y, 0).setUv(s.getU0(), s.getV0());
        b.addVertex(m, x, y + h, 0).setUv(s.getU1(), s.getV0());
        b.addVertex(m, x + w, y + h, 0).setUv(s.getU1(), s.getV1());
        b.addVertex(m, x + w, y, 0).setUv(s.getU0(), s.getV1());
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /** Основа своих кнопок: действие при нажатии, озвучка — как у обычной кнопки. */
    abstract static class Widget extends AbstractButton {

        private final Runnable action;

        Widget(int x, int y, int w, int h, Component text, Runnable action) {
            super(x, y, w, h, text);
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        static Font font() {
            return Minecraft.getInstance().font;
        }
    }

    /** Вкладка слева от окна — ванильная вкладка творческого режима, повёрнутая набок. Рисуется окном (drawBody, drawIcon), а сама кнопка — только область клика. */
    static class Tab extends Widget {

        /** Вкладка 32x26 заходит на окно на 4 пикселя, как вкладки творческого режима. */
        static final int WIDTH = 32, HEIGHT = 26, OVERLAP = 4;

        final int index;
        boolean selected;

        Tab(int x, int y, int index, Component name, Runnable action) {
            super(x, y, WIDTH - OVERLAP, HEIGHT, name, action);
            this.index = index;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

        /** Невыбранные рисуются до окна (окно закрывает их край), выбранная — после, сливаясь с ним. */
        void drawBody(GuiGraphics g) {
            spriteTransposed(g, selected ? TAB_SELECTED : TAB_UNSELECTED, getX(), getY(), WIDTH, HEIGHT);
        }

        void drawIcon(GuiGraphics g) {
            icon(g, getX() + 8, getY() + 5, TAB_ICON_U[index], 0, 16, 16);
        }
    }

    /** Кнопка-переключатель 22x22 с иконкой 16x16 — ванильная кнопка маяка. Выбранная выглядит нажатой, выключенная — тёмной, с бледной иконкой. */
    static class Toggle extends Widget {

        final int value, iconU, iconV;
        boolean checked;

        Toggle(int x, int y, int value, int iconU, int iconV, Component name, Runnable action) {
            this(x, y, 22, 22, value, iconU, iconV, name, action);
        }

        Toggle(int x, int y, int w, int h, int value, int iconU, int iconV, Component name, Runnable action) {
            super(x, y, w, h, name, action);
            this.value = value;
            this.iconU = iconU;
            this.iconV = iconV;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blitSprite(!active ? BEACON_DISABLED : checked ? BEACON_SELECTED : isHovered() ? BEACON_HIGHLIGHTED : BEACON_BUTTON,
                getX(), getY(), 22, 22);
            icon(g, getX() + 3, getY() + 3, iconU, iconV, 16, 16);
            if (!active) g.fill(getX() + 3, getY() + 3, getX() + 19, getY() + 19, 0xA0303030);
        }
    }

    /** Переключатель "выключить": глаз 10x10 без кнопки. Выбран — глаз закрыт и ярок; нет — открыт и бледен (под курсором — ярче). */
    static class Eye extends Toggle {

        static final int WIDTH = 12;

        Eye(int x, int y, int h, Component name, Runnable action) {
            super(x, y, WIDTH, h, 0, -1, 0, name, action);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, checked ? 1f : isHovered() ? 0.7f : 0.35f);
            g.blit(ICONS, getX() + (width - 10) / 2, getY() + (height - 10) / 2, checked ? ICON_EYE_CLOSED_U : ICON_EYE_OPEN_U, 0, 10, 10);
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
    }

    /** Радиокнопка с подписью. */
    static class Radio extends Widget {

        final int value;
        boolean checked;

        Radio(int x, int y, int w, int value, Component text, Runnable action) {
            super(x, y, w, 10, text, action);
            this.value = value;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            icon(g, getX(), getY(), checked ? 10 : 0, RADIO_V, 10, 10);
            g.drawString(font(), getMessage(), getX() + 13, getY() + 1, isHovered() ? HOVER : TEXT, false);
        }
    }

    /** Галочка с подписью. */
    static class Check extends Widget {

        boolean checked;

        Check(int x, int y, Component text, boolean checked, Runnable action) {
            super(x, y, 13 + font().width(text), 10, text, action);
            this.checked = checked;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            icon(g, getX(), getY(), checked ? 30 : 20, RADIO_V, 10, 10);
            g.drawString(font(), getMessage(), getX() + 13, getY() + 1, isHovered() ? HOVER : TEXT, false);
        }
    }

    /** Замок привата 20x20: открыт — открытый замок; закрыт — у владельца закрытый замок (его картина), у остальных — голова владельца (лицо с его скина). */
    static class Lock extends Widget {

        /** Владелец закрытого замка; null — открыт. */
        UUID owner;
        String ownerName = "";
        /** Смотрит сам владелец. */
        boolean mine;

        Lock(int x, int y, Runnable action) {
            super(x, y, 20, 20, Component.empty(), action);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blitSprite(BUTTON.get(active, isHovered()), getX(), getY(), width, height);
            if (owner == null || mine) {
                icon(g, getX() + 5, getY() + 5, owner == null ? ICON_UNLOCKED_U : ICON_LOCKED_U, 0, 10, 10);
            } else {
                PlayerFaceRenderer.draw(g, skin(owner), getX() + 2, getY() + 2, 16);
            }
        }
    }

    /** Скины владельцев: пока настоящий не загружен — стандартный (Стив, Алекс...). */
    private static final Map<UUID, PlayerSkin> SKINS = new HashMap<UUID, PlayerSkin>();

    /** Скин игрока: если он на сервере — из списка игроков, иначе его профиль запрашивается (как у голов игроков) и скин подгружается. */
    static PlayerSkin skin(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(id) : null;
        if (info != null) return info.getSkin();
        PlayerSkin known = SKINS.get(id);
        if (known != null) return known;
        PlayerSkin fallback = DefaultPlayerSkin.get(id);
        SKINS.put(id, fallback);
        SkullBlockEntity.fetchGameProfile(id)
            .thenCompose(p -> p.map(mc.getSkinManager()::getOrLoad).orElseGet(() -> CompletableFuture.completedFuture(null)))
            .thenAccept(s -> {
                if (s != null) mc.execute(() -> SKINS.put(id, s));
            });
        return fallback;
    }

    /** Маленькая ванильная кнопка (спрайт тянется по девяти частям): с текстом или с иконкой 10x10 (iconU >= 0). */
    static class Small extends Widget {

        final int iconU;

        Small(int x, int y, int w, int h, Component text, int iconU, Runnable action) {
            super(x, y, w, h, text, action);
            this.iconU = iconU;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blitSprite(BUTTON.get(active, isHovered()), getX(), getY(), width, height);
            if (iconU >= 0) {
                icon(g, getX() + (width - 10) / 2, getY() + (height - 10) / 2, iconU, 0, 10, 10);
            } else {
                g.drawCenteredString(font(), getMessage(), getX() + width / 2, getY() + (height - 8) / 2,
                    isHovered() ? 0xFFFFA0 : 0xE0E0E0);
            }
        }
    }
}
