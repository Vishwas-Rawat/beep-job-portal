package com.beep.jobportal.tag;

import com.beep.jobportal.job.Job;
import com.beep.jobportal.job.JobRepository;
import com.beep.jobportal.normalize.IndiaLocations;
import com.beep.jobportal.tag.JobTagger.BatchResult;
import com.beep.jobportal.tag.JobTagger.RawTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tags every untagged job: batches -> LLM -> validate -> save.
 * Rules: only allowed roles, normalized city names, each post tried at most
 * max-attempts times, then a rule-based fallback so nothing stays untagged.
 */
@Service
public class TaggingService {

    private static final Logger log = LoggerFactory.getLogger(TaggingService.class);
    private static final Pattern FRESHER_TITLE = Pattern.compile(
            "\\b(intern|internship|trainee|fresher|freshers|graduate|apprentice|entry[ -]level|junior)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Totals for run_log. */
    public record TagStats(int tagged, int invalidRole, int fallback, int failedCalls,
                           long inputTokens, long outputTokens) {}

    private final JobTagger tagger;
    private final JobRepository jobs;
    private final TaggingProperties props;
    private final IndiaLocations india;
    private final Map<String, String> rolesByKey;   // "data analyst" -> "Data Analyst"

    public TaggingService(JobTagger tagger, JobRepository jobs, TaggingProperties props, IndiaLocations india) {
        this.tagger = tagger;
        this.jobs = jobs;
        this.props = props;
        this.india = india;
        this.rolesByKey = props.roles().stream()
                .collect(Collectors.toMap(TaggingService::key, Function.identity()));
    }

    public TagStats tagPending() {
        if (!props.enabled()) return new TagStats(0, 0, 0, 0, 0, 0);
        int tagged = 0, invalidRole = 0, failedCalls = 0;
        long in = 0, out = 0;

        // Pass 1 tags everything; pass 2 retries posts whose batch failed or that the model skipped.
        for (int pass = 1; pass <= props.maxAttempts(); pass++) {
            List<Job> pending = jobs.findByRoleTagIsNullAndTagAttemptsLessThanOrderByIdAsc(
                    props.maxAttempts(), PageRequest.of(0, props.maxJobsPerRun()));
            if (pending.isEmpty()) break;
            log.info("Tagging pass {}: {} jobs", pass, pending.size());

            for (int i = 0; i < pending.size(); i += props.batchSize()) {
                List<Job> batch = pending.subList(i, Math.min(i + props.batchSize(), pending.size()));
                try {
                    BatchResult result = tagger.tag(batch);
                    in += result.inputTokens();
                    out += result.outputTokens();
                    int[] counts = apply(batch, result.tags());
                    tagged += counts[0];
                    invalidRole += counts[1];
                } catch (Exception e) {
                    // Bad JSON or API error: count an attempt for the whole batch, retry next pass
                    failedCalls++;
                    log.warn("Tagging batch failed ({} jobs): {}", batch.size(), e.getMessage());
                    batch.forEach(j -> j.setTagAttempts(j.getTagAttempts() + 1));
                }
                jobs.saveAll(batch);
                pause();
            }
        }

        int fallback = applyFallback();
        log.info("Tagging done: tagged={} invalidRole={} fallback={} failedCalls={} tokens in={} out={}",
                tagged, invalidRole, fallback, failedCalls, in, out);
        return new TagStats(tagged, invalidRole, fallback, failedCalls, in, out);
    }

    /** Validate each model answer and copy it onto the job. Returns {tagged, invalidRole}. */
    private int[] apply(List<Job> batch, List<RawTag> tags) {
        Map<Long, RawTag> byId = tags.stream()
                .collect(Collectors.toMap(RawTag::id, Function.identity(), (a, b) -> a));
        int tagged = 0, invalidRole = 0;
        for (Job job : batch) {
            job.setTagAttempts(job.getTagAttempts() + 1);
            RawTag t = byId.get(job.getId());
            if (t == null) continue;              // model skipped this post: retried next pass

            String role = rolesByKey.get(key(t.role()));
            if (role == null) {                   // not in our list: don't trust it
                role = "Other";
                invalidRole++;
            }
            job.setRoleTag(role);
            job.setCityTag(normalizeCity(t.city(), job));
            job.setIsFresher(t.isFresher() != null ? t.isFresher() : ruleFresher(job));
            job.setTagStatus("llm");
            job.setTaggedAt(Instant.now());
            tagged++;
        }
        return new int[]{tagged, invalidRole};
    }

    /** Posts the model failed on twice still get usable tags, marked 'fallback'. */
    private int applyFallback() {
        List<Job> givenUp = jobs.findByRoleTagIsNullAndTagAttemptsGreaterThanEqual(props.maxAttempts());
        for (Job job : givenUp) {
            job.setRoleTag("Other");
            job.setCityTag(normalizeCity(null, job));
            job.setIsFresher(ruleFresher(job));
            job.setTagStatus("fallback");
            job.setTaggedAt(Instant.now());
        }
        jobs.saveAll(givenUp);
        return givenUp.size();
    }

    /** "bangalore" -> "Bengaluru", "remote" -> "Remote", blank/Unknown -> from location text. */
    String normalizeCity(String llmCity, Job job) {
        String c = llmCity == null ? "" : llmCity.strip();
        if (c.isEmpty() || c.equalsIgnoreCase("unknown")) {
            String fromLocation = india.cityOrFallback(job.getLocationRaw());
            return fromLocation.equals("India") ? "Unknown" : fromLocation;
        }
        if (c.toLowerCase(Locale.ROOT).contains("remote")) return "Remote";
        String canonical = india.firstCity(c);
        if (canonical != null) return canonical;
        // A real place not in cities.txt (e.g. "Talikot"): keep it, tidy the case
        return Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    private boolean ruleFresher(Job job) {
        return FRESHER_TITLE.matcher(job.getTitle()).find();
    }

    private static String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private void pause() {
        try {
            Thread.sleep(props.delayBetweenBatchesMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
