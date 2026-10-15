package com.grihail.minepapers.neoforge;

import java.util.Collection;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.gametest.PictureGameTests;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Общие игровые тесты версии (PictureGameTests) для NeoForge: класс с @GameTestHolder он находит сам. */
@GameTestHolder(MinePapers.MODID)
public class NeoForgeGameTests {

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return PictureGameTests.all();
    }
}
