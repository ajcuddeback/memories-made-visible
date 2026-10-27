package com.memoriesmadevisible.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;

/** One package inside a category: a name, a one-line summary, a price, and the full "what's included" list. */
@Entity
@Table(name = "pricing_tier")
public class PricingTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_type_key")
    private SessionType sessionType;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 200)
    private String detail;

    @Column(nullable = false, length = 20)
    private String price;

    /** One bullet per line. Stored as text so the schema stays simple. */
    @Column(name = "includes_text", nullable = false, columnDefinition = "text")
    private String includesText = "";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** How long this package blocks the calendar; the booking page offers slots of this length. */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 60;

    protected PricingTier() {}

    public PricingTier(String name, String detail, String price) {
        this(name, detail, price, List.of());
    }

    public PricingTier(String name, String detail, String price, List<String> includes) {
        this(name, detail, price, includes, 60);
    }

    public PricingTier(String name, String detail, String price, List<String> includes, int durationMinutes) {
        this.name = name;
        this.detail = detail;
        this.price = price;
        setIncludes(includes);
        setDurationMinutes(durationMinutes);
    }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int v) { this.durationMinutes = v <= 0 ? 60 : Math.max(15, Math.min(12 * 60, v)); }

    public Long getId() { return id; }
    public SessionType getSessionType() { return sessionType; }
    void setSessionType(SessionType t) { this.sessionType = t; }
    public String getName() { return name; }
    public String getDetail() { return detail; }
    public String getPrice() { return price; }
    public int getSortOrder() { return sortOrder; }
    void setSortOrder(int v) { this.sortOrder = v; }

    public List<String> getIncludes() {
        if (includesText == null || includesText.isBlank()) return List.of();
        return Arrays.stream(includesText.split("\\r?\\n")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    public void setIncludes(List<String> items) {
        this.includesText = items == null ? "" : String.join("\n",
                items.stream().map(s -> s == null ? "" : s.strip()).filter(s -> !s.isEmpty()).toList());
    }
}
