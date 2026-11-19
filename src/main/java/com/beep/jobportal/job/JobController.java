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
        Page<JobView> result = jobs.search(toPattern(q), role.strip(), city.strip(), fresher, pageable)
                .map(JobView::of);
        return Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages());
    }

    /** GET /api/filters -> roles and top cities with counts, for the dropdowns */
    @GetMapping("/api/filters")
    public Map<String, Object> filters() {
        return Map.of(
                "roles", toCounts(jobs.countByRole(), Integer.MAX_VALUE),
                "cities", toCounts(jobs.countByCity(), MAX_CITIES));
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
     * "java python" -> "java|python": a job matches if it mentions ANY of the words.
     * Each word is quoted so characters like "c++" or ".net" are matched literally.
     */
    static String toPattern(String q) {
        return Arrays.stream(q.toLowerCase(Locale.ROOT).split("[\\s,]+"))
                .map(String::strip)
                .filter(w -> !w.isEmpty())
                .distinct()
                .limit(8)
                .map(w -> w.replaceAll("([\\\\.\\[\\]{}()*+?^$|])", "\\\\$1"))
                .collect(Collectors.joining("|"));
    }

    private static List<Map<String, Object>> toCounts(List<Object[]> rows, int limit) {
        return rows.stream().limit(limit)
                .map(r -> Map.<String, Object>of("name", r[0], "count", r[1]))
                .toList();
    }
}
