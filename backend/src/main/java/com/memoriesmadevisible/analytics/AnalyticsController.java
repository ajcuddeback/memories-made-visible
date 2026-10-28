package com.memoriesmadevisible.analytics;

import com.memoriesmadevisible.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Public: the site posts small batches of anonymous events here (also via sendBeacon on page leave). */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final RateLimiter limiter = new RateLimiter(300, Duration.ofMinutes(15));
    /** A full batch (50 events) is well under 20 KB; anything bigger isn't the site talking. */
    static final int MAX_BODY_BYTES = 64 * 1024;

    public AnalyticsController(AnalyticsService analytics, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.analytics = analytics;
        this.json = json;
    }

    /** Bodies arrive as application/json (fetch) or text/plain (sendBeacon on page leave); both are the same JSON. */
    @PostMapping(consumes = {"application/json", "text/plain"})
    public ResponseEntity<Void> record(@RequestBody String raw, HttpServletRequest http) {
        if (raw != null && raw.length() <= MAX_BODY_BYTES && limiter.tryAcquire(RateLimiter.clientKey(http))) {
            try {
                analytics.record(json.readValue(raw, AnalyticsDtos.Batch.class));
            } catch (Exception ignored) {
                // malformed or oversized: drop it; analytics never surfaces errors to visitors
            }
        }
        return ResponseEntity.noContent().build();
    }
}
