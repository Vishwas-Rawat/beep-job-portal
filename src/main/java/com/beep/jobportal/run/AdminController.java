package com.beep.jobportal.run;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Daily refresh trigger. Called by GitHub Actions cron (not @Scheduled),
 * because Render's free tier sleeps and an in-app scheduler would not fire.
 */
@RestController
public class AdminController {

    private final RefreshService refreshService;
    private final RunLogRepository runs;
    private final String adminToken;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AdminController(RefreshService refreshService, RunLogRepository runs,
                           @Value("${app.admin-token:}") String adminToken) {
        this.refreshService = refreshService;
        this.runs = runs;
        this.adminToken = adminToken;
    }

    /**
     * POST /api/admin/refresh  (header X-Admin-Token)
     * Default: start in background, return 202 at once.
     * ?wait=true: run now and return the run_log row (handy for testing).
     */
    @PostMapping("/api/admin/refresh")
    public ResponseEntity<?> refresh(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                     @RequestParam(defaultValue = "false") boolean wait) {
        if (adminToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "ADMIN_TOKEN is not configured"));
        }
        if (!tokenMatches(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "bad token"));
        }
        if (refreshService.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "refresh already running"));
        }
        if (wait) {
            return ResponseEntity.ok(refreshService.refresh());
        }
        executor.submit(refreshService::refresh);
        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /** Public: latest run (any status), for "last updated" and checking a background run. */
    @GetMapping("/api/runs/latest")
    public ResponseEntity<RunLog> latestRun() {
        return runs.findFirstByOrderByStartedAtDesc()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** Constant-time compare so the token can't be guessed from response timing. */
    private boolean tokenMatches(String given) {
        if (given == null) return false;
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
