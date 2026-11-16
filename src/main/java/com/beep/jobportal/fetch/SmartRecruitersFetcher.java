package com.beep.jobportal.fetch;

import com.beep.jobportal.fetch.SourcesProperties.Board;
import com.beep.jobportal.fetch.SourcesProperties.Company;
import com.beep.jobportal.normalize.IndiaLocations;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * https://api.smartrecruiters.com/v1/companies/{slug}/postings?country=in
 * The list endpoint filters India server-side but has no description.
 * Instead of one extra call per job, we use the structured fields it does give
 * (experience level is a strong fresher signal for the LLM).
 */
@Component
public class SmartRecruitersFetcher extends CompanyBoardFetcher {

    private static final int PAGE_SIZE = 100;

    public SmartRecruitersFetcher(RestClient http, SourcesProperties props, IndiaLocations india) {
        super(http, props, india);
    }

    @Override public String source() { return "smartrecruiters"; }
    @Override protected Board board() { return props.smartrecruiters(); }

    @Override
    protected List<RawJob> fetchCompany(Company company) {
        List<RawJob> out = new ArrayList<>();
        int offset = 0;
        while (out.size() < props.maxJobsPerCompany()) {
            JsonNode page = getJson(
                    "https://api.smartrecruiters.com/v1/companies/{slug}/postings?country=in&limit={limit}&offset={offset}",
                    company.slug(), PAGE_SIZE, offset);
            JsonNode content = page.path("content");
            if (content.isEmpty()) break;
            for (JsonNode j : content) {
                out.add(toRawJob(company, j));
            }
            offset += PAGE_SIZE;
            if (offset >= page.path("totalFound").asInt(0)) break;
        }
        return out;
    }

    private RawJob toRawJob(Company company, JsonNode j) {
        JsonNode loc = j.path("location");
        // fullLocation comes like "Chennai, //////////TN, India" or "bangalore, , India"
        String location = loc.path("fullLocation").asText("India")
                .replaceAll("/+", "")
                .replaceAll("(\\s*,\\s*)+", ", ")     // collapse empty parts: ", , " -> ", "
                .replaceAll("^, |, $", "")
                .strip();
        if (loc.path("remote").asBoolean(false)) location += " (Remote)";

        String description = String.format("Experience level: %s. Function: %s. Employment type: %s. Industry: %s.",
                j.path("experienceLevel").path("label").asText("n/a"),
                j.path("function").path("label").asText("n/a"),
                j.path("typeOfEmployment").path("label").asText("n/a"),
                j.path("industry").path("label").asText("n/a"));

        String identifier = j.path("company").path("identifier").asText(company.slug());
        return new RawJob(
                source(),
                company.slug() + ":" + j.path("id").asText(),
                j.path("name").asText().strip(),
                company.name(),
                location,
                description,
                "https://jobs.smartrecruiters.com/" + identifier + "/" + j.path("id").asText(),
                Text.instant(j.path("releasedDate").asText(null)));
    }
}
