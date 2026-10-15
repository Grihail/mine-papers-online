package com.grihail.minepapers.client;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Рисует картину: саму картину строит общий PictureMesh, здесь — вывод его вершин, обводка под прицелом и подпись с заголовком. */
public class RenderPicture extends EntityRenderer<EntityPicture> {

    /** Значок "18+" на скрытой картине NSFW. */
    private static final ResourceLocation NSFW_MARK = MinePapers.id("textures/misc/nsfw.png");

    private final Mesh mesh = new Mesh();

    public RenderPicture(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityPicture entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(EntityPicture picture, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        PictureFrame tile = picture.frame;
        Level level = picture.level();
        PictureManager.Variant shown = PictureMesh.shown(tile, level.dimension().location(), picture.getId(), level.getGameTime());

        poseStack.pushPose();
        // Отсчёт от угла клетки рамки у поверхности, а не от позиции сущности (центра холста).
        double[] origin = FrameGeometry.origin(tile);
        Vec3 at = picture.getPosition(partialTick);
        poseStack.translate(origin[0] - at.x, origin[1] - at.y, origin[2] - at.z);
        mesh.level = level;
        mesh.pose = poseStack.last();
        mesh.buffers = buffers;
        mesh.draw(tile, shown, tile.selfGlow(), tile.usesCanvas() ? FrameSkin.canvas(tile.canvas) : FrameSkin.plainCanvas(),
            FrameSkin.border(tile.border));
        mesh.level = null;
        mesh.pose = null;
        mesh.buffers = null;

        // Обводка хитбокса под прицелом — как у блоков. Здесь, а не в мировых координатах: так она повторяет картину,
        // даже если её позу меняет кто-то ещё (движущиеся конструкции других модов).
        if (PictureControls.isAimed(picture)) {
            AABB box = FrameGeometry.canvasBox(tile).inflate(0.002).move(-origin[0], -origin[1], -origin[2]);
            LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box, 0f, 0f, 0f, 0.4f);
        }
        poseStack.popPose();

        // Под прицелом с зажатым Shift (или сразу, если в конфиге titleNeedsSneak = false) — заголовок картины и курсивом ник подписавшего, как имя переименованного предмета в рамке.
        if ((!tile.title.isEmpty() || tile.isSigned()) && PictureControls.isAimed(picture) && (!ModConfig.titleNeedsSneak || Screen.hasShiftDown())
            && Minecraft.renderNames()) {
            double[] p = FrameGeometry.labelPos(tile);
            titleLabel(label(tile), p[0] - at.x, p[1] - at.y, p[2] - at.z, poseStack, buffers, packedLight);
        }
    }

    /** Заголовок и ник подписавшего курсивом: "Заголовок - ник". */
    private static Component label(PictureFrame tile) {
        MutableComponent text = Component.literal(tile.title);
        if (!tile.isSigned()) return text;
        if (!tile.title.isEmpty()) text.append(" - ");
        return text.append(Component.literal(tile.signerName).withStyle(ChatFormatting.ITALIC));
    }

    /** Подпись над миром, как ванильная табличка с именем (EntityRenderer.renderNameTag): тёмная подложка, текст виден и сквозь стены (тускло). */
    private void titleLabel(Component text, double x, double y, double z, PoseStack poseStack, MultiBufferSource buffers,
                            int packedLight) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.025f, -0.025f, 0.025f);
        Matrix4f matrix = poseStack.last().pose();
        int background = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255f) << 24;
        Font font = getFont();
        float left = -font.width(text) / 2f;
        font.drawInBatch(text, left, 0, 0x20FFFFFF, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, background,
            packedLight);
        font.drawInBatch(text, left, 0, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    /** Вершины картины — в буферы ванильных типов отрисовки сущностей. */
    private static final class Mesh extends PictureMesh {

        Level level;
        PoseStack.Pose pose;
        MultiBufferSource buffers;
        private VertexConsumer out;
        private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

        @Override
        protected void texture(int kind, Object texture) {
            switch (kind) {
                case ATLAS:
                    out = buffers.getBuffer(RenderType.entityCutoutNoCull((ResourceLocation) texture));
                    break;
                case PICTURE:
                    out = buffers.getBuffer(RenderType.entityTranslucent((ResourceLocation) texture));
                    break;
                case DECAL:
                    out = buffers.getBuffer(PictureRenderTypes.decal((ResourceLocation) texture));
                    break;
                default:
                    out = buffers.getBuffer(RenderType.entityCutoutNoCull(NSFW_MARK));
            }
        }

        @Override
        protected void finish() {
            out = null;
        }

        /** Нормаль у всех вершин — вверх: так ванильный шейдер сущностей не затемняет картину в зависимости от стороны света, а затенение граней PictureMesh делает сам, как у блоков. */
        @Override
        protected void emit(double x, double y, double z, double tu, double tv) {
            out.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(r, g, b, 1f)
                .setUv((float) tu, (float) tv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 1f, 0f);
        }

        @Override
        protected int lightAt(int x, int y, int z) {
            return LevelRenderer.getLightColor(level, cell.set(x, y, z));
        }

        @Override
        protected boolean opaqueAt(int x, int y, int z) {
            cell.set(x, y, z);
            return level.getBlockState(cell).isSolidRender(level, cell);
        }
    }
}
