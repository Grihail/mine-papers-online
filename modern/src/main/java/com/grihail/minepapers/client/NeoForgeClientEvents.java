package com.grihail.minepapers.client;

//? if neoforge && >=26.1 {
/*import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.CustomBlockOutlineRenderer;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;

/^* Обводка блока на NeoForge 26.x: код движка (modern/neoforge26) общий для всех 26.x и без условий версий, а вид этого колбэка у них разный. *^/
public final class NeoForgeClientEvents {

    private NeoForgeClientEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ExtractBlockOutlineRenderStateEvent e) -> {
            if (Minecraft.getInstance().level == null) return;
            PictureControls.Highlight h = ModClient.CONTROLS.onHighlightBlock(e.getCamera().position(), e.getHitResult());
            if (h != null) e.addCustomRenderer(renderer(h));
        });
    }

    private static CustomBlockOutlineRenderer renderer(PictureControls.Highlight highlight) {
        //? if >=26.2 {
        /^return (state, collector, poseStack, levelState) -> {
            Recorder recorder = new Recorder();
            highlight.draw(poseStack, recorder);
            recorder.submit(collector, poseStack);
            return true;
        };
        ^///?} else {
        return (state, buffers, poseStack, translucent, levelState) -> {
            if (!translucent) highlight.draw(poseStack, buffers::getBuffer);
            return true;
        };
        //?}
    }
}
*///?}
