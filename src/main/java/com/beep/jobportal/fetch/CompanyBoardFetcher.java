package com.beep.jobportal.fetch;

import com.beep.jobportal.fetch.SourcesProperties.Board;
import com.beep.jobportal.fetch.SourcesProperties.Company;
import com.beep.jobportal.normalize.IndiaLocations;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared loop for ATS sources that are "one board per company"
 * (Greenhouse, Lever, Ashby, SmartRecruiters).
 * Subclasses only say how to fetch ONE company; this class handles
 * the company list, per-company errors, the per-company cap and the delay.
 */
public abstract class CompanyBoardFetcher implements JobFetcher {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final RestClient http;
    protected final SourcesProperties props;
    protected final IndiaLocations india;

    protected CompanyBoardFetcher(RestClient http, SourcesProperties props, IndiaLocations india) {
        this.http = http;
        this.props = props;
        this.india = india;
    }

    /** This source's section of sources.yml. */
    protected abstract Board board();

    /** India jobs of one company. May throw; the loop below catches it. */
    protected abstract List<RawJob> fetchCompany(Company company);

    @Override
    public List<RawJob> fetch() {
        Board board = board();
        if (board == null || !board.enabled() || board.companies() == null) {
            return List.of();
        }
        List<RawJob> all = new ArrayList<>();
        for (Company company : board.companies()) {
            try {
                List<RawJob> jobs = fetchCompany(company);
                if (jobs.size() > props.maxJobsPerCompany()) {
                    jobs = jobs.subList(0, props.maxJobsPerCompany());
                }
                all.addAll(jobs);
                log.info("{} / {}: {} India jobs", source(), company.slug(), jobs.size());
            } catch (Exception e) {
                // one broken board must not kill the whole run
                log.warn("{} / {} failed: {}", source(), company.slug(), e.getMessage());
            }
            pause();
        }
        return all;
    }

    /** GET a URL template like ".../boards/{slug}/jobs" and parse JSON. */
    protected JsonNode getJson(String urlTemplate, Object... vars) {
        return http.get().uri(urlTemplate, vars).retrieve().body(JsonNode.class);
    }

    private void pause() {
        try {
            Thread.sleep(props.delayBetweenCompaniesMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
