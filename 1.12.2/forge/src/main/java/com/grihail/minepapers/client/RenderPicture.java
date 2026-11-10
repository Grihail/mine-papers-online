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
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

/** Рисует картину: саму картину строит общий PictureMesh, здесь — вывод его вершин через BufferBuilder и подпись с заголовком. */
public class RenderPicture extends Render<EntityPicture> {

    /** Значок "18+" на скрытой картине NSFW. */
    private static final ResourceLocation NSFW_MARK = new ResourceLocation(MinePapers.MODID, "textures/misc/nsfw.png");

    private final Mesh mesh = new Mesh();

    public RenderPicture(RenderManager manager) {
        super(manager);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityPicture entity) {
        return null;
    }

    /** x, y, z — место сущности относительно камеры; картина не движется, поэтому камера — позиция сущности минус они. */
    @Override
    public void doRender(EntityPicture picture, double x, double y, double z, float yaw, float partialTicks) {
        PictureFrame tile = picture.frame;
        World world = picture.world;
        double camX = picture.posX - x, camY = picture.posY - y, camZ = picture.posZ - z;
        PictureManager.Variant shown = PictureMesh.shown(tile, world.provider.getDimension(), picture.getEntityId(),
            world.getTotalWorldTime());

        GlStateManager.pushMatrix();
        // Отсчёт от угла клетки рамки у поверхности, а не от позиции сущности (центра холста).
        double[] origin = FrameGeometry.origin(tile);
        GlStateManager.translate(origin[0] - camX, origin[1] - camY, origin[2] - camZ);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1f, 1f, 1f, 1f);

        mesh.world = world;
        mesh.draw(tile, shown, tile.selfGlow(), tile.usesCanvas() ? FrameSkin.canvas(tile.canvas) : FrameSkin.plainCanvas(),
            FrameSkin.border(tile.border),
            tile.glass ? FrameSkin.glass(tile.pane) : null);
        mesh.world = null;

        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.popMatrix();

        // Под прицелом с зажатым Shift — заголовок картины и курсивом ник подписавшего, как имя переименованного предмета в рамке.
        RayTraceResult hit = Minecraft.getMinecraft().objectMouseOver;
        String label = label(tile);
        // Shift не нужен, если в конфиге titleNeedsSneak = false.
        if (!label.isEmpty() && hit != null && hit.entityHit == picture && (!ModConfig.titleNeedsSneak || GuiScreen.isShiftKeyDown())
            && Minecraft.isGuiEnabled()) {
            double[] p = FrameGeometry.labelPos(tile);
            titleLabel(label, p[0] - camX, p[1] - camY, p[2] - camZ);
        }
    }

    /** Заголовок и ник подписавшего курсивом: "Заголовок - ник". */
    private static String label(PictureFrame tile) {
        if (!tile.isSigned()) return tile.title;
        return (tile.title.isEmpty() ? "" : tile.title + " - ") + "§o" + tile.signerName;
    }

    /** Подпись над миром, как ванильная табличка с именем (EntityRenderer.drawNameplate): тёмная подложка, текст виден и сквозь стены (тускло). */
    private void titleLabel(String text, double x, double y, double z) {
        FontRenderer font = getFontRendererFromRenderManager();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        float scale = 0.025f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.glNormal3f(0f, 1f, 0f);
        GlStateManager.rotate(-renderManager.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate((renderManager.options.thirdPersonView == 2 ? -1 : 1) * renderManager.playerViewX, 1f, 0f, 0f);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableTexture2D();
        int half = font.getStringWidth(text) / 2;
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        b.pos(-half - 1, -1, 0).color(0f, 0f, 0f, 0.25f).endVertex();
        b.pos(-half - 1, 8, 0).color(0f, 0f, 0f, 0.25f).endVertex();
        b.pos(half + 1, 8, 0).color(0f, 0f, 0f, 0.25f).endVertex();
        b.pos(half + 1, -1, 0).color(0f, 0f, 0f, 0.25f).endVertex();
        t.draw();
        GlStateManager.enableTexture2D();
        font.drawString(text, -half, 0, 0x20FFFFFF);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        font.drawString(text, -half, 0, 0xFFFFFFFF);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    /** Вершины картины — в BufferBuilder (позиция, цвет, текстура, свет); новая текстура — новая порция (холст и рамка могут лежать в разных текстурах). */
    private final class Mesh extends PictureMesh {

        World world;
        private final Tessellator t = Tessellator.getInstance();
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
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
                GlStateManager.bindTexture((Integer) texture);
            } else {
                bindTexture(kind == MARK ? NSFW_MARK : (ResourceLocation) texture);
            }
            if (kind == DECAL) {
                // Прижимаем к стене без мерцания
                GlStateManager.enablePolygonOffset();
                GlStateManager.doPolygonOffset(-1f, -10f);
            }
            t.getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            drawing = true;
        }

        @Override
        protected void finish() {
            if (!drawing) return;
            t.draw();
            if (kind == DECAL) GlStateManager.disablePolygonOffset();
            drawing = false;
        }

        @Override
        protected void emit(double x, double y, double z, double tu, double tv) {
            t.getBuffer().pos(x, y, z).color(r, g, b, 1f).tex(tu, tv).lightmap(light >> 16 & 0xFFFF, light & 0xFFFF).endVertex();
        }

        @Override
        protected int lightAt(int x, int y, int z) {
            return world.getCombinedLight(pos.setPos(x, y, z), 0);
        }

        @Override
        protected boolean opaqueAt(int x, int y, int z) {
            return world.getBlockState(pos.setPos(x, y, z)).isOpaqueCube();
        }
    }
}
