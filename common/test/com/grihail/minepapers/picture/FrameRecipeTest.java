package com.grihail.minepapers.picture;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class FrameRecipeTest {

    @SuppressWarnings("deprecation")
    private static FrameRecipe parse(String json) {
        return FrameRecipe.fromJson(new JsonParser().parse(json));
    }

    @Test
    void defaultIsShapelessInAnyOrder() {
        FrameRecipe r = FrameRecipe.DEFAULT;
        String[] grid = { null, "minecraft:glass_pane", null, "minecraft:painting", "minecraft:writable_book", null, null, null, "minecraft:item_frame" };
        assertTrue(r.matches(grid, 3, 3));
        grid[1] = null;
        assertFalse(r.matches(grid, 3, 3)); // не хватает панели
        grid[1] = "minecraft:glass_pane";
        grid[0] = "minecraft:stick";
        assertFalse(r.matches(grid, 3, 3)); // лишний предмет
    }

    @Test
    void shapedMovesOverTheGrid() {
        FrameRecipe r = parse("{ \"pattern\": [\"P\", \"F\"], \"key\": { \"P\": \"minecraft:paper\", \"F\": \"mod:frame\" } }");
        assertTrue(r.shaped());
        String[] grid = { null, null, null, null, "minecraft:paper", null, null, "mod:frame", null };
        assertTrue(r.matches(grid, 3, 3));
        grid[4] = "minecraft:stone";
        assertFalse(r.matches(grid, 3, 3));
        assertFalse(r.matches(new String[] { "mod:frame", "minecraft:paper" }, 1, 2)); // перевёрнуто
    }

    @Test
    void metaIsIgnoredWhenMatchingIds() {
        FrameRecipe r = parse("{ \"ingredients\": [\"minecraft:wool:14\"] }");
        assertTrue(r.matches(new String[] { "minecraft:wool" }, 1, 1));
        assertArrayEquals(new String[] { "minecraft:wool", "14" }, FrameRecipe.split("minecraft:wool:14"));
        assertArrayEquals(new String[] { "minecraft:wool", null }, FrameRecipe.split("minecraft:wool"));
    }

    @Test
    void badRecipesAreRejected() {
        assertThrows(Exception.class, () -> parse("{ \"pattern\": [\"ABCD\"], \"key\": { \"A\": \"x:y\" } }"));
        assertThrows(Exception.class, () -> parse("{ \"pattern\": [\"AB\"], \"key\": { \"A\": \"x:y\" } }"));
        assertThrows(Exception.class, () -> parse("{ \"ingredients\": [] }"));
    }

    @Test
    void writtenJsonReadsBack() {
        FrameRecipe r = parse("{ \"pattern\": [\"PPP\", \"PFP\", \"PPP\"], \"key\": { \"P\": \"minecraft:paper\", \"F\": \"minecraft:item_frame\" } }");
        FrameRecipe back = parse(r.toJson(""));
        assertArrayEquals(r.pattern, back.pattern);
        assertEquals(r.key, back.key);
        assertEquals(FrameRecipe.DEFAULT.ingredients, parse(FrameRecipe.DEFAULT.toJson("")).ingredients);
    }
}
