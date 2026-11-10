package com.grihail.minepapers.client;

import org.lwjgl.opengl.GL11;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/** Рисует картину: саму картину строит общий PictureMesh, здесь — вывод его вершин через Tessellator и подпись с заголовком. */
public class RenderPicture extends Render {

    /** Значок "18+" на скрытой картине NSFW. */
    private static final ResourceLocation NSFW_MARK = new ResourceLocation(MinePapers.MODID, "textures/misc/nsfw.png");

    private final Mesh mesh = new Mesh();

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        EntityPicture picture = (EntityPicture) entity;
        PictureFrame tile = picture.frame;
        World world = picture.worldObj;
        PictureManager.Variant shown = PictureMesh.shown(tile, world.provider.dimensionId, picture.getEntityId(),
            world.getTotalWorldTime());

        GL11.glPushMatrix();
        // Отсчёт от угла клетки рамки у поверхности, а не от позиции сущности (центра холста).
        double[] origin = FrameGeometry.origin(tile);
        GL11.glTranslated(origin[0] - RenderManager.renderPosX, origin[1] - RenderManager.renderPosY, origin[2] - RenderManager.renderPosZ);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        mesh.world = world;
        mesh.draw(tile, shown, tile.selfGlow(), tile.usesCanvas() ? FrameSkin.canvas(tile.canvas) : FrameSkin.plainCanvas(),
            FrameSkin.border(tile.border),
            tile.glass ? FrameSkin.glass(tile.pane) : null);
        mesh.world = null;

        // Возвращаем состояние OpenGL как было: моды вроде Angelica следят за ним и не любят "хвостов".
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        bindTexture(TextureMap.locationBlocksTexture);
        GL11.glPopMatrix();

        // Под прицелом с зажатым Shift — заголовок картины и курсивом ник подписавшего, как имя переименованного предмета в рамке.
        MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
        String label = label(tile);
        // Shift не нужен, если в конфиге titleNeedsSneak = false.
        if (!label.isEmpty() && hit != null && hit.entityHit == picture && (!ModConfig.titleNeedsSneak || GuiScreen.isShiftKeyDown())
            && Minecraft.isGuiEnabled()) {
            double[] p = FrameGeometry.labelPos(tile);
            titleLabel(label, p[0] - RenderManager.renderPosX, p[1] - RenderManager.renderPosY,
                p[2] - RenderManager.renderPosZ);
        }
    }

    /** Заголовок и ник подписавшего курсивом: "Заголовок - ник". */
    private static String label(PictureFrame tile) {
        if (!tile.isSigned()) return tile.title;
        return (tile.title.isEmpty() ? "" : tile.title + " - ") + "§o" + tile.signerName;
    }

    /** Подпись над миром, как ванильная табличка с именем (Render.func_147906_a): тёмная подложка, текст виден и сквозь стены (тускло). */
    private void titleLabel(String text, double x, double y, double z) {
        FontRenderer font = getFontRendererFromRenderManager();
        Tessellator t = Tessellator.instance;
        float scale = 0.025f;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0f, 1f, 0f);
        GL11.glRotatef(-renderManager.playerViewY, 0f, 1f, 0f);
        GL11.glRotatef(renderManager.playerViewX, 1f, 0f, 0f);
        GL11.glScalef(-scale, -scale, scale);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        int half = font.getStringWidth(text) / 2;
        t.startDrawingQuads();
        t.setColorRGBA_F(0f, 0f, 0f, 0.25f);
        t.addVertex(-half - 1, -1, 0);
        t.addVertex(-half - 1, 8, 0);
        t.addVertex(half + 1, 8, 0);
        t.addVertex(half + 1, -1, 0);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        font.drawString(text, -half, 0, 0x20FFFFFF);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        font.drawString(text, -half, 0, 0xFFFFFFFF);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glPopMatrix();
    }

    /** Вершины картины — в Tessellator; новая текстура — новая порция (холст и рамка могут лежать в разных атласах). */
    private final class Mesh extends PictureMesh {

        World world;
        private final Tessellator t = Tessellator.instance;
        private boolean drawing;
        private int kind;
        private Object texture;

        @Override
        protected void texture(int kind, Object texture) {
            if (drawing && kind == this.kind && texture == this.texture) return;
            finish();
            this.kind = kind;
            this.texture = texture;
            if (kind == PICTURE || kind == DECAL) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, (Integer) texture);
            } else {
                bindTexture(kind == MARK ? NSFW_MARK : (ResourceLocation) texture);
            }
            if (kind == DECAL) {
                // Прижимаем к стене без мерцания
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(-1f, -10f);
            }
            t.startDrawingQuads();
            drawing = true;
        }

        @Override
        protected void finish() {
            if (!drawing) return;
            t.draw();
            if (kind == DECAL) GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            drawing = false;
        }

        @Override
        protected void emit(double x, double y, double z, double tu, double tv) {
            t.setBrightness(light);
            t.setColorOpaque_F(r, g, b);
            t.addVertexWithUV(x, y, z, tu, tv);
        }

        @Override
        protected int lightAt(int x, int y, int z) {
            return world.getLightBrightnessForSkyBlocks(x, y, z, 0);
        }

        @Override
        protected boolean opaqueAt(int x, int y, int z) {
            return world.getBlock(x, y, z).isOpaqueCube();
        }
    }
}
