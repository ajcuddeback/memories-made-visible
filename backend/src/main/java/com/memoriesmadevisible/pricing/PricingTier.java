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

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected PricingTier() {}

    public PricingTier(String name, String detail, String price) {
        this.name = name;
        this.detail = detail;
        this.price = price;
    }

    public Long getId() { return id; }
    public SessionType getSessionType() { return sessionType; }
    void setSessionType(SessionType t) { this.sessionType = t; }
    public String getName() { return name; }
    public String getDetail() { return detail; }
    public String getPrice() { return price; }
    public int getSortOrder() { return sortOrder; }
    void setSortOrder(int v) { this.sortOrder = v; }
}
