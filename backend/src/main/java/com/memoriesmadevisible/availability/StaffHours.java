package com.memoriesmadevisible.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.Objects;

/** One weekday of one person's own booking window (overrides the studio's for them). */
@Entity
@Table(name = "staff_hours")
public class StaffHours {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "staff_name", length = 60)
        private String staffName;
        private int weekday;
        protected Key() {}
        public Key(String staffName, int weekday) { this.staffName = staffName; this.weekday = weekday; }
        public String getStaffName() { return staffName; }
        public int getWeekday() { return weekday; }
        @Override public boolean equals(Object o) { return o instanceof Key k && k.weekday == weekday && Objects.equals(k.staffName, staffName); }
        @Override public int hashCode() { return Objects.hash(staffName, weekday); }
    }

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private boolean open = true;

    @Column(name = "start_time", nullable = false)
    private LocalTime start = LocalTime.of(7, 0);

    @Column(name = "end_time", nullable = false)
    private LocalTime end = LocalTime.of(18, 0);

    protected StaffHours() {}

    public StaffHours(String staffName, int weekday) { this.id = new Key(staffName, weekday); }

    public Key getId() { return id; }
    public int getWeekday() { return id.getWeekday(); }
    public boolean isOpen() { return open; }
    public void setOpen(boolean v) { this.open = v; }
    public LocalTime getStart() { return start; }
    public void setStart(LocalTime v) { this.start = v; }
    public LocalTime getEnd() { return end; }
    public void setEnd(LocalTime v) { this.end = v; }
}
