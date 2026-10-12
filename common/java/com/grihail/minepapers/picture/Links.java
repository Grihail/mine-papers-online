package com.grihail.minepapers.picture;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Внешние ссылки в описании картины: в поле ввода они подчёркнуты, а в окне чужой заприваченной картины по ним можно кликнуть. Ссылка — только с http:// или https:// и доменом с точкой. */
public final class Links {

    private static final Pattern URL = Pattern.compile("https?://[\\w\\-]+(?:\\.[\\w\\-]+)*\\.[A-Za-z]{2,}(?::\\d+)?(?:[/?#]\\S*)?",
        Pattern.CASE_INSENSITIVE);
    /** Знаки, которыми обычно заканчивают фразу после ссылки, — не её часть. */
    private static final String TRAILING = ".,;:!?)]}'\"";

    private Links() {}

    /** Ссылки в тексте: пары {начало, конец} по порядку. */
    public static List<int[]> find(String text) {
        List<int[]> spans = new ArrayList<int[]>();
        Matcher m = URL.matcher(text);
        while (m.find()) {
            int end = m.end();
            while (end > m.start() && TRAILING.indexOf(text.charAt(end - 1)) >= 0) end--;
            spans.add(new int[] { m.start(), end });
        }
        return spans;
    }
}
