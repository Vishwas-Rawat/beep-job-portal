package com.beep.jobportal.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebFiltersTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final RateLimitFilter limiter = new RateLimitFilter(now::get);

    private int call(String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.addHeader("X-Forwarded-For", ip + ", 10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        limiter.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void apiAllows60PerMinuteThenBlocks() throws Exception {
        for (int i = 0; i < 60; i++) assertEquals(200, call("/api/jobs", "1.1.1.1"));
        assertEquals(429, call("/api/jobs", "1.1.1.1"));
        assertEquals(200, call("/api/jobs", "2.2.2.2"));   // other IP unaffected
        assertEquals(200, call("/index.html", "1.1.1.1")); // non-API not limited

        now.addAndGet(60_000);                             // next window
        assertEquals(200, call("/api/jobs", "1.1.1.1"));
    }

    @Test
    void adminAllows10PerMinute() throws Exception {
        for (int i = 0; i < 10; i++) assertEquals(200, call("/api/admin/runs", "3.3.3.3"));
        assertEquals(429, call("/api/admin/runs", "3.3.3.3"));
    }

    @Test
    void askAllows6PerMinute() throws Exception {
        for (int i = 0; i < 6; i++) assertEquals(200, call("/api/ask", "5.5.5.5"));
        assertEquals(429, call("/api/ask", "5.5.5.5"));
        assertEquals(200, call("/api/jobs", "5.5.5.5"));   // normal search still works
    }

    @Test
    void blockedResponseHasRetryAfter() throws Exception {
        for (int i = 0; i < 10; i++) call("/api/admin/runs", "4.4.4.4");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/runs");
        req.setRemoteAddr("4.4.4.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        limiter.doFilter(req, res, new MockFilterChain());
        assertEquals(429, res.getStatus());
        assertNotNull(res.getHeader("Retry-After"));
    }

    @Test
    void securityHeadersAreSet() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(new MockHttpServletRequest("GET", "/"), res, new MockFilterChain());
        assertEquals("DENY", res.getHeader("X-Frame-Options"));
        assertEquals("nosniff", res.getHeader("X-Content-Type-Options"));
        assertEquals(SecurityHeadersFilter.CSP, res.getHeader("Content-Security-Policy"));
    }
}
