package com.beep.jobportal.ask;

import com.beep.jobportal.job.JobRepository;
import com.beep.jobportal.normalize.IndiaLocations;
import com.beep.jobportal.tag.TaggingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AskServiceTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private AskService ask;

    @BeforeEach
    void setUp() throws Exception {
        var props = new TaggingProperties(true, "v3", 20, 2000, 2, 3000, 0, "", 0.3, 2.5,
                List.of("Software Engineer (Backend)", "Data Analyst", "HR", "Customer Support", "Other"));
        ask = new AskService(chatModel, new ObjectMapper(), jobs, new IndiaLocations(), props, now::get);
        jobCounts(10);
    }

    /** Every search finds n jobs, except searches in "London" (0). */
    private void jobCounts(long n) {
        when(jobs.search(anyString(), anyString(), anyString(), anyBoolean(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    long total = "London".equals(inv.getArgument(2)) ? 0 : n;
                    return new PageImpl<>(List.of(), inv.getArgument(4), total);
                });
    }

    @Test
    void modelAnswerIsChecked() throws Exception {
        var p = ask.fromModelJson("""
                {"relevant":true,"keywords":"Java Intern","role":"Data Analyst","city":"bangalore","fresher":null}""",
                "java trainee jobs in bangalore");
        assertTrue(p.relevant());
        assertEquals("java", p.filters().q());          // "intern" dropped: it would also match "international"
        assertEquals("Data Analyst", p.filters().role());
        assertEquals("Bengaluru", p.filters().city());  // alias -> standard name
        assertTrue(p.filters().fresher());              // not said -> fresher portal default
    }

    @Test
    void unknownOrOtherRoleIsIgnored() throws Exception {
        assertEquals("", ask.fromModelJson("{\"relevant\":true,\"role\":\"Astronaut\"}", "x").filters().role());
        assertEquals("", ask.fromModelJson("{\"relevant\":true,\"role\":\"Other\"}", "x").filters().role());
    }

    @Test
    void notAJobQuestion() throws Exception {
        assertFalse(ask.fromModelJson("{\"relevant\":false}", "write a poem").relevant());
    }

    @Test
    void rulesWorkWithoutAi() {
        var f = ask.fromRules("Data Analyst internship in Bangalore").filters();
        assertEquals("Data Analyst", f.role());
        assertEquals("Bengaluru", f.city());
        assertEquals("", f.q());                        // internship -> fresher filter, not a keyword
        assertTrue(f.fresher());
        assertFalse(ask.fromRules("senior java developer 5 years").filters().fresher());
        assertEquals("Remote", ask.fromRules("python work from home").filters().city());
    }

    @Test
    void aiDownFallsBackToRules() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("503 high demand"));
        var a = ask.ask("HR jobs in Pune");
        assertTrue(a.relevant());
        assertFalse(a.aiUsed());
        assertEquals("HR", a.role());
        assertEquals("Pune", a.city());
        assertEquals("Found 10 fresher HR jobs in Pune.", a.message());
    }

    @Test
    void noResultsGivesCloseSearches() {
        var a = ask.answer(new AskService.Filters("java", "", "London", true), true);
        assertEquals(0, a.total());
        assertTrue(a.message().startsWith("No fresher jobs in London matching \"java\" right now."));
        assertTrue(a.message().contains("only lists jobs in India"));
        assertEquals("All cities", a.suggestions().get(0).label());
        assertEquals(10, a.suggestions().get(0).count());
    }

    @Test
    void aiCallsAreCappedPerMinute() {
        for (int i = 0; i < AskService.MAX_AI_CALLS_PER_MINUTE; i++) assertTrue(ask.allowAiCall());
        assertFalse(ask.allowAiCall());
        now.addAndGet(60_000);
        assertTrue(ask.allowAiCall());
    }

    @Test
    void emptyQuestion() {
        assertFalse(ask.ask("   ").relevant());
    }
}
