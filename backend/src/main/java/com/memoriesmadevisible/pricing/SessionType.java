package com.memoriesmadevisible.pricing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** One of the studio's offerings (weddings, families, …) with its price packages. */
@Entity
@Table(name = "session_type")
public class SessionType {

    @Id
    @Column(name = "type_key", length = 40)
    private String key;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 300)
    private String description;

    /** Display string, e.g. "$150" or "$150/hr" — the booking quote is parsed from it. */
    @Column(name = "from_price", nullable = false, length = 20)
    private String fromPrice;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "sessionType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    private List<PricingTier> tiers = new ArrayList<>();

    protected SessionType() {}

    public SessionType(String key, String name, String description, String fromPrice, int sortOrder) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.fromPrice = fromPrice;
        this.sortOrder = sortOrder;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getFromPrice() { return fromPrice; }
    public void setFromPrice(String v) { this.fromPrice = v; }
    public int getSortOrder() { return sortOrder; }
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
}
