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

    /** Онлайн-папка: ссылки Яндекс Диска узнаются (и без https://), чужие сервисы — нет. */
    @Test
    void yandexLinksAreRecognized() {
        assertTrue(PictureManager.isYandexLink("https://disk.yandex.ru/d/abc"));
        assertTrue(PictureManager.isYandexLink("disk.yandex.com/d/abc"));
        assertTrue(PictureManager.isYandexLink("https://yadi.sk/d/abc"));
        assertTrue(PictureManager.isYandexLink("https://disk.360.yandex.ru/d/abc"));
        assertFalse(PictureManager.isYandexLink("https://drive.google.com/drive/folders/abc"));
        assertFalse(PictureManager.isYandexLink("https://notyandex.com/d/abc"));
        assertFalse(PictureManager.isYandexLink("not a link"));
    }

    /** Google Диск: id папки из ссылок разного вида, картинки из страницы папки (подпапки и не картинки пропускаются). */
    @Test
    void googleDriveFolders() {
        String id = "1WQVKjL3OsS__Fth4eSZ4iNFblRnBfnUu";
        assertEquals(id, PictureManager.googleDriveId("https://drive.google.com/drive/folders/" + id + "?usp=sharing"));
        assertEquals(id, PictureManager.googleDriveId("https://drive.google.com/drive/u/1/folders/" + id));
        assertEquals("abc", PictureManager.googleDriveId("https://drive.google.com/file/d/abc/view"));
        assertNull(PictureManager.googleDriveId("https://disk.yandex.ru/d/abc"));
        assertFalse(PictureManager.isYandexLink("https://drive.google.com/drive/folders/" + id));

        // Записи — как на настоящей странице встраиваемого просмотра папки.
        List<PictureManager.Picture> list = PictureManager.parseGoogleFolder(GOOGLE_PAGE);
        assertEquals(2, list.size());
        assertEquals("_1708780261.personalami_2402flora1_000.png", list.get(0).name);
        assertEquals("cats & dogs.gif", list.get(1).name);
    }

    private static final String GOOGLE_PAGE = "<div class=\"flip-entries\"><div class=\"flip-entry\" id=\"entry-1BVB20xBeMa47KCGSsHlZ56L-nz2a5q7z\" tabindex=\"0\" role=\"link\"><div class=\"flip-entry-info\"><a href=\"https://drive.google.com/file/d/1BVB20xBeMa47KCGSsHlZ56L-nz2a5q7z/view?usp=drive_web\" target=\"_blank\"><div class=\"flip-entry-visual\"><div class=\"flip-entry-visual-card\"><div class=\"flip-entry-thumb\"><img src=\"https://lh3.googleusercontent.com/drive-storage/AJQWtBMd7SLqSHDJ6enxmlhfaY0YxE6VX4X-8xwoskJ6wa4xhq7b1MqqlR5l8sifuR2beYH9cN7VWbj5BoB5f8ScsMsFvULFhwNjy8O8gvm0=s190\" alt=\"PNG Image\"/></div></div></div><div class=\"flip-entry-list-icon\"><img src=\"https://drive-thirdparty.googleusercontent.com/16/type/image/png\" alt=\"\"/></div><div class=\"flip-entry-title\">_1708780261.personalami_2402flora1_000.png</div></a></div><div class=\"flip-entry-last-modified\"><div>Apr 30</div></div><div class=\"flip-entry\" id=\"entry-FOLDERID\" tabindex=\"0\" role=\"link\"><div class=\"flip-entry-info\"><a href=\"https://drive.google.com/file/d/FOLDERID/view?usp=drive_web\" target=\"_blank\"><div class=\"flip-entry-visual\"><div class=\"flip-entry-visual-card\"><div class=\"flip-entry-thumb\"><img src=\"https://lh3.googleusercontent.com/drive-storage/AJQWtBMd7SLqSHDJ6enxmlhfaY0YxE6VX4X-8xwoskJ6wa4xhq7b1MqqlR5l8sifuR2beYH9cN7VWbj5BoB5f8ScsMsFvULFhwNjy8O8gvm0=s190\" alt=\"PNG Image\"/></div></div></div><div class=\"flip-entry-list-icon\"><img src=\"https://drive-thirdparty.googleusercontent.com/16/type/application/vnd.google-apps.folder\" alt=\"\"/></div><div class=\"flip-entry-title\">Sub</div></a></div><div class=\"flip-entry-last-modified\"><div>Apr 30</div></div><div class=\"flip-entry\" id=\"entry-TEXTID\" tabindex=\"0\" role=\"link\"><div class=\"flip-entry-info\"><a href=\"https://drive.google.com/file/d/TEXTID/view?usp=drive_web\" target=\"_blank\"><div class=\"flip-entry-visual\"><div class=\"flip-entry-visual-card\"><div class=\"flip-entry-thumb\"><img src=\"https://lh3.googleusercontent.com/drive-storage/AJQWtBMd7SLqSHDJ6enxmlhfaY0YxE6VX4X-8xwoskJ6wa4xhq7b1MqqlR5l8sifuR2beYH9cN7VWbj5BoB5f8ScsMsFvULFhwNjy8O8gvm0=s190\" alt=\"PNG Image\"/></div></div></div><div class=\"flip-entry-list-icon\"><img src=\"https://drive-thirdparty.googleusercontent.com/16/type/text/plain\" alt=\"\"/></div><div class=\"flip-entry-title\">notes.txt</div></a></div><div class=\"flip-entry-last-modified\"><div>Apr 30</div></div><div class=\"flip-entry\" id=\"entry-GIFID\" tabindex=\"0\" role=\"link\"><div class=\"flip-entry-info\"><a href=\"https://drive.google.com/file/d/GIFID/view?usp=drive_web\" target=\"_blank\"><div class=\"flip-entry-visual\"><div class=\"flip-entry-visual-card\"><div class=\"flip-entry-thumb\"><img src=\"https://lh3.googleusercontent.com/drive-storage/AJQWtBMd7SLqSHDJ6enxmlhfaY0YxE6VX4X-8xwoskJ6wa4xhq7b1MqqlR5l8sifuR2beYH9cN7VWbj5BoB5f8ScsMsFvULFhwNjy8O8gvm0=s190\" alt=\"PNG Image\"/></div></div></div><div class=\"flip-entry-list-icon\"><img src=\"https://drive-thirdparty.googleusercontent.com/16/type/image/gif\" alt=\"\"/></div><div class=\"flip-entry-title\">cats &amp; dogs.gif</div></a></div><div class=\"flip-entry-last-modified\"><div>Apr 30</div></div></div>";
}
