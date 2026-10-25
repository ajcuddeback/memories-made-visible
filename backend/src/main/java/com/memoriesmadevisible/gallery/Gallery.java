package com.memoriesmadevisible.gallery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Post-session delivery pipeline for a confirmed booking.
 * Stage: 0 Shot → 1 Editing → 2 Proofing → 3 Delivered.
 */
@Entity
@Table(name = "gallery")
public class Gallery {

    public static final int MAX_STAGE = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(nullable = false)
    private int stage = 0;

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long v) { this.bookingId = v; }
    public int getStage() { return stage; }
    public void setStage(int v) { this.stage = Math.max(0, Math.min(MAX_STAGE, v)); }
}
