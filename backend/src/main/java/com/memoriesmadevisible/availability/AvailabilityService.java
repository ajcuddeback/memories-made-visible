package com.memoriesmadevisible.availability;

import com.memoriesmadevisible.booking.Booking;
import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.booking.BookingStatus;
import com.memoriesmadevisible.common.ConflictException;
import com.memoriesmadevisible.common.NotFoundException;
import com.memoriesmadevisible.publish.PublishService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who can book when. Slots start every {@link #STEP} inside the weekday's hours, last the
 * session type's duration, and must not overlap a time block or a pending/confirmed booking.
 */
@Service
public class AvailabilityService {

    public static final Duration STEP = Duration.ofMinutes(30);
    /** Same-day requests need at least this much notice. */
    static final Duration NOTICE = Duration.ofHours(2);
    static final int DEFAULT_DURATION = 60;
    static final int MAX_RANGE_DAYS = 62;

    private final AvailabilityHoursRepository hours;
    private final TimeBlockRepository blocks;
    private final BookingRepository bookings;
    private final CalendarDayRepository days;
    private final PublishService publish;

    public AvailabilityService(AvailabilityHoursRepository hours, TimeBlockRepository blocks, BookingRepository bookings,
                               CalendarDayRepository days, PublishService publish) {
        this.hours = hours;
        this.blocks = blocks;
        this.bookings = bookings;
        this.days = days;
        this.publish = publish;
    }

    // ── hours ──

    @Transactional(readOnly = true)
    public List<AvailabilityDtos.Hours> hours() {
        return hours.findAllByOrderByWeekdayAsc().stream().map(AvailabilityDtos::toHours).toList();
    }

    @Transactional
    public List<AvailabilityDtos.Hours> saveHours(List<AvailabilityDtos.Hours> updates) {
        for (var u : updates) {
            var start = AvailabilityDtos.parse(u.start());
            var end = AvailabilityDtos.parse(u.end());
            if (start == null || end == null) throw new IllegalArgumentException("Each day needs a start and end time.");
            if (!start.isBefore(end)) throw new IllegalArgumentException("A day's end time has to be after its start time.");
            var h = hours.findById(u.weekday()).orElseGet(() -> new AvailabilityHours(u.weekday()));
            h.setOpen(u.open());
            h.setStart(start);
            h.setEnd(end);
            hours.save(h);
        }
        return hours();
    }

    // ── blocks ──

    @Transactional(readOnly = true)
    public List<AvailabilityDtos.Block> blocks(LocalDate from, LocalDate to) {
        return blocks.findAllByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscStartTimeAsc(from, to)
                .stream().map(AvailabilityDtos::toBlock).toList();
    }

    @Transactional
    public AvailabilityDtos.Block addBlock(AvailabilityDtos.BlockCreate c) {
        var b = new TimeBlock();
        b.setStartDate(c.startDate());
        b.setEndDate(c.endDate() == null ? c.startDate() : c.endDate());
        if (b.getEndDate().isBefore(b.getStartDate())) throw new IllegalArgumentException("The end date is before the start date.");
        var start = AvailabilityDtos.parse(c.startTime());
        var end = AvailabilityDtos.parse(c.endTime());
        if ((start == null) != (end == null)) throw new IllegalArgumentException("Give both a start and an end time, or neither for all day.");
        if (start != null && !start.isBefore(end)) throw new IllegalArgumentException("The end time has to be after the start time.");
        b.setStartTime(start);
        b.setEndTime(end);
        b.setNote(c.note() == null || c.note().isBlank() ? null : c.note().strip());
        return AvailabilityDtos.toBlock(blocks.save(b));
    }

    @Transactional
    public void removeBlock(Long id) {
        blocks.delete(blocks.findById(id).orElseThrow(() -> new NotFoundException("That block is already gone.")));
    }

    // ── slots ──

    public int durationFor(String typeKey) {
        return durationFor(typeKey, null);
    }

    /**
     * The package's length from the *published* price list (what the customer was shown), else the
     * category's default. A length edited in the admin applies once it's published, like a price.
     */
    public int durationFor(String typeKey, String packageName) {
        var type = publish.publishedType(typeKey).orElse(null);
        if (type == null) return DEFAULT_DURATION;
        if (packageName != null && !packageName.isBlank()) {
            var pkg = type.tiers().stream().filter(t -> t.name().equalsIgnoreCase(packageName.strip())).findFirst();
            if (pkg.isPresent() && pkg.get().durationMinutes() != null && pkg.get().durationMinutes() > 0) return pkg.get().durationMinutes();
        }
        return type.durationMinutes() > 0 ? type.durationMinutes() : DEFAULT_DURATION;
    }

    /** Bookable start times per day in [from, to] for a session of the given type. Days with none are left out. */
    @Transactional(readOnly = true)
    public List<AvailabilityDtos.DaySlots> slots(LocalDate from, LocalDate to, String typeKey, String packageName) {
        LocalDate today = LocalDate.now();
        if (from.isBefore(today)) from = today;
        if (to.isBefore(from)) return List.of();
        if (to.isAfter(from.plusDays(MAX_RANGE_DAYS))) to = from.plusDays(MAX_RANGE_DAYS);
        int minutes = durationFor(typeKey, packageName);
        var hoursByDay = hours.findAll().stream().collect(Collectors.toMap(AvailabilityHours::getWeekday, Function.identity()));
        var blockList = blocks.findAllByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscStartTimeAsc(from, to);
        var booked = bookings.findAllByDateBetweenAndStatusNot(from, to, BookingStatus.DECLINED);

        List<AvailabilityDtos.DaySlots> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            final LocalDate day = d;
            var starts = openStarts(day, minutes, hoursByDay, blockList,
                    booked.stream().filter(b -> b.getDate().equals(day)).toList());
            if (!starts.isEmpty()) {
                out.add(new AvailabilityDtos.DaySlots(day, starts.stream()
                        .map(t -> new AvailabilityDtos.Slot(AvailabilityDtos.hm(t), AvailabilityDtos.label(t))).toList()));
            }
        }
        return out;
    }

    private List<LocalTime> openStarts(LocalDate day, int minutes, Map<Integer, AvailabilityHours> hoursByDay,
                                       List<TimeBlock> blockList, List<Booking> sameDay) {
        var h = hoursByDay.get(day.getDayOfWeek().getValue() % 7);
        if (h == null || !h.isOpen()) return List.of();
        LocalDateTime cutoff = LocalDateTime.now().plus(NOTICE);
        List<LocalTime> out = new ArrayList<>();
        for (LocalTime t = h.getStart(); ; t = t.plus(STEP)) {
            LocalTime end = t.plusMinutes(minutes);
            if (end.isAfter(h.getEnd()) || end.isBefore(t) /* wrapped past midnight */) break;
            if (LocalDateTime.of(day, t).isBefore(cutoff)) continue;
            if (!clash(day, t, end, blockList, sameDay, null, true).isPresent()) out.add(t);
            if (t.plus(STEP).isBefore(t)) break;
        }
        return out;
    }

    /** What (if anything) a [start, end) on this day runs into. Pending bookings count only when asked. */
    private Optional<String> clash(LocalDate day, LocalTime start, LocalTime end, List<TimeBlock> blockList,
                                   List<Booking> sameDay, Long ignoreBookingId, boolean includePending) {
        for (var b : blockList) {
            if (b.overlaps(day, start, end)) return Optional.of("time you've blocked off" + (b.getNote() == null ? "" : " (" + b.getNote() + ")"));
        }
        for (var b : sameDay) {
            if (b.getId() != null && b.getId().equals(ignoreBookingId)) continue;
            if (b.getStatus() == BookingStatus.DECLINED) continue;
            if (b.getStatus() == BookingStatus.PENDING && !includePending) continue;
            if (b.getStartTime() == null || b.getEndTime() == null) continue; // legacy row without a time range
            if (b.getStartTime().isBefore(end) && start.isBefore(b.getEndTime())) {
                return Optional.of(b.getName() + "'s " + (b.getStatus() == BookingStatus.PENDING ? "pending request" : "confirmed session")
                        + " at " + AvailabilityDtos.label(b.getStartTime()));
            }
        }
        return Optional.empty();
    }

    /**
     * Take the day's lock, then confirm [start, start+duration) is still bookable. Must run inside
     * the caller's transaction, which then inserts/updates the booking before the lock is released.
     *
     * @param includePending true for a new public request (a pending request holds its slot);
     *                       false when approving (only confirmed sessions and blocks matter).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public LocalTime reserve(LocalDate day, LocalTime start, int minutes, Long ignoreBookingId, boolean includePending,
                             boolean enforceHours) {
        days.ensure(day);
        days.lock(day);
        LocalTime end = start.plusMinutes(minutes);
        if (enforceHours) {
            var h = hours.findById(day.getDayOfWeek().getValue() % 7).orElse(null);
            if (h == null || !h.isOpen() || start.isBefore(h.getStart()) || end.isAfter(h.getEnd()) || end.isBefore(start)) {
                throw new ConflictException("That time isn't within the studio's booking hours. Please pick another.");
            }
        }
        var blockList = blocks.findAllByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscStartTimeAsc(day, day);
        var sameDay = bookings.findAllByDateBetweenAndStatusNot(day, day, BookingStatus.DECLINED);
        clash(day, start, end, blockList, sameDay, ignoreBookingId, includePending).ifPresent(what -> {
            throw new ConflictException(includePending
                    ? "That time was just taken. Please pick another available time."
                    : "This overlaps " + what + ". Move or decline the other one first.");
        });
        return end;
    }
}
