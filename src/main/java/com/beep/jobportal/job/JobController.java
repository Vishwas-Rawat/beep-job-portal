package com.beep.jobportal.job;

import com.beep.jobportal.run.RunLog;
import com.beep.jobportal.run.RunLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Public read API used by the web page. Role/city/fresher filters come with the LLM tagger. */
@RestController
public class JobController {

    private static final int MAX_PAGE_SIZE = 100;

    private final JobRepository jobs;
    private final RunLogRepository runs;

    public JobController(JobRepository jobs, RunLogRepository runs) {
        this.jobs = jobs;
        this.runs = runs;
    }

    /** GET /api/jobs?q=java&page=0&size=20  (newest first) */
    @GetMapping("/api/jobs")
    public Map<String, Object> list(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("postedAt").nullsLast()));
        Page<JobView> result = jobs.search(q.strip(), pageable).map(JobView::of);
        return Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages());
    }

    /** GET /api/stats -> total jobs + when the last successful refresh finished */
    @GetMapping("/api/stats")
    public Map<String, Object> stats() {
        Map<String, Object> out = new HashMap<>();
        out.put("totalJobs", jobs.count());
        out.put("lastUpdated", runs.findFirstByStatusOrderByFinishedAtDesc("SUCCESS")
                .map(RunLog::getFinishedAt).orElse(null));
        return out;
    }
}
