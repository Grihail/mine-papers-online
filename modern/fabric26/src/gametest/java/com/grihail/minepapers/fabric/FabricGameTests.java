package com.grihail.minepapers.fabric;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.gametest.PictureGameTests;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Общие игровые тесты (PictureGameTests) для Fabric 26.x - отдельным модом minepapers-gametest только в запуске тестов: функции тестов - в реестр под именем minepapers, описания тестов (data/minepapers/test_instance) пишет сборка (mc26.gradle). */
public class FabricGameTests implements ModInitializer {

    @Override
    public void onInitialize() {
        PictureGameTests.functions().forEach((name, test) -> Registry.register(BuiltInRegistries.TEST_FUNCTION, MinePapers.id(name), test));
    }
}
