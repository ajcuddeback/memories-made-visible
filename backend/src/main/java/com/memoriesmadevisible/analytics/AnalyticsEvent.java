package com.memoriesmadevisible.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One anonymous event from the public site: a page view, time spent on a page, or a booking-funnel step. */
@Entity
@Table(name = "analytics_event")
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visitor_id", nullable = false, length = 40)
    private String visitorId;

    @Column(name = "session_id", nullable = false, length = 40)
    private String sessionId;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(nullable = false, length = 200)
    private String path;

    @Column(length = 200)
    private String referrer;

    @Column(nullable = false, length = 10)
    private String device;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public Long getId() { return id; }
    public String getVisitorId() { return visitorId; }
    public void setVisitorId(String v) { this.visitorId = v; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String v) { this.sessionId = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }
    public String getPath() { return path; }
    public void setPath(String v) { this.path = v; }
    public String getReferrer() { return referrer; }
    public void setReferrer(String v) { this.referrer = v; }
    public String getDevice() { return device; }
    public void setDevice(String v) { this.device = v; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer v) { this.durationMs = v; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant v) { this.occurredAt = v; }
}
