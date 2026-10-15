package com.grihail.minepapers.forge;

import java.util.Collection;

import com.grihail.minepapers.MinePapers;
import com.grihail.minepapers.gametest.PictureGameTests;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;

/** Общие игровые тесты (PictureGameTests) для Forge: класс с @GameTestHolder он находит сам. */
@GameTestHolder(MinePapers.MODID)
public class ForgeGameTests {

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return PictureGameTests.all();
    }
}
