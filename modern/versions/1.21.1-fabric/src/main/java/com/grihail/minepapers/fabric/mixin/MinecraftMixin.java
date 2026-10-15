package com.grihail.minepapers.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.grihail.minepapers.client.ModClient;

import net.minecraft.client.Minecraft;

/** Клики мышью до ванильной обработки — как событие NeoForge InputEvent.InteractionKeyMappingTriggered: картина, до которой ванильный выбор цели не дотянулся, становится целью клика, а ЛКМ по ней не ломает блок за ней (см. PictureControls.onInteraction). */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void minepapers$startAttack(CallbackInfoReturnable<Boolean> cir) {
        if (ModClient.CONTROLS.onInteraction(true)) cir.setReturnValue(false);
    }

    /** Зажатая ЛКМ продолжает копать блок под прицелом — не тот, что за картиной. */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void minepapers$continueAttack(boolean leftClick, CallbackInfo ci) {
        if (leftClick && ModClient.CONTROLS.onInteraction(true)) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void minepapers$startUseItem(CallbackInfo ci) {
        ModClient.CONTROLS.onInteraction(false);
    }

    @Inject(method = "pickBlock", at = @At("HEAD"))
    private void minepapers$pickBlock(CallbackInfo ci) {
        ModClient.CONTROLS.onInteraction(false);
    }
}
