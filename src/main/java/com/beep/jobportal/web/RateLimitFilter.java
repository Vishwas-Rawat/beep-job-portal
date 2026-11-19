package com.beep.jobportal.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Per-IP fixed-window rate limit: 60 req/min on /api/*, 10 req/min on /api/admin/*, 6 req/min on /api/ask.
 * In-memory, fine for a single Render instance.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    static final int API_LIMIT = 60;
    static final int ADMIN_LIMIT = 10;
    static final int ASK_LIMIT = 6;   // each "Ask AI" call uses the shared Gemini quota
    private static final long WINDOW_MS = 60_000;

    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter() {
        this(System::currentTimeMillis);
    }

    RateLimitFilter(LongSupplier clock) {
        this.clock = clock;
    }

    private record Window(long start, AtomicInteger count) {}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String bucket = uri.startsWith("/api/admin/") ? "admin" : uri.equals("/api/ask") ? "ask" : "api";
        int limit = switch (bucket) {
            case "admin" -> ADMIN_LIMIT;
            case "ask" -> ASK_LIMIT;
            default -> API_LIMIT;
        };
        long now = clock.getAsLong();
        long start = now - (now % WINDOW_MS);

        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> w.start() < start);
        }

        String key = bucket + ":" + clientIp(request);
        Window w = windows.compute(key, (k, old) ->
                old == null || old.start() != start ? new Window(start, new AtomicInteger()) : old);

        if (w.count().incrementAndGet() > limit) {
            long retryAfter = Math.max(1, (start + WINDOW_MS - now + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"too many requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Render sits behind a proxy, so the real client is the first X-Forwarded-For entry. */
    static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
