package com.example.situation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static class WindowCounter {
        private volatile long windowStartMs;
        private final AtomicInteger count = new AtomicInteger(0);
    }

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final int maxRequestsPerMinute;
    private final int authMaxRequestsPerMinute;

    public ApiRateLimitFilter(
        @Value("${security.rate-limit.max-requests-per-minute:120}") int maxRequestsPerMinute,
        @Value("${security.rate-limit.auth-max-requests-per-minute:10}") int authMaxRequestsPerMinute
    ) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.authMaxRequestsPerMinute = authMaxRequestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/") || path.startsWith("/api/v1/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        long now = System.currentTimeMillis();
        long windowMs = Duration.ofMinutes(1).toMillis();
        String key = resolveClientIp(request) + "|" + request.getRequestURI();
        int limit = isAuthenticationRequest(request) ? authMaxRequestsPerMinute : maxRequestsPerMinute;

        WindowCounter counter = counters.computeIfAbsent(key, k -> {
            WindowCounter wc = new WindowCounter();
            wc.windowStartMs = now;
            return wc;
        });

        synchronized (counter) {
            if (now - counter.windowStartMs >= windowMs) {
                counter.windowStartMs = now;
                counter.count.set(0);
            }
            if (counter.count.incrementAndGet() > limit) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Too many requests. Please wait before trying again.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAuthenticationRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") || path.startsWith("/api/v1/auth/");
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
