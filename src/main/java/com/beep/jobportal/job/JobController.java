package com.beep.jobportal.job;

import com.beep.jobportal.run.RunLog;
import com.beep.jobportal.run.RunLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Public read API used by the web page. */
@RestController
public class JobController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CITIES = 25;

    private final JobRepository jobs;
    private final RunLogRepository runs;

    public JobController(JobRepository jobs, RunLogRepository runs) {
        this.jobs = jobs;
        this.runs = runs;
    }

    /** GET /api/jobs?q=java&role=Data%20Analyst&city=Pune&fresher=true&page=0&size=20  (newest first) */
    @GetMapping("/api/jobs")
    public Map<String, Object> list(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "") String role,
                                    @RequestParam(defaultValue = "") String city,
                                    @RequestParam(defaultValue = "false") boolean fresher,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        // order is fixed in the query (newest first)
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Pattern words = javaPattern(q);
        Page<JobView> result = jobs.search(toPattern(q), role.strip(), city.strip(), fresher, pageable)
                .map(j -> JobView.of(j, descriptionMatch(j, words)));
        return Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages());
    }

    /** GET /api/filters -> roles and top cities with counts, for the dropdowns */
    @GetMapping("/api/filters")
    public Map<String, Object> filters() {
        List<Object[]> cities = jobs.countByCity().stream()
                .filter(r -> isCityName((String) r[0]))
                .toList();
        return Map.of(
                "roles", toCounts(jobs.countByRole(), Integer.MAX_VALUE),
                "cities", toCounts(cities, MAX_CITIES));
    }

    /** Tags that are not a city (no city found, or only a country/state). Jobs still show under "All cities". */
    private static final Set<String> NOT_CITIES = Set.of(
            "unknown", "india", "andhra pradesh", "assam", "bihar", "gujarat", "haryana", "karnataka",
            "kerala", "madhya pradesh", "maharashtra", "odisha", "punjab", "rajasthan", "tamil nadu",
            "telangana", "uttar pradesh", "uttarakhand", "west bengal");

    static boolean isCityName(String name) {
        return name != null && !name.isBlank() && !NOT_CITIES.contains(name.strip().toLowerCase(Locale.ROOT));
    }

    /** GET /api/stats -> totals + when the last successful run finished */
    @GetMapping("/api/stats")
    public Map<String, Object> stats() {
        Map<String, Object> out = new HashMap<>();
        out.put("totalJobs", jobs.count());
        out.put("fresherJobs", jobs.countByIsFresherTrue());
        out.put("lastUpdated", runs.findFirstByStatusOrderByFinishedAtDesc("SUCCESS")
                .map(RunLog::getFinishedAt).orElse(null));
        return out;
    }

    /**
     * "java python" -> "\mjava\M|\mpython\M": a job matches if it mentions ANY of the words, as whole words
     * (Postgres \m = start of word, \M = end of word), so "java" does not match "javascript".
     * Each word is quoted so characters like "c++" or ".net" are matched literally.
     */
    public static String toPattern(String q) {
        return Arrays.stream(q.toLowerCase(Locale.ROOT).split("[\\s,]+"))
                .map(String::strip)
                .filter(w -> !w.isEmpty())
                .distinct()
                .limit(8)
                .map(w -> (Character.isLetterOrDigit(w.charAt(0)) ? "\\m" : "")
                        + w.replaceAll("([\\\\.\\[\\]{}()*+?^$|])", "\\\\$1")
                        + (Character.isLetterOrDigit(w.charAt(w.length() - 1)) ? "\\M" : ""))
                .collect(Collectors.joining("|"));
    }

    /** Same words as toPattern, as a Java regex (\b instead of Postgres \m / \M). */
    static Pattern javaPattern(String q) {
        String p = toPattern(q).replace("\\m", "\\b").replace("\\M", "\\b");
        return p.isEmpty() ? null : Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * When the search words are only in the description (not title, company or city),
     * return a short piece of text around the first match, so the page can show why the job is listed.
     */
    static String descriptionMatch(Job j, Pattern p) {
        if (p == null || j.getDescription() == null) return null;
        String shown = String.join(" ", nz(j.getTitle()), nz(j.getCompany()), nz(j.getLocationRaw()));
        if (p.matcher(shown).find()) return null;
        Matcher m = p.matcher(j.getDescription());
        if (!m.find()) return null;
        String d = j.getDescription();
        int from = Math.max(0, m.start() - 60), to = Math.min(d.length(), m.end() + 60);
        while (from > 0 && from < m.start() && !Character.isWhitespace(d.charAt(from - 1))) from++;   // start on a whole word
        while (to < d.length() && !Character.isWhitespace(d.charAt(to))) to--;     // end on a whole word
        if (to <= m.end()) to = Math.min(d.length(), m.end() + 60);
        return (from > 0 ? "…" : "") + d.substring(from, to).replaceAll("\\s+", " ").strip() + (to < d.length() ? "…" : "");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static List<Map<String, Object>> toCounts(List<Object[]> rows, int limit) {
        return rows.stream().limit(limit)
                .map(r -> Map.<String, Object>of("name", r[0], "count", r[1]))
                .toList();
    }
}
