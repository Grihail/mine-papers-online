package com.grihail.minepapers.client;

//? if fabric && >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/^* Обводка блока на Fabric 26.x: код движка (modern/fabric26) общий для всех 26.x и без условий версий, а вид этого события у них разный. *^/
public final class FabricClientEvents {

    private FabricClientEvents() {}

    public static void register() {
        // За картиной обводка не рисуется, с картиной в руке - вместо неё контур будущей картины. false - ванильную не рисовать.
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, state) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return true;
            PictureControls.Highlight h = ModClient.CONTROLS.onHighlightBlock(ctx.levelState().cameraRenderState.pos, hit);
            if (h == null) return true;
            //? if >=26.2 {
            /^Recorder recorder = new Recorder();
            h.draw(ctx.poseStack(), recorder);
            recorder.submit(ctx.submitNodeCollector(), ctx.poseStack());
            ^///?} else {
            h.draw(ctx.poseStack(), ctx.bufferSource()::getBuffer);
            //?}
            return false;
        });
    }
}
*///?}
