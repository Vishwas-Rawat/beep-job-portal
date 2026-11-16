package com.beep.jobportal.fetch;

import com.beep.jobportal.fetch.SourcesProperties.Board;
import com.beep.jobportal.fetch.SourcesProperties.Company;
import com.beep.jobportal.normalize.IndiaLocations;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** https://api.lever.co/v0/postings/{slug}?mode=json */
@Component
public class LeverFetcher extends CompanyBoardFetcher {

    public LeverFetcher(RestClient http, SourcesProperties props, IndiaLocations india) {
        super(http, props, india);
    }

    @Override public String source() { return "lever"; }
    @Override protected Board board() { return props.lever(); }

    @Override
    protected List<RawJob> fetchCompany(Company company) {
        JsonNode root = getJson("https://api.lever.co/v0/postings/{slug}?mode=json", company.slug());
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root) {
            JsonNode cat = j.path("categories");
            Set<String> locations = new LinkedHashSet<>();
            if (cat.hasNonNull("location")) locations.add(cat.path("location").asText());
            for (JsonNode l : cat.path("allLocations")) locations.add(l.asText());
            String location = String.join(", ", locations);
            if ("remote".equalsIgnoreCase(j.path("workplaceType").asText())) location += " (Remote)";

            boolean isIndia = "IN".equalsIgnoreCase(j.path("country").asText()) || india.isIndia(location);
            if (!isIndia) continue;

            // main text + bullet lists (requirements often say "0-2 years" there)
            StringBuilder desc = new StringBuilder(j.path("descriptionPlain").asText(""));
            for (JsonNode list : j.path("lists")) {
                desc.append(' ').append(list.path("text").asText(""))
                    .append(": ").append(Text.plain(list.path("content").asText("")));
            }
            Instant posted = j.hasNonNull("createdAt") ? Instant.ofEpochMilli(j.path("createdAt").asLong()) : null;

            out.add(new RawJob(
                    source(),
                    company.slug() + ":" + j.path("id").asText(),
                    j.path("text").asText().strip(),
                    company.name(),
                    location.isBlank() ? "India" : location,
                    Text.plain(desc.toString()),
                    j.path("hostedUrl").asText(null),
                    posted));
        }
        return out;
    }
}
