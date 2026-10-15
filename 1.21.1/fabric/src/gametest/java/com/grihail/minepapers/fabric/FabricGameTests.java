package com.grihail.minepapers.fabric;

import java.util.Collection;

import com.grihail.minepapers.gametest.PictureGameTests;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

/** Общие игровые тесты версии (PictureGameTests) для Fabric: класс указан в точке входа fabric-gametest тестового мода (src/gametest/resources/fabric.mod.json). */
public class FabricGameTests {

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return PictureGameTests.all();
    }
}
