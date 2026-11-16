package com.beep.jobportal.fetch;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import java.time.Instant;
import java.time.OffsetDateTime;

/** Small helpers shared by fetchers. */
public final class Text {

    /** Descriptions are long; we keep enough for the page and the LLM. */
    public static final int MAX_DESCRIPTION = 5000;

    private Text() {}

    /**
     * HTML (possibly entity-escaped, like Greenhouse sends it) -> plain text.
     * "&lt;p&gt;Hi&lt;/p&gt;" -> "<p>Hi</p>" -> "Hi"
     */
    public static String plain(String html) {
        if (html == null || html.isBlank()) return null;
        String unescaped = Parser.unescapeEntities(html, false);
        String text = Jsoup.parse(unescaped).text().strip();
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) : text;
    }

    /** ISO-8601 with offset ("2026-11-14T16:10:26Z", "...-04:00") -> Instant, or null. */
    public static Instant instant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    public static String orNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
