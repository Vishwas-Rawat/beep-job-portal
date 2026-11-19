package com.beep.jobportal.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPatternTest {

    @Test
    void wordsBecomeAnyOf() {
        assertEquals("\\mjava\\M|\\mpython\\M", JobController.toPattern("java python"));
        assertEquals("\\mjava\\M|\\mpython\\M", JobController.toPattern("  Java,  PYTHON "));
        assertEquals("\\mjava\\M", JobController.toPattern("java java"));
    }

    @Test
    void wholeWordsOnly() {
        var p = JobController.javaPattern("java");
        assertTrue(p.matcher("Senior Java Developer").find());
        assertFalse(p.matcher("JavaScript Developer").find());
        assertFalse(JobController.javaPattern("intern").matcher("RSM International").find());
    }

    @Test
    void cityListSkipsNonCities() {
        assertEquals(true, JobController.isCityName("Pune"));
        assertEquals(true, JobController.isCityName("Remote"));
        assertEquals(false, JobController.isCityName("Unknown"));
        assertEquals(false, JobController.isCityName("India"));
        assertEquals(false, JobController.isCityName("Karnataka"));
        assertEquals(false, JobController.isCityName(" "));
    }

    @Test
    void emptyMeansNoFilter() {
        assertEquals("", JobController.toPattern(""));
        assertEquals("", JobController.toPattern("   "));
    }

    @Test
    void specialCharactersAreLiteral() {
        assertEquals("\\mc\\+\\+", JobController.toPattern("c++"));
        assertEquals("\\.net\\M|\\mnode\\.js\\M", JobController.toPattern(".net node.js"));
        assertTrue(JobController.javaPattern("c++").matcher("Embedded C++ engineer").find());
        assertTrue(JobController.javaPattern(".net").matcher("ASP.NET developer").find());
    }

    @Test
    void showsWhyAJobMatched() {
        Job j = new Job("lever", "1", "Associate Customer Experience Engineer", "Hevo Data");
        j.setLocationRaw("Pune, Maharashtra");
        j.setDescription("You will help customers with REST APIs and webhooks. Working knowledge of Java "
                + "or another object-oriented programming language is a plus.");
        String m = JobController.descriptionMatch(j, JobController.javaPattern("java"));
        assertTrue(m.contains("Working knowledge of Java"));

        Job titled = new Job("lever", "2", "Java Developer", "Acme");
        titled.setDescription("Java, Spring");
        assertNull(JobController.descriptionMatch(titled, JobController.javaPattern("java")));   // title already shows it
    }
}
