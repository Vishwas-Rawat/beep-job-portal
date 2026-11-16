package com.beep.jobportal.fetch;

import com.beep.jobportal.fetch.SourcesProperties.Board;
import com.beep.jobportal.fetch.SourcesProperties.Company;
import com.beep.jobportal.normalize.IndiaLocations;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** https://api.ashbyhq.com/posting-api/job-board/{slug} */
@Component
public class AshbyFetcher extends CompanyBoardFetcher {

    public AshbyFetcher(RestClient http, SourcesProperties props, IndiaLocations india) {
        super(http, props, india);
    }

    @Override public String source() { return "ashby"; }
    @Override protected Board board() { return props.ashby(); }

    @Override
    protected List<RawJob> fetchCompany(Company company) {
        JsonNode root = getJson("https://api.ashbyhq.com/posting-api/job-board/{slug}?includeCompensation=false",
                company.slug());
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            if (!j.path("isListed").asBoolean(true)) continue;

            Set<String> locations = new LinkedHashSet<>();
            if (j.hasNonNull("location")) locations.add(j.path("location").asText());
            for (JsonNode l : j.path("secondaryLocations")) locations.add(l.path("location").asText());
            String location = String.join(", ", locations);
            if (j.path("isRemote").asBoolean(false)) location += " (Remote)";

            String country = j.path("address").path("postalAddress").path("addressCountry").asText("");
            if (!"India".equalsIgnoreCase(country) && !india.isIndia(location)) continue;

            out.add(new RawJob(
                    source(),
                    company.slug() + ":" + j.path("id").asText(),
                    j.path("title").asText().strip(),
                    company.name(),
                    location,
                    Text.plain(j.path("descriptionPlain").asText(null)),
                    j.path("jobUrl").asText(null),
                    Text.instant(j.path("publishedAt").asText(null))));
        }
        return out;
    }
}
