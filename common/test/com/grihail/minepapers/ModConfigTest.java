package com.grihail.minepapers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModConfigTest {

    @AfterEach
    void defaults() {
        ModConfig.disassembleSigned = false;
        ModConfig.disassembleLocked = false;
    }

    @Test
    void ordinaryPicturesAlwaysCraft() {
        assertTrue(ModConfig.mayCraft(false, false, true));
        assertTrue(ModConfig.mayCraft(false, false, false));
    }

    @Test
    void signedAndLockedAreNeverCopied() {
        ModConfig.disassembleSigned = true;
        ModConfig.disassembleLocked = true;
        assertFalse(ModConfig.mayCraft(true, false, true));
        assertFalse(ModConfig.mayCraft(false, true, true));
    }

    @Test
    void disassemblyNeedsPermission() {
        assertFalse(ModConfig.mayCraft(false, true, false), "signed: forbidden by default");
        assertFalse(ModConfig.mayCraft(true, false, false), "locked: forbidden by default");
        ModConfig.disassembleSigned = true;
        assertTrue(ModConfig.mayCraft(false, true, false));
        assertFalse(ModConfig.mayCraft(true, true, false), "locked and signed needs both");
        ModConfig.disassembleLocked = true;
        assertTrue(ModConfig.mayCraft(true, true, false));
        ModConfig.disassembleSigned = false;
        assertTrue(ModConfig.mayCraft(true, false, false));
    }
}
