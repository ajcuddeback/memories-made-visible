package com.memoriesmadevisible.analytics;

import com.memoriesmadevisible.common.StudioClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Stores what the site sends and turns it into the Insights report. Volumes are small (one studio's site), so aggregation is in memory. */
@Service
public class AnalyticsService {

    private static final int MAX_BATCH = 50;
    private static final int MAX_DAYS = 365;
    /** The admin's own pages are never counted. */
    private static final String ADMIN_PREFIX = "/studio";

    private final AnalyticsEventRepository events;
    private final StudioClock clock;

    public AnalyticsService(AnalyticsEventRepository events, StudioClock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public int record(AnalyticsDtos.Batch batch) {
        if (batch == null || batch.events() == null) return 0;
        List<AnalyticsEvent> rows = new ArrayList<>();
        Instant now = Instant.now();
        for (var in : batch.events().stream().limit(MAX_BATCH).toList()) {
            if (in == null || !AnalyticsDtos.KINDS.contains(in.kind())) continue;
            String path = normalizePath(in.path());
            if (path.startsWith(ADMIN_PREFIX)) continue;
            var e = new AnalyticsEvent();
            e.setVisitorId(clean(in.visitor(), 40));
            e.setSessionId(clean(in.session(), 40));
            e.setKind(in.kind());
            e.setPath(path);
            e.setReferrer(referrerHost(in.referrer()));
            e.setDevice("mobile".equals(in.device()) ? "mobile" : "desktop");
            e.setDurationMs(in.durationMs() == null ? null : Math.max(0, Math.min(in.durationMs(), 6 * 60 * 60 * 1000)));
            e.setOccurredAt(now);
            rows.add(e);
        }
        events.saveAll(rows);
        return rows.size();
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.Report report(int days) {
        days = Math.max(1, Math.min(MAX_DAYS, days));
        LocalDate start = clock.today().minusDays(days - 1L);
        Instant from = start.atStartOfDay(clock.zone()).toInstant();
        var all = events.findAllByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(from);

        Set<String> visitors = new HashSet<>();
        Set<String> sessions = new HashSet<>();
        long pageviews = 0;
        Map<String, Long> timeBySession = new HashMap<>();
        Map<String, Integer> viewsBySession = new HashMap<>();
        Map<String, Long> pages = new HashMap<>();
        Map<String, Long> referrers = new HashMap<>();
        Map<String, Long> devices = new HashMap<>();
        TreeMap<LocalDate, Set<String>> visitorsByDay = new TreeMap<>();
        TreeMap<LocalDate, Set<String>> sessionsByDay = new TreeMap<>();
        Map<String, Set<String>> funnelSessions = new LinkedHashMap<>();
        for (String k : List.of("book_view", "book_type", "book_slot", "book_details", "book_submit")) funnelSessions.put(k, new HashSet<>());
        Set<String> sessionsWithReferrer = new HashSet<>();
        Set<String> sessionsWithDevice = new HashSet<>();

        for (var e : all) {
            visitors.add(e.getVisitorId());
            sessions.add(e.getSessionId());
            LocalDate day = ZonedDateTime.ofInstant(e.getOccurredAt(), clock.zone()).toLocalDate();
            switch (e.getKind()) {
                case "view" -> {
                    pageviews++;
                    pages.merge(e.getPath(), 1L, Long::sum);
                    viewsBySession.merge(e.getSessionId(), 1, Integer::sum);
                    visitorsByDay.computeIfAbsent(day, d -> new HashSet<>()).add(e.getVisitorId());
                    sessionsByDay.computeIfAbsent(day, d -> new HashSet<>()).add(e.getSessionId());
                    if (sessionsWithReferrer.add(e.getSessionId())) {
                        referrers.merge(e.getReferrer() == null ? "Direct / typed in" : e.getReferrer(), 1L, Long::sum);
                    }
                    if (sessionsWithDevice.add(e.getSessionId())) devices.merge(e.getDevice(), 1L, Long::sum);
                }
                case "leave" -> { if (e.getDurationMs() != null) timeBySession.merge(e.getSessionId(), e.getDurationMs().longValue(), Long::sum); }
                default -> { var set = funnelSessions.get(e.getKind()); if (set != null) set.add(e.getSessionId()); }
            }
        }

        long avgSeconds = timeBySession.isEmpty() ? 0 : Math.round(timeBySession.values().stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0);
        long singlePage = viewsBySession.values().stream().filter(n -> n == 1).count();
        double bounce = viewsBySession.isEmpty() ? 0 : (double) singlePage / viewsBySession.size();

        List<AnalyticsDtos.Day> daily = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(clock.today()); d = d.plusDays(1)) {
            daily.add(new AnalyticsDtos.Day(d.toString(),
                    visitorsByDay.getOrDefault(d, Set.of()).size(), sessionsByDay.getOrDefault(d, Set.of()).size()));
        }
        var funnel = List.of(
                step("book_view", "Opened the booking page", funnelSessions),
                step("book_type", "Chose a session type", funnelSessions),
                step("book_slot", "Picked a date & time", funnelSessions),
                step("book_details", "Filled in their details", funnelSessions),
                step("book_submit", "Sent the request", funnelSessions));
        return new AnalyticsDtos.Report(days,
                new AnalyticsDtos.Totals(visitors.size(), sessions.size(), pageviews, avgSeconds, bounce),
                daily, top(pages, 8), top(referrers, 6), top(devices, 2), funnel, funnelSessions.get("book_submit").size());
    }

    private static AnalyticsDtos.FunnelStep step(String key, String label, Map<String, Set<String>> m) {
        return new AnalyticsDtos.FunnelStep(key, label, m.get(key).size());
    }

    private static List<AnalyticsDtos.Row> top(Map<String, Long> m, int n) {
        return m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(n).map(e -> new AnalyticsDtos.Row(e.getKey(), e.getValue())).toList();
    }

    private static String normalizePath(String p) {
        if (p == null || p.isBlank()) return "/";
        String s = p.strip();
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /** Only the referring site's host is kept ("google.com", "instagram.com"), never the full URL. */
    private static String referrerHost(String r) {
        if (r == null || r.isBlank()) return null;
        try {
            String host = java.net.URI.create(r.strip()).getHost();
            if (host == null) return null;
            host = host.toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);
            if (host.contains("memoriesmadevisible") || host.equals("localhost")) return null; // our own pages
            return host.length() > 200 ? host.substring(0, 200) : host;
        } catch (Exception e) {
            return null;
        }
    }

    private static String clean(String s, int max) {
        String t = s.replaceAll("[^A-Za-z0-9_-]", "");
        return t.length() > max ? t.substring(0, max) : t;
    }
}
