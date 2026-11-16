package com.beep.jobportal.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** One refresh run: counts, token usage and cost. Maps to "run_log". */
@Entity
@Table(name = "run_log")
public class RunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private String status = "RUNNING";

    private int fetched;

    @Column(name = "new_jobs")
    private int newJobs;

    private int duplicates;

    @Column(name = "already_stored")
    private int alreadyStored;

    @Column(name = "source_counts")
    private String sourceCounts;

    private int tagged;

    @Column(name = "tag_failed")
    private int tagFailed;

    @Column(name = "input_tokens")
    private long inputTokens;

    @Column(name = "output_tokens")
    private long outputTokens;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal costUsd = BigDecimal.ZERO;

    private String error;

    public Long getId() { return id; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getFetched() { return fetched; }
    public void setFetched(int fetched) { this.fetched = fetched; }

    public int getNewJobs() { return newJobs; }
    public void setNewJobs(int newJobs) { this.newJobs = newJobs; }

    public int getDuplicates() { return duplicates; }
    public void setDuplicates(int duplicates) { this.duplicates = duplicates; }

    public int getAlreadyStored() { return alreadyStored; }
    public void setAlreadyStored(int alreadyStored) { this.alreadyStored = alreadyStored; }

    public String getSourceCounts() { return sourceCounts; }
    public void setSourceCounts(String sourceCounts) { this.sourceCounts = sourceCounts; }

    public int getTagged() { return tagged; }
    public void setTagged(int tagged) { this.tagged = tagged; }

    public int getTagFailed() { return tagFailed; }
    public void setTagFailed(int tagFailed) { this.tagFailed = tagFailed; }

    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }

    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }

    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }

    public BigDecimal getCostUsd() { return costUsd; }
    public void setCostUsd(BigDecimal costUsd) { this.costUsd = costUsd; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
