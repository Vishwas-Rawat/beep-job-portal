package com.beep.jobportal.fetch;

import com.beep.jobportal.fetch.SourcesProperties.Board;
import com.beep.jobportal.fetch.SourcesProperties.Company;
import com.beep.jobportal.normalize.IndiaLocations;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/** https://boards-api.greenhouse.io/v1/boards/{slug}/jobs?content=true */
@Component
public class GreenhouseFetcher extends CompanyBoardFetcher {

    public GreenhouseFetcher(RestClient http, SourcesProperties props, IndiaLocations india) {
        super(http, props, india);
    }

    @Override public String source() { return "greenhouse"; }
    @Override protected Board board() { return props.greenhouse(); }

    @Override
    protected List<RawJob> fetchCompany(Company company) {
        JsonNode root = getJson("https://boards-api.greenhouse.io/v1/boards/{slug}/jobs?content=true", company.slug());
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            // location.name is free text; offices can add the real city
            String location = j.path("location").path("name").asText("");
            if (!india.isIndia(location)) {
                List<String> indiaOffices = new ArrayList<>();
                for (JsonNode office : j.path("offices")) {
                    String name = office.path("name").asText("");
                    if (india.isIndia(name)) indiaOffices.add(name);
                }
                if (indiaOffices.isEmpty()) continue;   // not an India job
                location = String.join(", ", indiaOffices);
            }
            String posted = j.hasNonNull("first_published")
                    ? j.path("first_published").asText()
                    : j.path("updated_at").asText(null);
            out.add(new RawJob(
                    source(),
                    company.slug() + ":" + j.path("id").asText(),
                    j.path("title").asText().strip(),
                    company.name(),
                    location,
                    Text.plain(j.path("content").asText(null)),
                    j.path("absolute_url").asText(null),
                    Text.instant(posted)));
        }
        return out;
    }
}
