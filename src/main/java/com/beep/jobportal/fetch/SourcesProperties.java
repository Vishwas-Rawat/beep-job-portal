package com.beep.jobportal.fetch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Typed view of sources.yml. */
@ConfigurationProperties(prefix = "sources")
public record SourcesProperties(
        int httpTimeoutSeconds,
        int delayBetweenCompaniesMs,
        int maxJobsPerCompany,
        Board greenhouse,
        Board lever,
        Board ashby,
        Board smartrecruiters
) {
    public record Board(boolean enabled, List<Company> companies) {}

    /** slug = the company's id in the ATS URL; name = what we show on the page. */
    public record Company(String slug, String name) {}
}
