package com.grihail.minepapers.forgetests;

import java.util.function.Consumer;

import com.grihail.minepapers.gametest.PictureGameTests;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

/** Общие игровые тесты (PictureGameTests) для Forge 26.x — отдельным модом только в запуске тестов: у Forge наборы исходников — отдельные модули, и данные тестов (data/minepapers/test_instance, площадка) подключаются как данные мода. Функции тестов — в реестр под его именем, описания тестов пишет сборка (mc26.gradle). */
@Mod(ForgeGameTests.MODID)
public final class ForgeGameTests {

    public static final String MODID = "minepapers_tests";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, MODID);

    public ForgeGameTests(FMLJavaModLoadingContext context) {
        PictureGameTests.functions().forEach((name, test) -> FUNCTIONS.register(name, () -> test));
        FUNCTIONS.register(context.getModBusGroup());
    }
}
