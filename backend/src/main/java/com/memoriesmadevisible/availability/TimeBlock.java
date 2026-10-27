package com.memoriesmadevisible.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Time the studio isn't available: a doctor's appointment, a holiday, a week away.
 * Whole days when the times are null; otherwise the same time window on each day in the range.
 * The note is private to the studio — customers only ever see "unavailable".
 */
@Entity
@Table(name = "time_block")
public class TimeBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime v) { this.startTime = v; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime v) { this.endTime = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isAllDay() { return startTime == null || endTime == null; }
    public boolean covers(LocalDate d) { return !d.isBefore(startDate) && !d.isAfter(endDate); }

    /** Does this block overlap [start, end) on the given day? */
    public boolean overlaps(LocalDate day, LocalTime start, LocalTime end) {
        if (!covers(day)) return false;
        return isAllDay() || (startTime.isBefore(end) && start.isBefore(endTime));
    }
}
