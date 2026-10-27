package com.memoriesmadevisible.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;

/** The window customers can book inside, per weekday (0 = Sunday … 6 = Saturday). */
@Entity
@Table(name = "availability_hours")
public class AvailabilityHours {

    @Id
    private int weekday;

    @Column(nullable = false)
    private boolean open = true;

    @Column(name = "start_time", nullable = false)
    private LocalTime start = LocalTime.of(7, 0);

    @Column(name = "end_time", nullable = false)
    private LocalTime end = LocalTime.of(18, 0);

    protected AvailabilityHours() {}

    public AvailabilityHours(int weekday) { this.weekday = weekday; }

    public int getWeekday() { return weekday; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean v) { this.open = v; }
    public LocalTime getStart() { return start; }
    public void setStart(LocalTime v) { this.start = v; }
    public LocalTime getEnd() { return end; }
    public void setEnd(LocalTime v) { this.end = v; }
}
