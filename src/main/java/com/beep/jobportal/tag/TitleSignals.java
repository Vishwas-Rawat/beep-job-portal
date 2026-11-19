package com.beep.jobportal.tag;

import java.util.regex.Pattern;

/**
 * Seniority words in a job title. Used as a hard rule after the LLM answers:
 * the title beats anything else (job boards' "experience level" fields are often wrong).
 */
public final class TitleSignals {

    private static final Pattern JUNIOR = Pattern.compile(
            "\\b(intern|internship|trainee|fresher|freshers|graduate|apprentice|junior|jr\\.?|entry[ -]level)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SENIOR = Pattern.compile(
            "\\b(senior|sr\\.?|lead|manager|director|head|principal|staff|architect|vp|vice president|partner|ii|iii|iv)\\b",
            Pattern.CASE_INSENSITIVE);

    private TitleSignals() {}

    public static boolean isJunior(String title) {
        return title != null && JUNIOR.matcher(title).find();
    }

    public static boolean isSenior(String title) {
        return title != null && SENIOR.matcher(title).find();
    }

    /**
     * true / false when the title decides it, null when the title says nothing
     * (then the LLM's answer stands). Junior words win: "Graduate Trainee, Senior Team" is still fresher.
     */
    public static Boolean fresherFromTitle(String title) {
        if (isJunior(title)) return true;
        if (isSenior(title)) return false;
        return null;
    }
}
