package com.grihail.minepapers.client;

//? if forge && >=26.1 {
/*import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;

/^* События клиента Forge 26.x, которые в разных 26.x устроены по-разному: код движка (modern/forge26) общий для всех 26.x и без условий версий, поэтому эти события подключаются здесь. *^/
public final class ForgeClientEvents {

    private ForgeClientEvents() {}

    public static void register() {
        // Клики мышью (до ванильной обработки): ЛКМ, ПКМ и колёсико по картине, до которой ванильный выбор не дотянулся.
        // Слушатель с результатом (true — отменить событие): у шины есть и слушатели без него, поэтому лямбды — с return.
        //? if >=26.3 {
        /^InputEvent.InteractionKeyMappingTriggered.Attack.BUS.addListener(e -> { return interaction(e, true); });
        InputEvent.InteractionKeyMappingTriggered.UseItem.BUS.addListener(e -> { return interaction(e, false); });
        InputEvent.InteractionKeyMappingTriggered.PickBlock.BUS.addListener(e -> { return interaction(e, false); });
        ^///?} else {
        InputEvent.InteractionKeyMappingTriggered.BUS.addListener(e -> { return interaction(e, e.isAttack()); });
        //?}
        // Обводка блока под прицелом: за картиной — не рисуется, с картиной в руке — ещё и контур будущей картины.
        RenderHighlightEvent.Block.BUS.addListener(e -> {
            if (Minecraft.getInstance().level == null) return false;
            PictureControls.Highlight h = ModClient.CONTROLS.onHighlightBlock(e.getCamera().position(), e.getTarget());
            if (h != null) e.setCustomRenderer(callback(h));
            return false;
        });
    }

    /^* @return true — отменить ванильную обработку клика (и не махать рукой) *^/
    private static boolean interaction(InputEvent.InteractionKeyMappingTriggered e, boolean attack) {
        if (!ModClient.CONTROLS.onInteraction(attack)) return false;
        e.setSwingHand(false);
        return true;
    }

    private static RenderHighlightEvent.Callback callback(PictureControls.Highlight highlight) {
        //? if >=26.2 {
        /^return (collector, poseStack, state) -> {
            Recorder recorder = new Recorder();
            highlight.draw(poseStack, recorder);
            recorder.submit(collector, poseStack);
        };
        ^///?} else {
        // В 26.1 колбэк зовут дважды: для обычных и для полупрозрачных блоков; рисуем один раз.
        return (buffers, poseStack, translucent, state) -> {
            if (!translucent) highlight.draw(poseStack, buffers::getBuffer);
        };
        //?}
    }
}
*///?}
