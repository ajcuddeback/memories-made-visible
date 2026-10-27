package com.memoriesmadevisible.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    public enum PricingModel { FLAT, HOURLY }

    /** FLAT: fixed length and price. HOURLY: {@code price} is per hour, the customer picks {@code minHours}…{@code maxHours}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false, length = 10)
    private PricingModel pricingModel = PricingModel.FLAT;

    @Column(name = "min_hours", nullable = false)
    private int minHours = 2;

    @Column(name = "max_hours", nullable = false)
    private int maxHours = 8;

    /** Optional extras, one per line: {@code name|price|flat} or {@code name|price|hour}. */
    @Column(name = "addons_text", nullable = false, columnDefinition = "text")
    private String addonsText = "";

    /** An optional extra on a package; {@code perHour} extras scale with the session's hours. */
    public record AddOn(String name, java.math.BigDecimal price, boolean perHour) {}

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

    public PricingModel getPricingModel() { return pricingModel; }
    public void setPricingModel(PricingModel m) { this.pricingModel = m == null ? PricingModel.FLAT : m; }
    public boolean isHourly() { return pricingModel == PricingModel.HOURLY; }
    public int getMinHours() { return minHours; }
    public int getMaxHours() { return maxHours; }
    public void setHourRange(Integer min, Integer max) {
        int lo = min == null || min < 1 ? 1 : Math.min(min, 12);
        int hi = max == null || max < lo ? Math.max(lo, 8) : Math.min(max, 12);
        this.minHours = lo;
        this.maxHours = hi;
    }

    public List<AddOn> getAddOns() {
        if (addonsText == null || addonsText.isBlank()) return List.of();
        List<AddOn> out = new java.util.ArrayList<>();
        for (String line : addonsText.split("\\r?\\n")) {
            String[] p = line.split("\\|");
            if (p.length < 2 || p[0].isBlank()) continue;
            java.math.BigDecimal price;
            try { price = new java.math.BigDecimal(p[1].strip()); } catch (NumberFormatException e) { continue; }
            out.add(new AddOn(p[0].strip(), price, p.length > 2 && p[2].strip().equalsIgnoreCase("hour")));
        }
        return out;
    }
    public void setAddOns(List<AddOn> addOns) {
        StringBuilder sb = new StringBuilder();
        if (addOns != null) for (var a : addOns) {
            if (a == null || a.name() == null || a.name().isBlank() || a.price() == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(a.name().strip().replace("|", "/")).append('|').append(a.price().max(java.math.BigDecimal.ZERO).stripTrailingZeros().toPlainString())
              .append('|').append(a.perHour() ? "hour" : "flat");
        }
        this.addonsText = sb.toString();
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
