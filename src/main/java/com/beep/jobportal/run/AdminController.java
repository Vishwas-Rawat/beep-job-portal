package com.beep.jobportal.run;

import com.beep.jobportal.fetch.EgressIp;
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

    private static final String TOKEN_HEADER = "X-Admin-Token";

    private final RefreshService refreshService;
    private final RunLogRepository runs;
    private final EgressIp egressIp;
    private final String adminToken;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AdminController(RefreshService refreshService, RunLogRepository runs, EgressIp egressIp,
                           @Value("${app.admin-token:}") String adminToken) {
        this.refreshService = refreshService;
        this.runs = runs;
        this.egressIp = egressIp;
        this.adminToken = adminToken;
    }

    /**
     * POST /api/admin/refresh  (header X-Admin-Token)
     * Default: start in background, return 202 at once.
     * ?wait=true: run now and return the run_log row (handy for testing).
     */
    @PostMapping("/api/admin/refresh")
    public ResponseEntity<?> refresh(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                     @RequestParam(defaultValue = "false") boolean wait) {
        ResponseEntity<?> denied = checkToken(token);
        if (denied != null) return denied;
        if (refreshService.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "refresh already running"));
        }
        if (wait) {
            return ResponseEntity.ok(refreshService.refresh());
        }
        executor.submit(refreshService::refresh);
        return ResponseEntity.accepted().body(Map.of("status", "started"));
    }

    /**
     * GET /api/admin/egress-ip  (header X-Admin-Token)
     * Which public IPv4 this server uses for outgoing calls, to declare it in Careerjet.
     */
    @GetMapping("/api/admin/egress-ip")
    public ResponseEntity<?> egressIp(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        ResponseEntity<?> denied = checkToken(token);
        if (denied != null) return denied;
        return ResponseEntity.ok(Map.of("ip", egressIp.current()));
    }

    /** Public: latest run (any status), for "last updated" and checking a background run. */
    @GetMapping("/api/runs/latest")
    public ResponseEntity<RunLog> latestRun() {
        return runs.findFirstByOrderByStartedAtDesc()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** null = allowed; otherwise the error response to return. */
    private ResponseEntity<?> checkToken(String given) {
        if (adminToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "ADMIN_TOKEN is not configured"));
        }
        // Constant-time compare so the token can't be guessed from response timing
        boolean ok = given != null && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), adminToken.getBytes(StandardCharsets.UTF_8));
        return ok ? null : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "bad token"));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
