package com.grihail.minepapers.neoforge;

import java.util.function.Consumer;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.gametest.PictureGameTests;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Общие игровые тесты (PictureGameTests) для NeoForge 26.x: функции тестов - в реестр, описания тестов пишет сборка (mc26.gradle). Класс есть только в запуске тестов. */
@Mod(MinePapers.MODID)
public class NeoForgeGameTests {

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, MinePapers.MODID);

    public NeoForgeGameTests(IEventBus modBus) {
        PictureGameTests.functions().forEach((name, test) -> FUNCTIONS.register(name, () -> test));
        FUNCTIONS.register(modBus);
    }
}
