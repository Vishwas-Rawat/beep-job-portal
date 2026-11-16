package com.beep.jobportal.tag;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Typed view of tagging.yml. */
@ConfigurationProperties(prefix = "tagging")
public record TaggingProperties(
        boolean enabled,
        int batchSize,
        int descriptionChars,
        int maxAttempts,
        int maxJobsPerRun,
        int delayBetweenBatchesMs,
        String reasoningEffort,
        double priceInputPerMillion,
        double priceOutputPerMillion,
        List<String> roles
) {}
