package com.memoriesmadevisible.pricing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A category on the pricing page (weddings, families, …) with its packages.
 * Admin-managed: created, renamed, reordered and removed from the studio.
 */
@Entity
@Table(name = "session_type")
public class SessionType {

    /** URL-safe slug, generated from the name on creation and never changed (bookings reference it). */
    @Id
    @Column(name = "type_key", length = 40)
    private String key;

    @Column(nullable = false, length = 80)
    private String name;

    /** Short label for filter chips and captions ("Families & solo"); falls back to the name. */
    @Column(name = "short_name", length = 40)
    private String shortName;

    @Column(nullable = false, length = 300)
    private String description;

    /** Display string, e.g. "$150" or "$150/hr" — the booking quote is parsed from it. */
    @Column(name = "from_price", nullable = false, length = 20)
    private String fromPrice;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Whether the Portfolio page shows a filter chip and three photo slots for this category. */
    @Column(name = "show_in_portfolio", nullable = false)
    private boolean showInPortfolio = true;

    @OneToMany(mappedBy = "sessionType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    private List<PricingTier> tiers = new ArrayList<>();

    protected SessionType() {}

    public SessionType(String key, String name, String shortName, String description, String fromPrice, int sortOrder) {
        this.key = key;
        this.name = name;
        this.shortName = shortName;
        this.description = description;
        this.fromPrice = fromPrice;
        this.sortOrder = sortOrder;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getShortName() { return shortName; }
    public void setShortName(String v) { this.shortName = v; }
    /** The label to show where space is tight. */
    public String displayShortName() { return shortName == null || shortName.isBlank() ? name : shortName; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getFromPrice() { return fromPrice; }
    public void setFromPrice(String v) { this.fromPrice = v; }
    public boolean isShowInPortfolio() { return showInPortfolio; }
    public void setShowInPortfolio(boolean v) { this.showInPortfolio = v; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public List<PricingTier> getTiers() { return tiers; }

    public void replaceTiers(List<PricingTier> fresh) {
        tiers.clear();
        for (int i = 0; i < fresh.size(); i++) {
            PricingTier t = fresh.get(i);
            t.setSessionType(this);
            t.setSortOrder(i);
            tiers.add(t);
        }
    }

    /** "Pets & Animals" → "pets-animals". */
    public static String slugify(String name) {
        String s = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (s.isEmpty()) s = "category";
        return s.length() > 36 ? s.substring(0, 36).replaceAll("-+$", "") : s;
    }
}
