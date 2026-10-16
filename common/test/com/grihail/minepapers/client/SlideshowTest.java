package com.grihail.minepapers.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Слайдшоу источника, список которого растёт по мере загрузки (папка Яндекс Диска страницами). */
class SlideshowTest {

    private static List<PictureManager.Picture> pictures(int n) {
        List<PictureManager.Picture> list = new ArrayList<PictureManager.Picture>();
        for (int i = 0; i < n; i++) list.add(new PictureManager.Picture("p" + i, () -> null));
        return list;
    }

    @Test
    void slideStaysUntilItsIntervalEndsWhileListGrows() {
        PictureManager.Source source = new PictureManager.Source("test");
        List<PictureManager.Picture> all = pictures(500);
        int interval = 30, ticks = interval * 20;
        long t = 5 * ticks + 7;
        source.pictures = new ArrayList<PictureManager.Picture>(all.subList(0, 20));
        PictureManager.Picture first = source.slide(t, interval, 0, 42);
        for (int n = 220; n <= 500; n += 140) {
            source.pictures = new ArrayList<PictureManager.Picture>(all.subList(0, n));
            assertSame(first, source.slide(t + 100, interval, 0, 42), "same interval, list of " + n);
        }
        // Следующий интервал — уже по полному списку; за круг каждая картинка показывается раз, поэтому подряд не повторяется.
        assertNotSame(first, source.slide(t + ticks, interval, 0, 42));
    }
}
