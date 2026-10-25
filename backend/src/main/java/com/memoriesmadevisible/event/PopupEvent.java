package com.memoriesmadevisible.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A themed photo set at a market or community event, with on-site print sales. */
@Entity
@Table(name = "popup_event")
public class PopupEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 120)
    private String theme;

    @Column(nullable = false, length = 160)
    private String venue;

    @Column(name = "hours", nullable = false, length = 60)
    private String time;

    @Column(name = "prints_sold", nullable = false)
    private int prints = 0;

    @Column(name = "print_price", nullable = false, precision = 8, scale = 2)
    private BigDecimal printPrice = BigDecimal.TEN;

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate v) { this.date = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getTheme() { return theme; }
    public void setTheme(String v) { this.theme = v; }
    public String getVenue() { return venue; }
    public void setVenue(String v) { this.venue = v; }
    public String getTime() { return time; }
    public void setTime(String v) { this.time = v; }
    public int getPrints() { return prints; }
    public void setPrints(int v) { this.prints = Math.max(0, v); }
    public BigDecimal getPrintPrice() { return printPrice; }
    public void setPrintPrice(BigDecimal v) { this.printPrice = v == null ? BigDecimal.ZERO : v.max(BigDecimal.ZERO); }
}
