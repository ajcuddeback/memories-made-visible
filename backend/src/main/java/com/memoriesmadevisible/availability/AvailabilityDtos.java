package com.memoriesmadevisible.availability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class AvailabilityDtos {

    private AvailabilityDtos() {}

    static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    /** One weekday's booking window. Times are "HH:mm". */
    public record Hours(@Min(0) @Max(6) int weekday, boolean open, @NotNull String start, @NotNull String end) {}

    /** Admin: block time. {@code endDate} null = same day; times null = all day. */
    /** {@code staffName} null/blank = the whole studio. Employees can only block their own time. */
    public record BlockCreate(@NotNull LocalDate startDate, LocalDate endDate, String startTime, String endTime,
                              @Size(max = 200) String note, @Size(max = 60) String staffName) {}

    public record Block(Long id, LocalDate startDate, LocalDate endDate, String startTime, String endTime, String note, String staffName) {}

    /** Public: bookable start times on a day. */
    public record Slot(String start, String label) {}
    public record DaySlots(LocalDate date, List<Slot> slots) {}

    public static String hm(LocalTime t) { return t == null ? null : HM.format(t); }
    public static String label(LocalTime t) { return t == null ? null : LABEL.format(t); }

    static Hours toHours(AvailabilityHours h) {
        return new Hours(h.getWeekday(), h.isOpen(), hm(h.getStart()), hm(h.getEnd()));
    }

    static Block toBlock(TimeBlock b) {
        return new Block(b.getId(), b.getStartDate(), b.getEndDate(), hm(b.getStartTime()), hm(b.getEndTime()), b.getNote(), b.getStaffName());
    }

    /** "HH:mm" or "H:mm" → LocalTime; null/blank → null; anything else → 400. */
    public static LocalTime parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalTime.parse(s.strip().length() == 4 ? "0" + s.strip() : s.strip(), HM);
        } catch (Exception e) {
            throw new IllegalArgumentException("Times need to look like 09:00 or 14:30.");
        }
    }
}
