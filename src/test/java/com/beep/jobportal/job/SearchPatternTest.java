package com.beep.jobportal.job;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchPatternTest {

    @Test
    void wordsBecomeAnyOf() {
        assertEquals("java|python", JobController.toPattern("java python"));
        assertEquals("java|python", JobController.toPattern("  Java,  PYTHON "));
        assertEquals("java", JobController.toPattern("java java"));
    }

    @Test
    void emptyMeansNoFilter() {
        assertEquals("", JobController.toPattern(""));
        assertEquals("", JobController.toPattern("   "));
    }

    @Test
    void specialCharactersAreLiteral() {
        assertEquals("c\\+\\+", JobController.toPattern("c++"));
        assertEquals("\\.net|node\\.js", JobController.toPattern(".net node.js"));
    }
}
