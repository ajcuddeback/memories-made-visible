package com.memoriesmadevisible.analytics;

import com.memoriesmadevisible.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    private final RateLimiter limiter = new RateLimiter(300, Duration.ofMinutes(15));

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @PostMapping(consumes = {"application/json", "text/plain"})
    public ResponseEntity<Void> record(@Valid @RequestBody AnalyticsDtos.Batch batch, HttpServletRequest http) {
        if (limiter.tryAcquire(RateLimiter.clientKey(http))) analytics.record(batch);
        return ResponseEntity.noContent().build(); // never an error the browser would retry
    }
}
