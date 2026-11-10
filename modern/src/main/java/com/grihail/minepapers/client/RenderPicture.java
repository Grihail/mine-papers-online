package com.grihail.minepapers.client;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.ModConfig;
import com.grihail.minepapers.picture.EntityPicture;
import com.grihail.minepapers.picture.FrameGeometry;
import com.grihail.minepapers.picture.PictureFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
//? if >=26.1 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
*///?} else {
import org.joml.Matrix4f;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.inventory.InventoryMenu;
//?}
//? if >=26.2 {
/*import net.minecraft.util.LightCoordsUtil;
*///?} else if >=26.1 {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}

/** Рисует картину: саму картину строит общий PictureMesh, здесь — вывод его вершин, обводка под прицелом и подпись с заголовком. */
//? if >=26.1 {
/*public class RenderPicture extends EntityRenderer<EntityPicture, RenderPicture.State> {
*///?} else {
public class RenderPicture extends EntityRenderer<EntityPicture> {
//?}

    /** Значок "18+" на скрытой картине NSFW. */
    private static final ResourceLocation NSFW_MARK = MinePapers.id("textures/misc/nsfw.png");

    private final Mesh mesh = new Mesh();

    public RenderPicture(EntityRendererProvider.Context context) {
        super(context);
    }

    //? if >=26.1 {
    /*// С 26.1 рендерер сущности рисует не саму сущность, а её снимок за кадр (состояние), и отдаёт геометрию в SubmitNodeCollector.
    // Картине снимок не нужен: всё, что нужно, - в её настройках, их и рисуем.
    public static final class State extends EntityRenderState {
        EntityPicture picture;
        float partialTick;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EntityPicture picture, State state, float partialTick) {
        super.extractRenderState(picture, state, partialTick);
        state.picture = picture;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        EntityPicture picture = state.picture;
        if (picture == null) return;
        Recorder recorder = new Recorder();
        draw(picture, state.partialTick, poseStack, recorder);
        recorder.submit(collector, poseStack);
        if (showsLabel(picture.frame, picture)) {
            Vec3 at = picture.getPosition(state.partialTick);
            double[] p = FrameGeometry.labelPos(picture.frame);
            //? if >=26.2 {
            /^collector.submitNameTag(poseStack, new Vec3(p[0] - at.x, p[1] - at.y, p[2] - at.z), 0, label(picture.frame), true,
                state.lightCoords, camera);
            ^///?} else {
            collector.submitNameTag(poseStack, new Vec3(p[0] - at.x, p[1] - at.y, p[2] - at.z), 0, label(picture.frame), true,
                state.lightCoords, state.distanceToCameraSq, camera);
            //?}
        }
    }
    *///?} else {
    @Override
    public ResourceLocation getTextureLocation(EntityPicture entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public void render(EntityPicture picture, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        draw(picture, partialTick, poseStack, buffers::getBuffer);
        PictureFrame tile = picture.frame;
        if (showsLabel(tile, picture)) {
            Vec3 at = picture.getPosition(partialTick);
            double[] p = FrameGeometry.labelPos(tile);
            titleLabel(label(tile), p[0] - at.x, p[1] - at.y, p[2] - at.z, poseStack, buffers, packedLight);
        }
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
    //?}

    /** Сама картина и обводка под прицелом; poseStack стоит в позиции сущности. */
    private void draw(EntityPicture picture, float partialTick, PoseStack poseStack, VertexSink sink) {
        PictureFrame tile = picture.frame;
        Level level = picture.level();
        //? if >=26.1 {
        /*ResourceLocation dimension = level.dimension().identifier();
        *///?} else {
        ResourceLocation dimension = level.dimension().location();
        //?}
        PictureManager.Variant shown = PictureMesh.shown(tile, dimension, picture.getId(), level.getGameTime());

        poseStack.pushPose();
        // Отсчёт от угла клетки рамки у поверхности, а не от позиции сущности (центра холста).
        double[] origin = FrameGeometry.origin(tile);
        Vec3 at = picture.getPosition(partialTick);
        poseStack.translate(origin[0] - at.x, origin[1] - at.y, origin[2] - at.z);
        mesh.level = level;
        mesh.pose = poseStack.last();
        mesh.sink = sink;
        mesh.draw(tile, shown, tile.selfGlow(), tile.usesCanvas() ? FrameSkin.canvas(tile.canvas) : FrameSkin.plainCanvas(),
            FrameSkin.border(tile.border),
            tile.glass ? FrameSkin.glass(tile.pane) : null);
        mesh.level = null;
        mesh.pose = null;
        mesh.sink = null;

        // Обводка хитбокса под прицелом — как у блоков. Здесь, а не в мировых координатах: так она повторяет картину,
        // даже если её позу меняет кто-то ещё (движущиеся конструкции других модов).
        if (PictureControls.isAimed(picture)) {
            AABB box = FrameGeometry.canvasBox(tile).inflate(0.002).move(-origin[0], -origin[1], -origin[2]);
            //? if >=26.1 {
            /*PictureRenderTypes.lineBox(sink.buffer(PictureRenderTypes.lines()), poseStack.last(), box, 0f, 0f, 0f, 0.4f, 2f);
            *///?} else {
            LevelRenderer.renderLineBox(poseStack, sink.buffer(PictureRenderTypes.lines()), box, 0f, 0f, 0f, 0.4f);
            //?}
        }
        poseStack.popPose();
    }

    /** Под прицелом с зажатым Shift (или сразу, если в конфиге titleNeedsSneak = false) — заголовок картины и курсивом ник подписавшего, как имя переименованного предмета в рамке. */
    private static boolean showsLabel(PictureFrame tile, EntityPicture picture) {
        return (!tile.title.isEmpty() || tile.isSigned()) && PictureControls.isAimed(picture)
            && (!ModConfig.titleNeedsSneak || ModClient.shiftDown()) && !ModClient.hudHidden();
    }

    /** Заголовок и ник подписавшего курсивом: "Заголовок - ник". */
    private static Component label(PictureFrame tile) {
        MutableComponent text = Component.literal(tile.title);
        if (!tile.isSigned()) return text;
        if (!tile.title.isEmpty()) text.append(" - ");
        return text.append(Component.literal(tile.signerName).withStyle(ChatFormatting.ITALIC));
    }

    /** Вершины картины — в буферы ванильных типов отрисовки сущностей. */
    private static final class Mesh extends PictureMesh {

        Level level;
        PoseStack.Pose pose;
        VertexSink sink;
        private VertexConsumer out;
        private final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();

        @Override
        protected void texture(int kind, Object texture) {
            switch (kind) {
                case ATLAS:
                    out = sink.buffer(PictureRenderTypes.cutout((ResourceLocation) texture));
                    break;
                case PICTURE:
                    out = sink.buffer(PictureRenderTypes.translucent((ResourceLocation) texture));
                    break;
                case DECAL:
                    out = sink.buffer(PictureRenderTypes.decal((ResourceLocation) texture));
                    break;
                case GLASS:
                    out = sink.buffer(PictureRenderTypes.translucent((ResourceLocation) texture));
                    break;
                default:
                    out = sink.buffer(PictureRenderTypes.cutout(NSFW_MARK));
            }
        }

        @Override
        protected void finish() {
            out = null;
        }

        /** Нормаль у всех вершин — вверх: так ванильный шейдер сущностей не затемняет картину в зависимости от стороны света, а затенение граней PictureMesh делает сам, как у блоков. */
        @Override
        protected void emit(double x, double y, double z, double tu, double tv) {
            //? if >=1.21 {
            out.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(r, g, b, 1f)
                .setUv((float) tu, (float) tv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 1f, 0f);
            //?} else {
            /*out.vertex(pose.pose(), (float) x, (float) y, (float) z)
                .color(r, g, b, 1f)
                .uv((float) tu, (float) tv)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0f, 1f, 0f)
                .endVertex();
            *///?}
        }

        @Override
        protected int lightAt(int x, int y, int z) {
            //? if >=26.2 {
            /*return LightCoordsUtil.getLightCoords(level, cell.set(x, y, z));
            *///?} else if >=26.1 {
            /*return LevelRenderer.getLightCoords(level, cell.set(x, y, z));
            *///?} else {
            return LevelRenderer.getLightColor(level, cell.set(x, y, z));
            //?}
        }

        @Override
        protected boolean opaqueAt(int x, int y, int z) {
            cell.set(x, y, z);
            //? if >=26.1 {
            /*return level.getBlockState(cell).isSolidRender();
            *///?} else {
            return level.getBlockState(cell).isSolidRender(level, cell);
            //?}
        }
    }
}
