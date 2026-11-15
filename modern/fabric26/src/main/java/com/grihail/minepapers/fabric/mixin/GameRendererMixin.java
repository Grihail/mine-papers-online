package com.grihail.minepapers.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.grihail.minepapers.client.ModClient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

/** Начало кадра, до его сбора - как событие NeoForge RenderFrameEvent.Pre: картина под прицелом ищется раз за кадр, по ней RenderPicture рисует обводку. У Fabric API такого события нет. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Inject(method = "extract", at = @At("HEAD"))
    private void minepapers$extract(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null) ModClient.CONTROLS.onRenderLevel(deltaTracker.getGameTimeDeltaPartialTick(true));
    }
}
