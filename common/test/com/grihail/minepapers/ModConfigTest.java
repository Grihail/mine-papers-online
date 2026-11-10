package com.grihail.minepapers;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModConfigTest {

    @TempDir
    Path configDir;

    @AfterEach
    void defaults() {
        ModConfig.disassembleSigned = false;
        ModConfig.disassembleLocked = false;
        ModConfig.maxImageMb = 16;
    }

    private void loadConfig(String json) throws IOException {
        Files.write(configDir.resolve(ModConfig.FILE_NAME), json.getBytes(StandardCharsets.UTF_8));
        ModConfig.load(configDir.toFile());
    }

    @Test
    void maxImageMbIsReadAndConverted() throws IOException {
        loadConfig("{ \"maxImageMb\": 64 }");
        assertEquals(64, ModConfig.maxImageMb);
        assertEquals(64 * 1024 * 1024, ModConfig.maxImageBytes());
    }

    @Test
    void maxImageMbStaysInRange() throws IOException {
        loadConfig("{ \"maxImageMb\": 100000 }");
        assertEquals(ModConfig.MAX_IMAGE_MB_CEILING, ModConfig.maxImageMb);
        loadConfig("{ \"maxImageMb\": 0 }");
        assertEquals(1, ModConfig.maxImageMb);
    }

    @Test
    void oldConfigGetsMaxImageMb() throws IOException {
        loadConfig("{ \"hideNsfw\": false, \"titleNeedsSneak\": true, \"disassembleSigned\": false, \"disassembleLocked\": false }");
        assertEquals(16, ModConfig.maxImageMb);
        File file = configDir.resolve(ModConfig.FILE_NAME).toFile();
        assertTrue(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains("\"maxImageMb\": 16"));
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
