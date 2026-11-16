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
        Board smartrecruiters,
        Careerjet careerjet
) {
    public record Board(boolean enabled, List<Company> companies) {}

    /** slug = the company's id in the ATS URL; name = what we show on the page. */
    public record Company(String slug, String name) {}

    /** Keyword-search source: one query per keyword, a few pages each. */
    public record Careerjet(boolean enabled, String apiKey, String referer, String locale,
                            int pageSize, int pagesPerKeyword, List<String> keywords) {}
}
