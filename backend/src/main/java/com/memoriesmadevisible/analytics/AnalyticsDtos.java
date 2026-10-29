package com.memoriesmadevisible.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    /** Kinds the site sends. Anything else is dropped. */
    public static final List<String> KINDS = List.of(
            "view", "leave", "book_view", "book_type", "book_slot", "book_details", "book_submit", "contact_click");

    public record Incoming(@NotBlank @Size(max = 40) String visitor, @NotBlank @Size(max = 40) String session,
                           @NotBlank @Size(max = 30) String kind, @NotBlank @Size(max = 200) String path,
                           @Size(max = 200) String referrer, @Size(max = 10) String device, Integer durationMs) {}

    public record Batch(List<Incoming> events) {}

    // ── report ──
    public record Totals(long visitors, long visits, long pageviews, long avgVisitSeconds, double bounceRate) {}
    public record Day(String date, long visitors, long visits) {}
    public record Row(String label, long count) {}
    public record FunnelStep(String key, String label, long visits) {}
    public record Report(int days, Totals totals, List<Day> daily, List<Row> pages, List<Row> referrers, List<Row> devices,
                         List<FunnelStep> funnel, long bookingsSubmitted) {}
}
