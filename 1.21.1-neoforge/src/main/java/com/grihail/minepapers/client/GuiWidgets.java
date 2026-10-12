package com.grihail.minepapers.client;

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
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Элементы окна настройки картины. Панель, слоты, вкладки и кнопки — ванильные текстуры и спрайты (сундук, вкладки творческого режима, кнопки, кнопки маяка), поэтому окно выглядит как родное и меняется вместе с ресурспаком. Из своей текстуры textures/gui/picture_frame.png — только иконки. */
final class GuiWidgets {

    /** Свои иконки: вкладки 16x16 в ряд с (0, 0), обновление 10x10 в (64, 0), радио и галочки 10x10 с (0, 16), вписывание 16x16 с (0, 32), источники 16x16 с (0, 48). */
    static final ResourceLocation ICONS = MinePapers.id("textures/gui/picture_frame.png");
    /** Копировать и вставить 10x10 — в ряду иконок (v = 0). */
    static final int ICON_COPY_U = 134, ICON_PASTE_U = 144;
    static final int ICON_RELOAD_U = 64, RADIO_V = 16, FIT_ICON_V = 32, SOURCE_ICON_V = 48;

    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation TAB_SELECTED = vanilla("container/creative_inventory/tab_top_selected_2");
    private static final ResourceLocation TAB_UNSELECTED = vanilla("container/creative_inventory/tab_top_unselected_2");
    private static final ResourceLocation BEACON_BUTTON = vanilla("container/beacon/button");
    private static final ResourceLocation BEACON_SELECTED = vanilla("container/beacon/button_selected");
    private static final ResourceLocation BEACON_HIGHLIGHTED = vanilla("container/beacon/button_highlighted");
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
            icon(g, getX() + 8, getY() + 5, index * 16, 0, 16, 16);
        }
    }

    /** Кнопка-переключатель 22x22 с иконкой 16x16 — ванильная кнопка маяка. Выбранная выглядит нажатой. */
    static class Toggle extends Widget {

        final int value, iconU, iconV;
        boolean checked;

        Toggle(int x, int y, int value, int iconU, int iconV, Component name, Runnable action) {
            super(x, y, 22, 22, name, action);
            this.value = value;
            this.iconU = iconU;
            this.iconV = iconV;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blitSprite(checked ? BEACON_SELECTED : isHovered() ? BEACON_HIGHLIGHTED : BEACON_BUTTON, getX(), getY(), 22, 22);
            icon(g, getX() + 3, getY() + 3, iconU, iconV, 16, 16);
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
