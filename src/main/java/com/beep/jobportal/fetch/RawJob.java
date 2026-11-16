package com.beep.jobportal.fetch;

import java.time.Instant;

/**
 * A job exactly as one source gave it, in a common shape.
 * Every fetcher returns these; nothing here is normalized yet.
 */
public record RawJob(
        String source,
        String sourceJobId,
        String title,
        String company,
        String locationRaw,
        String description,
        String applyUrl,
        Instant postedAt
) {}
