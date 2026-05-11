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

    public ApiRateLimitFilter(@Value("${security.rate-limit.max-requests-per-minute:120}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        long now = System.currentTimeMillis();
        long windowMs = Duration.ofMinutes(1).toMillis();
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();

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
            if (counter.count.incrementAndGet() > maxRequestsPerMinute) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Too many requests\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
