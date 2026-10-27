package com.memoriesmadevisible.publish;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One published snapshot per content kind (pricing, events, photos), stored as canonical JSON. */
@Entity
@Table(name = "published_site")
public class PublishedSite {

    @Id
    @Column(length = 20)
    private String kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    protected PublishedSite() {}

    public PublishedSite(String kind, String payload) {
        this.kind = kind;
        this.payload = payload;
        this.publishedAt = Instant.now();
    }

    public String getKind() { return kind; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; this.publishedAt = Instant.now(); }
    public Instant getPublishedAt() { return publishedAt; }
}
