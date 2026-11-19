package com.beep.jobportal.ask;

import com.beep.jobportal.job.JobController;
import com.beep.jobportal.job.JobRepository;
import com.beep.jobportal.normalize.IndiaLocations;
import com.beep.jobportal.tag.TaggingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * "Ask AI" search: the LLM only turns a sentence into filters (keywords, role, city, fresher).
 * Java checks those filters, the normal search runs, and Java writes the reply.
 * The model never sees or invents jobs. If the AI is slow, busy or down, simple rules take over.
 */
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    static final int MAX_QUESTION_CHARS = 200;
    private static final long AI_TIMEOUT_SECONDS = 12;
    /** Shared Gemini free-tier quota: keep plenty left for the daily tagging run. */
    static final int MAX_AI_CALLS_PER_MINUTE = 20;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "in", "at", "for", "of", "to", "and", "or", "with", "on", "me", "my", "i", "am", "is", "are",
            "any", "some", "show", "find", "get", "want", "need", "looking", "search", "please", "available", "near",
            "job", "jobs", "role", "roles", "opening", "openings", "vacancy", "vacancies", "position", "positions",
            "hiring", "work", "career", "careers", "company", "developer", "engineer", "fresher", "freshers",
            "entry", "level", "graduate", "graduates", "trainee", "experience", "experienced", "senior", "years",
            "intern", "interns", "internship", "internships",
            "remote", "wfh", "home", "from", "india");

    /** Filters after validation. "" means "no filter". */
    public record Filters(String q, String role, String city, boolean fresher) {}

    /** A looser search the user can click when nothing matched. */
    public record Suggestion(String label, String q, String role, String city, boolean fresher, long count) {}

    public record Answer(boolean relevant, boolean aiUsed, String q, String role, String city, boolean fresher,
                         long total, String message, List<Suggestion> suggestions) {}

    private final ChatModel chatModel;
    private final ObjectMapper mapper;
    private final JobRepository jobs;
    private final IndiaLocations india;
    private final List<String> roles;          // allowed roles, without "Other"
    private final String systemPrompt;
    private final LongSupplier clock;
    private final AtomicLong minute = new AtomicLong();
    private final AtomicInteger callsThisMinute = new AtomicInteger();
    // Small pool: a few AI calls at a time; extra requests use the rule-based fallback instead of queueing forever
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    public AskService(ChatModel chatModel, ObjectMapper mapper, JobRepository jobs, IndiaLocations india,
                      TaggingProperties props) throws IOException {
        this(chatModel, mapper, jobs, india, props, System::currentTimeMillis);
    }

    AskService(ChatModel chatModel, ObjectMapper mapper, JobRepository jobs, IndiaLocations india,
               TaggingProperties props, LongSupplier clock) throws IOException {
        this.chatModel = chatModel;
        this.mapper = mapper;
        this.jobs = jobs;
        this.india = india;
        this.clock = clock;
        this.roles = props.roles().stream().filter(r -> !r.equals("Other")).toList();
        this.systemPrompt = new ClassPathResource("prompts/ask-system.txt")
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("[[ROLES]]", String.join(", ", roles))
                .replace("[[CITIES]]", String.join(", ", india.canonicalCities()));
    }

    public Answer ask(String question) {
        String text = question == null ? "" : question.strip();
        if (text.length() > MAX_QUESTION_CHARS) text = text.substring(0, MAX_QUESTION_CHARS);
        if (text.isEmpty()) {
            return notRelevant(false, "Type what you're looking for, for example \"fresher java jobs in Pune\".");
        }

        Parsed parsed = null;
        if (allowAiCall()) {
            try {
                String finalText = text;
                String json = CompletableFuture.supplyAsync(() -> callModel(finalText), pool)
                        .get(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                parsed = fromModelJson(json, text);
            } catch (Exception e) {
                log.warn("Ask AI failed, using rules instead: {}", e.toString());
            }
        }
        boolean aiUsed = parsed != null;
        if (parsed == null) parsed = fromRules(text);

        if (!parsed.relevant()) {
            return notRelevant(aiUsed, "I can only help you find jobs. Try something like \"data analyst internship in Bengaluru\".");
        }
        return answer(parsed.filters(), aiUsed);
    }

    // ---------- AI part ----------

    private String callModel(String text) {
        var options = OpenAiChatOptions.builder()
                .temperature(0.0)
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                .build();
        var prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(text)), options);
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    /** Global cap per minute, so a busy page can't use up the free quota the daily tagging needs. */
    boolean allowAiCall() {
        long now = clock.getAsLong() / 60_000;
        if (minute.getAndSet(now) != now) callsThisMinute.set(0);
        return callsThisMinute.incrementAndGet() <= MAX_AI_CALLS_PER_MINUTE;
    }

    record Parsed(boolean relevant, Filters filters) {}

    /** Read the model's JSON and keep only values we trust. */
    Parsed fromModelJson(String json, String question) throws IOException {
        JsonNode n = mapper.readTree(json.strip());
        if (!n.path("relevant").asBoolean(true)) return new Parsed(false, null);

        String q = cleanKeywords(text(n, "keywords"));
        String role = matchRole(text(n, "role"));
        String city = cleanCity(text(n, "city"));
        JsonNode f = n.path("fresher");
        boolean fresher = f.isBoolean() ? f.asBoolean() : true;   // not said -> fresher jobs (this is a fresher portal)
        return new Parsed(true, new Filters(q, role, city, fresher));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("").strip();
    }

    /**
     * Lowercase, max 5 words. "intern" is dropped: as a text search it also matches "international" and "internal",
     * and internships are already covered by the fresher filter.
     */
    static String cleanKeywords(String keywords) {
        return Arrays.stream(keywords.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(w -> !w.isBlank())
                .filter(w -> !w.startsWith("intern"))
                .distinct()
                .limit(5)
                .collect(Collectors.joining(" "));
    }

    /** Only roles from our fixed list; anything else is ignored. */
    String matchRole(String role) {
        return roles.stream().filter(r -> r.equalsIgnoreCase(role)).findFirst().orElse("");
    }

    String cleanCity(String city) {
        if (city.isEmpty()) return "";
        if (city.toLowerCase(Locale.ROOT).matches(".*\\b(remote|wfh|work from home)\\b.*")) return "Remote";
        String canonical = india.firstCity(city);
        if (canonical != null) return canonical;
        return Character.toUpperCase(city.charAt(0)) + city.substring(1);   // e.g. "London": kept, will find 0 jobs
    }

    // ---------- Rule-based fallback (AI busy or down) ----------

    Parsed fromRules(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        String role = roles.stream().filter(r -> lower.contains(r.toLowerCase(Locale.ROOT))).findFirst().orElse("");
        String city = lower.matches(".*\\b(remote|wfh|work from home)\\b.*") ? "Remote"
                : java.util.Objects.requireNonNullElse(india.firstCity(question), "");
        boolean fresher = !lower.matches(".*\\b(senior|experienced|\\d+\\+? ?years)\\b.*");

        Set<String> skip = new LinkedHashSet<>(STOP_WORDS);
        if (!role.isEmpty()) skip.addAll(Arrays.asList(role.toLowerCase(Locale.ROOT).split("\\W+")));
        String q = Arrays.stream(lower.split("[^a-z0-9+#.]+"))
                .map(w -> w.replaceAll("^\\.+|\\.+$", ""))
                .filter(w -> w.length() > 1 && !skip.contains(w) && india.firstCity(w) == null)
                .distinct().limit(5)
                .collect(Collectors.joining(" "));
        return new Parsed(true, new Filters(q, role, city, fresher));
    }

    // ---------- Answer ----------

    Answer answer(Filters f, boolean aiUsed) {
        long total = count(f);
        if (total > 0) {
            return new Answer(true, aiUsed, f.q(), f.role(), f.city(), f.fresher(), total,
                    "Found " + total + " " + describe(f, total) + ".", List.of());
        }
        List<Suggestion> suggestions = new ArrayList<>();
        if (!f.q().isEmpty()) addIfAny(suggestions, "Without \"" + f.q() + "\"", new Filters("", f.role(), f.city(), f.fresher()));
        if (!f.city().isEmpty()) addIfAny(suggestions, "All cities", new Filters(f.q(), f.role(), "", f.fresher()));
        if (!f.role().isEmpty()) addIfAny(suggestions, "Any role", new Filters(f.q(), "", f.city(), f.fresher()));
        if (f.fresher()) addIfAny(suggestions, "Include experienced jobs", new Filters(f.q(), f.role(), f.city(), false));

        String msg = "No " + describe(f, 0) + " right now.";
        if (!f.city().isEmpty() && cityHasNoJobs(f.city())) msg += " This portal only lists jobs in India.";
        msg += suggestions.isEmpty() ? " Try different words." : " These are close:";
        return new Answer(true, aiUsed, f.q(), f.role(), f.city(), f.fresher(), 0, msg, suggestions.subList(0, Math.min(3, suggestions.size())));
    }

    private void addIfAny(List<Suggestion> out, String label, Filters f) {
        long n = count(f);
        if (n > 0) out.add(new Suggestion(label, f.q(), f.role(), f.city(), f.fresher(), n));
    }

    private long count(Filters f) {
        return jobs.search(JobController.toPattern(f.q()), f.role(), f.city(), f.fresher(), PageRequest.of(0, 1))
                .getTotalElements();
    }

    private boolean cityHasNoJobs(String city) {
        return count(new Filters("", "", city, false)) == 0;
    }

    /** "fresher Data Analyst jobs in Pune matching "sql"" */
    static String describe(Filters f, long total) {
        StringBuilder s = new StringBuilder();
        if (f.fresher()) s.append("fresher ");
        if (!f.role().isEmpty()) s.append(f.role()).append(' ');
        s.append(total == 1 ? "job" : "jobs");
        if (!f.city().isEmpty()) s.append(f.city().equals("Remote") ? " (remote)" : " in " + f.city());
        if (!f.q().isEmpty()) s.append(" matching \"").append(f.q()).append('"');
        return s.toString();
    }

    private static Answer notRelevant(boolean aiUsed, String message) {
        return new Answer(false, aiUsed, "", "", "", true, 0, message, List.of());
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
