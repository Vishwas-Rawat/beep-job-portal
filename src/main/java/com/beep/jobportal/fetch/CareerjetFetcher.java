package com.beep.jobportal.fetch;

import com.beep.jobportal.fetch.SourcesProperties.Careerjet;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Careerjet partner API: keyword search across Indian job sites.
 * This is where most fresher/intern posts come from; company ATS boards are mostly experienced roles.
 *
 * GET https://search.api.careerjet.net/v4/query  (HTTP Basic auth: key as username, empty password)
 */
@Component
public class CareerjetFetcher implements JobFetcher {

    private static final Logger log = LoggerFactory.getLogger(CareerjetFetcher.class);
    private static final String URL = "https://search.api.careerjet.net/v4/query"
            + "?locale_code={locale}&keywords={kw}&pagesize={size}&page={page}&sort=date"
            + "&user_ip={ip}&user_agent={ua}";
    private static final String USER_AGENT = "beep-job-portal/0.1 (daily batch)";

    private final RestClient http;
    private final SourcesProperties props;
    private final EgressIp egressIp;

    public CareerjetFetcher(RestClient http, SourcesProperties props, EgressIp egressIp) {
        this.http = http;
        this.props = props;
        this.egressIp = egressIp;
    }

    @Override public String source() { return "careerjet"; }

    @Override
    public List<RawJob> fetch() {
        Careerjet cfg = props.careerjet();
        if (cfg == null || !cfg.enabled() || cfg.apiKey() == null || cfg.apiKey().isBlank()) {
            log.info("careerjet: skipped (disabled or no CAREERJET_API_KEY)");
            return List.of();
        }
        // Batch run, no end user: send our own server IP as user_ip.
        String ip = egressIp.current();
        log.info("careerjet: calling from IP {}", ip);

        List<RawJob> out = new ArrayList<>();
        for (String keyword : cfg.keywords()) {
            for (int page = 1; page <= cfg.pagesPerKeyword(); page++) {
                try {
                    JsonNode root = http.get()
                            .uri(URL, cfg.locale(), keyword, cfg.pageSize(), page, ip, USER_AGENT)
                            .headers(h -> {
                                h.setBasicAuth(cfg.apiKey(), "");
                                h.set(HttpHeaders.REFERER, cfg.referer());
                            })
                            .retrieve()
                            .body(JsonNode.class);
                    if (root == null || !"JOBS".equals(root.path("type").asText())) {
                        log.warn("careerjet '{}' p{}: unexpected response {}", keyword, page,
                                root == null ? "null" : root.path("error").asText(root.toString()));
                        break;
                    }
                    int before = out.size();
                    for (JsonNode j : root.path("jobs")) {
                        RawJob job = toRawJob(j);
                        if (job != null) out.add(job);
                    }
                    log.info("careerjet '{}' p{}: {} jobs", keyword, page, out.size() - before);
                    if (page >= root.path("pages").asInt(1)) break;   // no more pages
                } catch (Exception e) {
                    // 403 here usually means: IP not declared, or Referer missing/wrong
                    log.warn("careerjet '{}' p{} failed: {}", keyword, page, e.getMessage());
                    break;
                }
            }
        }
        return out;
    }

    private RawJob toRawJob(JsonNode j) {
        String title = Text.orNull(j.path("title").asText(null));
        String url = Text.orNull(j.path("url").asText(null));
        if (title == null || url == null) return null;

        String company = Text.orNull(j.path("company").asText(null));
        String locations = Text.orNull(j.path("locations").asText(null));
        String date = j.path("date").asText(null);

        // Careerjet gives no job id. Build a stable one from the fields that identify the post.
        String id = sha1(title + "|" + company + "|" + locations + "|" + date);

        return new RawJob(
                source(),
                id,
                title,
                company == null ? "Company not disclosed" : company,
                locations == null ? "India" : locations + ", India",
                Text.plain(j.path("description").asText(null)),
                url,                         // Careerjet tracking link: required by their terms
                parseDate(date));
    }

    /** "Sun, 15 Nov 2026 07:59:10 GMT" */
    private static Instant parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 20);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
