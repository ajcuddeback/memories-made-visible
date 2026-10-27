package com.memoriesmadevisible.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** A lock row per day; see {@link CalendarDayRepository#lock}. */
@Entity
@Table(name = "calendar_day")
public class CalendarDay {

    @Id
    @Column(name = "cal_date")
    private LocalDate day;

    protected CalendarDay() {}

    public LocalDate getDay() { return day; }
}
