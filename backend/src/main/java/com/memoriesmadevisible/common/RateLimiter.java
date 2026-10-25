package com.memoriesmadevisible.common;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny in-memory fixed-window limiter. The booking form and the login are
 * the only unauthenticated writes, and both send email or guard a password,
 * so a per-IP cap is cheap insurance against bots. Single-instance only by
 * design; swap for Redis if the API is ever scaled out.
 */
public class RateLimiter {

    private record Window(Instant start, int count) {}

    private final int max;
    private final Duration window;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int max, Duration window) {
        this.max = max;
        this.window = window;
    }

    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        Window w = windows.compute(key, (k, cur) -> {
            if (cur == null || cur.start.plus(window).isBefore(now)) {
                return new Window(now, 1);
            }
            return new Window(cur.start, cur.count + 1);
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().start.plus(window).isBefore(now));
        }
        return w.count <= max;
    }

    public static String clientKey(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
