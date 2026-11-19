package com.beep.jobportal.tag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Titles taken from the 50-post accuracy check where prompt v1 got "fresher" wrong. */
class TitleSignalsTest {

    @Test
    void seniorTitlesAreNotFresher() {
        assertEquals(false, TitleSignals.fresherFromTitle("Senior Account Executive - Inside Sales"));
        assertEquals(false, TitleSignals.fresherFromTitle("Senior Full Stack Developer (Python, React.js & Azure)"));
        assertEquals(false, TitleSignals.fresherFromTitle("Field Sales - Team Lead - Hyderabad (LRM)"));
        assertEquals(false, TitleSignals.fresherFromTitle("Software Dev Engineer II, Amazon"));
        assertEquals(false, TitleSignals.fresherFromTitle("Sr Analyst – Innovation & Portfolio"));
    }

    @Test
    void juniorTitlesAreFresher() {
        assertEquals(true, TitleSignals.fresherFromTitle("Junior Service Desk Analyst"));
        assertEquals(true, TitleSignals.fresherFromTitle("Communications intern"));
        assertEquals(true, TitleSignals.fresherFromTitle("Graduate Apprentice Trainee"));
    }

    @Test
    void neutralTitlesLeaveItToTheModel() {
        assertNull(TitleSignals.fresherFromTitle("HW Design Engineer"));
        assertNull(TitleSignals.fresherFromTitle("Executive - IT Support"));
        assertNull(TitleSignals.fresherFromTitle("Account Executive, Enterprise"));
    }

    @Test
    void wordsInsideOtherWordsDoNotCount() {
        // "lead" inside "leadership"? "head" inside "headquarters"? -> whole words only
        assertNull(TitleSignals.fresherFromTitle("Leadership Development Coordinator"));
        assertNull(TitleSignals.fresherFromTitle("Headquarters Operations Executive"));
    }
}
