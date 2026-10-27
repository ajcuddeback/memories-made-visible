package com.memoriesmadevisible.analytics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analytics;

    public AdminAnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    /** The Insights report for the last {@code days} days (default 30). */
    @GetMapping
    public AnalyticsDtos.Report report(@RequestParam(defaultValue = "30") int days) {
        return analytics.report(days);
    }
}
