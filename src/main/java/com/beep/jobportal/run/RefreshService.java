package com.beep.jobportal.run;

import com.beep.jobportal.fetch.JobFetcher;
import com.beep.jobportal.fetch.RawJob;
import com.beep.jobportal.job.Job;
import com.beep.jobportal.job.JobRepository;
import com.beep.jobportal.normalize.JobNormalizer;
import com.beep.jobportal.tag.TaggingProperties;
import com.beep.jobportal.tag.TaggingService;
import com.beep.jobportal.tag.TaggingService.TagStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * One run: fetch from every source -> dedupe -> save new jobs -> LLM-tag untagged jobs -> write run_log.
 * tagOnly() skips fetching (used to tag the existing backlog).
 */
@Service
public class RefreshService {

    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);

    private final List<JobFetcher> fetchers;   // Spring injects every @Component JobFetcher
    private final JobRepository jobs;
    private final RunLogRepository runs;
    private final JobNormalizer normalizer;
    private final TaggingService tagging;
    private final TaggingProperties tagProps;
    private final String llmModel;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RefreshService(List<JobFetcher> fetchers, JobRepository jobs, RunLogRepository runs,
                          JobNormalizer normalizer, TaggingService tagging, TaggingProperties tagProps,
                          @Value("${spring.ai.openai.chat.options.model}") String llmModel) {
        this.fetchers = fetchers;
        this.jobs = jobs;
        this.runs = runs;
        this.normalizer = normalizer;
        this.tagging = tagging;
        this.tagProps = tagProps;
        this.llmModel = llmModel;
    }

    public boolean isRunning() {
        return running.get();
    }

    public RunLog refresh() {
        return run(true);
    }

    public RunLog tagOnly() {
        return run(false);
    }

    private RunLog run(boolean fetch) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A run is already in progress");
        }
        RunLog run = runs.save(new RunLog());
        try {
            if (fetch) fetchAndSave(run);
            tag(run);
            run.setStatus("SUCCESS");
        } catch (Exception e) {
            log.error("Run failed", e);
            run.setStatus("FAILED");
            run.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            run.setFinishedAt(Instant.now());
            runs.save(run);
            running.set(false);
        }
        return run;
    }

    private void fetchAndSave(RunLog run) {
        // 1. Fetch from all sources
        List<RawJob> fetched = new ArrayList<>();
        Map<String, Integer> perSource = new LinkedHashMap<>();
        for (JobFetcher fetcher : fetchers) {
            try {
                List<RawJob> batch = fetcher.fetch();
                fetched.addAll(batch);
                perSource.put(fetcher.source(), batch.size());
            } catch (Exception e) {
                log.error("Fetcher {} failed", fetcher.source(), e);
                perSource.put(fetcher.source() + "(failed)", 0);
            }
        }
        run.setFetched(fetched.size());
        run.setSourceCounts(perSource.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ")));

        // 2. Dedupe against the DB and within this run, 3. save new ones
        Set<String> knownSourceIds = jobs.findAllSourceIds();
        Set<String> knownKeys = jobs.findAllDedupeKeys();
        int alreadyStored = 0, duplicates = 0, saved = 0;

        for (RawJob raw : fetched) {
            if (!knownSourceIds.add(raw.source() + "|" + raw.sourceJobId())) {
                alreadyStored++;                 // we saved this exact post in an earlier run
                continue;
            }
            String key = normalizer.dedupeKey(raw);
            if (!knownKeys.add(key)) {
                duplicates++;                    // same company+title+city already exists
                continue;
            }
            jobs.save(toEntity(raw, key));
            saved++;
        }

        run.setAlreadyStored(alreadyStored);
        run.setDuplicates(duplicates);
        run.setNewJobs(saved);
        log.info("Fetch done: fetched={} new={} duplicates={} alreadyStored={} [{}]",
                fetched.size(), saved, duplicates, alreadyStored, run.getSourceCounts());
    }

    /** Tag every untagged job and record real token usage + cost. A tagging failure never loses fetched jobs. */
    private void tag(RunLog run) {
        try {
            TagStats s = tagging.tagPending();
            run.setTagged(s.tagged());
            run.setTagFailed(s.fallback());
            run.setInputTokens(s.inputTokens());
            run.setOutputTokens(s.outputTokens());
            run.setLlmModel(llmModel);
            run.setCostUsd(cost(s.inputTokens(), s.outputTokens()));
        } catch (Exception e) {
            log.error("Tagging failed", e);
            run.setError("tagging: " + e.getMessage());
        }
    }

    /** cost = input tokens x input price + output tokens x output price (prices per 1M tokens) */
    private BigDecimal cost(long in, long out) {
        double usd = in / 1_000_000.0 * tagProps.priceInputPerMillion()
                   + out / 1_000_000.0 * tagProps.priceOutputPerMillion();
        return BigDecimal.valueOf(usd).setScale(6, RoundingMode.HALF_UP);
    }

    private Job toEntity(RawJob raw, String dedupeKey) {
        Job job = new Job(raw.source(), raw.sourceJobId(), raw.title(), raw.company());
        job.setLocationRaw(raw.locationRaw());
        job.setDescription(raw.description());
        job.setApplyUrl(raw.applyUrl());
        job.setPostedAt(raw.postedAt());
        job.setDedupeKey(dedupeKey);
        return job;
    }
}
