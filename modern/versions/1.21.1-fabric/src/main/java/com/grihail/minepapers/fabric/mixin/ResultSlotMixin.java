package com.grihail.minepapers.fabric.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.grihail.minepapers.item.RecipePictureFrame;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;

/** Игрок забрал результат крафта (до того как ингредиенты списаны) — как событие NeoForge PlayerEvent.ItemCraftedEvent: блоки из слотов материалов картин в сетке возвращаются ему (RecipePictureFrame.onCrafted). */
@Mixin(ResultSlot.class)
abstract class ResultSlotMixin {

    @Shadow
    @Final
    private Player player;

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void minepapers$onCrafted(ItemStack stack, CallbackInfo ci) {
        RecipePictureFrame.onCrafted(player, craftSlots);
    }
}
