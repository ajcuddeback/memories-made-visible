package com.memoriesmadevisible.availability;

import com.memoriesmadevisible.booking.Booking;
import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.booking.BookingStatus;
import com.memoriesmadevisible.common.ConflictException;
import com.memoriesmadevisible.common.StudioClock;
import com.memoriesmadevisible.common.NotFoundException;
import com.memoriesmadevisible.publish.PublishService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    /** How far ahead the public form may book. */
    static final int MAX_AHEAD_MONTHS = 18;

    private final AvailabilityHoursRepository hours;
    private final TimeBlockRepository blocks;
    private final BookingRepository bookings;
    private final CalendarDayRepository days;
    private final PublishService publish;
    private final StudioClock clock;
    private final com.memoriesmadevisible.auth.AdminUserRepository staff;
    private final StaffHoursRepository staffHours;

    public AvailabilityService(AvailabilityHoursRepository hours, TimeBlockRepository blocks, BookingRepository bookings,
                               CalendarDayRepository days, PublishService publish, StudioClock clock,
                               com.memoriesmadevisible.auth.AdminUserRepository staff, StaffHoursRepository staffHours) {
        this.staffHours = staffHours;
        this.hours = hours;
        this.blocks = blocks;
        this.bookings = bookings;
        this.days = days;
        this.publish = publish;
        this.clock = clock;
        this.staff = staff;
    }

    /** Rows from before scheduling existed get a time range from their label so they block the calendar too. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void backfillLegacyTimes() {
        var fmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US);
        for (var b : bookings.findAll()) {
            if (b.getStartTime() != null || b.getTime() == null) continue;
            try {
                LocalTime start = LocalTime.parse(b.getTime().strip().toUpperCase(java.util.Locale.US), fmt);
                b.setStartTime(start);
                b.setEndTime(start.plusMinutes(durationFor(b.getSessionTypeKey(), b.getPackageName(), b.getHours())));
                bookings.save(b);
            } catch (Exception ignored) {
                // "Golden hour" and friends: leave as-is; the admin can Move it to a real time.
            }
        }
    }

    // ── hours ──

    @Transactional(readOnly = true)
    public List<AvailabilityDtos.Hours> hours() {
        return hours.findAllByOrderByWeekdayAsc().stream().map(AvailabilityDtos::toHours).toList();
    }

    /** The week that applies to a person: their own if set, else the studio's. */
    @Transactional(readOnly = true)
    public AvailabilityDtos.Week week(String staffName) {
        if (staffName == null || staffName.isBlank()) return new AvailabilityDtos.Week(null, false, hours());
        var own = staffHours.findAllByIdStaffNameIgnoreCaseOrderByIdWeekdayAsc(staffName.strip());
        if (own.isEmpty()) return new AvailabilityDtos.Week(staffName.strip(), false, hours());
        return new AvailabilityDtos.Week(staffName.strip(), true, own.stream()
                .map(h -> new AvailabilityDtos.Hours(h.getWeekday(), h.isOpen(), AvailabilityDtos.hm(h.getStart()), AvailabilityDtos.hm(h.getEnd()))).toList());
    }

    /** Give a person their own week (every weekday they didn't send keeps its current/studio value). */
    @Transactional
    public AvailabilityDtos.Week saveStaffHours(String staffName, List<AvailabilityDtos.Hours> updates) {
        var u = staff.findByNameIgnoreCase(staffName.strip()).orElseThrow(() -> new NotFoundException("No account called " + staffName));
        var current = week(u.getName()).hours();
        for (int wd = 0; wd < 7; wd++) {
            final int d = wd;
            var row = staffHours.findById(new StaffHours.Key(u.getName(), d)).orElseGet(() -> new StaffHours(u.getName(), d));
            var src = updates.stream().filter(x -> x.weekday() == d).findFirst()
                    .orElseGet(() -> current.stream().filter(x -> x.weekday() == d).findFirst().orElse(new AvailabilityDtos.Hours(d, true, "07:00", "18:00")));
            var start = AvailabilityDtos.parse(src.start());
            var end = AvailabilityDtos.parse(src.end());
            if (start == null || end == null) throw new IllegalArgumentException("Each day needs a start and end time.");
            if (!start.isBefore(end)) throw new IllegalArgumentException("A day's end time has to be after its start time.");
            row.setOpen(src.open());
            row.setStart(start);
            row.setEnd(end);
            staffHours.save(row);
        }
        return week(u.getName());
    }

    /** Back to the studio's hours for this person. */
    @Transactional
    public AvailabilityDtos.Week clearStaffHours(String staffName) {
        staffHours.deleteAllByIdStaffNameIgnoreCase(staffName.strip());
        return week(staffName);
    }

    /** A weekday's window for a photographer (null = the studio). */
    private Window windowFor(String photographer, int weekday, Map<Integer, AvailabilityHours> studio, Map<String, Map<Integer, StaffHours>> own) {
        if (photographer != null) {
            var mine = own.computeIfAbsent(photographer.toLowerCase(), k -> {
                Map<Integer, StaffHours> m = new HashMap<>();
                staffHours.findAllByIdStaffNameIgnoreCaseOrderByIdWeekdayAsc(photographer).forEach(h -> m.put(h.getWeekday(), h));
                return m;
            });
            if (!mine.isEmpty()) {
                var h = mine.get(weekday);
                return h == null || !h.isOpen() ? null : new Window(h.getStart(), h.getEnd());
            }
        }
        var h = studio.get(weekday);
        return h == null || !h.isOpen() ? null : new Window(h.getStart(), h.getEnd());
    }
    private record Window(LocalTime start, LocalTime end) {
        boolean contains(LocalTime s, LocalTime e) { return !s.isBefore(start) && !e.isAfter(end); }
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
    public AvailabilityDtos.Block addBlock(AvailabilityDtos.BlockCreate c, String by, boolean limitedToSelf) {
        var b = new TimeBlock();
        String who = c.staffName() == null || c.staffName().isBlank() ? null : c.staffName().strip();
        if (limitedToSelf) who = by; // employees block their own time, never the studio's
        if (who != null) {
            var u = staff.findByNameIgnoreCase(who).orElseThrow(() -> new IllegalArgumentException("No account called " + c.staffName()));
            who = u.getName();
        }
        b.setStaffName(who);
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
    public void removeBlock(Long id, String by, boolean limitedToSelf) {
        var b = blocks.findById(id).orElseThrow(() -> new NotFoundException("That block is already gone."));
        if (limitedToSelf && (b.getStaffName() == null || !b.getStaffName().equalsIgnoreCase(by))) {
            throw new org.springframework.security.access.AccessDeniedException("Not your block");
        }
        blocks.delete(b);
    }

    /** Photographers customers may choose. Empty means the studio books as one unit (no per-person capacity). */
    @Transactional(readOnly = true)
    public List<String> photographers() {
        return staff.findAllByActiveTrueAndBookableTrueOrderByNameAsc().stream().map(com.memoriesmadevisible.auth.AdminUser::getName).toList();
    }

    /** The booking's lead if they're a bookable photographer, else null (the booking then counts against everyone). */
    public static String photographerOf(Booking b, List<String> photographers) {
        String lead = b.getLead();
        if (lead != null) for (String p : photographers) if (p.equalsIgnoreCase(lead)) return p;
        return null;
    }

    /** Does this booking take up the given photographer's time? Every bookable person on it is busy; an unassigned booking blocks everyone. */
    static boolean occupies(Booking b, String photographer, List<String> photographers) {
        if (photographer == null) return true;
        boolean anyBookable = false;
        for (String a : b.getAssignees()) {
            for (String p : photographers) if (p.equalsIgnoreCase(a)) { anyBookable = true; if (p.equalsIgnoreCase(photographer)) return true; }
        }
        return !anyBookable;
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
        return durationFor(typeKey, packageName, null);
    }

    /** Hourly packages run {@code hours} (clamped to the package's range, default its minimum). */
    public int durationFor(String typeKey, String packageName, Integer hours) {
        var type = publish.publishedType(typeKey).orElse(null);
        if (type == null) return DEFAULT_DURATION;
        if (packageName != null && !packageName.isBlank()) {
            var pkg = type.tiers().stream().filter(t -> t.name().equalsIgnoreCase(packageName.strip())).findFirst().orElse(null);
            if (pkg != null) {
                if (pkg.hourly()) {
                    int lo = pkg.minHours() == null ? 1 : pkg.minHours(), hi = pkg.maxHours() == null ? 12 : pkg.maxHours();
                    int h = hours == null ? lo : Math.max(lo, Math.min(hi, hours));
                    return h * 60;
                }
                if (pkg.durationMinutes() != null && pkg.durationMinutes() > 0) return pkg.durationMinutes();
            }
        }
        return type.durationMinutes() > 0 ? type.durationMinutes() : DEFAULT_DURATION;
    }

    /** Bookable start times per day in [from, to] for a session of the given type. Days with none are left out. */
    @Transactional(readOnly = true)
    public List<AvailabilityDtos.DaySlots> slots(LocalDate from, LocalDate to, String typeKey, String packageName, Integer bookedHours) {
        return slots(from, to, typeKey, packageName, bookedHours, null);
    }

    /**
     * @param photographer a bookable photographer's name, or null for "no preference" (open if anyone is free).
     *                     Ignored when nobody is bookable — the studio then books as one.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityDtos.DaySlots> slots(LocalDate from, LocalDate to, String typeKey, String packageName, Integer bookedHours,
                                                 String photographer) {
        LocalDate today = clock.today();
        if (from.isBefore(today)) from = today;
        if (to.isBefore(from)) return List.of();
        if (to.isAfter(from.plusDays(MAX_RANGE_DAYS))) to = from.plusDays(MAX_RANGE_DAYS);
        int minutes = durationFor(typeKey, packageName, bookedHours);
        var hoursByDay = hours.findAll().stream().collect(Collectors.toMap(AvailabilityHours::getWeekday, Function.identity()));
        var blockList = blocks.findAllByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscStartTimeAsc(from, to);
        var booked = bookings.findAllByDateBetweenAndStatusNot(from, to, BookingStatus.DECLINED);
        var candidates = candidates(photographer);

        List<AvailabilityDtos.DaySlots> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            final LocalDate day = d;
            var starts = openStarts(day, minutes, hoursByDay, blockList,
                    booked.stream().filter(b -> b.getDate().equals(day)).toList(), candidates);
            if (!starts.isEmpty()) {
                out.add(new AvailabilityDtos.DaySlots(day, starts.stream()
                        .map(t -> new AvailabilityDtos.Slot(AvailabilityDtos.hm(t), AvailabilityDtos.label(t))).toList()));
            }
        }
        return out;
    }

    /**
     * Who a slot must be free for. Nobody bookable → [null] (one studio-wide capacity). A named
     * photographer → [that one]. No preference → every bookable photographer (free if any is).
     */
    private List<String> candidates(String photographer) {
        var all = photographers();
        if (all.isEmpty()) return java.util.Collections.singletonList(null);
        if (photographer != null && !photographer.isBlank()) {
            return all.stream().filter(p -> p.equalsIgnoreCase(photographer.strip())).findFirst().map(List::of)
                    .orElseThrow(() -> new IllegalArgumentException("That photographer isn't available to book."));
        }
        return all;
    }

    private List<LocalTime> openStarts(LocalDate day, int minutes, Map<Integer, AvailabilityHours> hoursByDay,
                                       List<TimeBlock> blockList, List<Booking> sameDay, List<String> candidates) {
        int weekday = day.getDayOfWeek().getValue() % 7;
        Map<String, Map<Integer, StaffHours>> own = new HashMap<>();
        // Each candidate inside their own window; a start is offered if any candidate can take it.
        Map<String, Window> windows = new HashMap<>();
        LocalTime earliest = null, latest = null;
        for (String c : candidates) {
            var w = windowFor(c, weekday, hoursByDay, own);
            if (w == null) continue;
            windows.put(c == null ? "" : c, w);
            if (earliest == null || w.start().isBefore(earliest)) earliest = w.start();
            if (latest == null || w.end().isAfter(latest)) latest = w.end();
        }
        if (windows.isEmpty()) return List.of();
        LocalDateTime cutoff = clock.now().plus(NOTICE);
        List<LocalTime> out = new ArrayList<>();
        for (LocalTime t = earliest; ; t = t.plus(STEP)) {
            LocalTime end = t.plusMinutes(minutes);
            if (end.isAfter(latest) || end.isBefore(t) /* wrapped past midnight */) break;
            if (!LocalDateTime.of(day, t).isBefore(cutoff)) {
                final LocalTime s = t, e = end;
                var inWindow = candidates.stream().filter(c -> { var w = windows.get(c == null ? "" : c); return w != null && w.contains(s, e); }).toList();
                if (!inWindow.isEmpty() && freeFor(inWindow, day, t, end, blockList, sameDay, null, true) != null) out.add(t);
            }
            if (t.plus(STEP).isBefore(t)) break;
        }
        return out;
    }

    /**
     * The first candidate who is free for [start, end), or null if none. Candidates are tried in order,
     * so "no preference" lands on the photographer with the fewest sessions that day.
     */
    private String freeFor(List<String> candidates, LocalDate day, LocalTime start, LocalTime end, List<TimeBlock> blockList,
                           List<Booking> sameDay, Long ignoreBookingId, boolean includePending) {
        var photographers = candidates.size() == 1 && candidates.get(0) == null ? List.<String>of() : photographers();
        List<String> ordered = new ArrayList<>(candidates);
        if (ordered.size() > 1) {
            Map<String, Long> load = new HashMap<>();
            for (var b : sameDay) for (String a : b.getAssignees()) for (String p : photographers) if (p.equalsIgnoreCase(a)) load.merge(p, 1L, Long::sum);
            ordered.sort(Comparator.comparingLong(p -> load.getOrDefault(p, 0L)));
        }
        for (String p : ordered) {
            if (clash(p, photographers, day, start, end, blockList, sameDay, ignoreBookingId, includePending).isEmpty()) return p == null ? "" : p;
        }
        return null;
    }

    /** What (if anything) a [start, end) on this day runs into, for one photographer (null = the studio as one). */
    private Optional<String> clash(String photographer, List<String> photographers, LocalDate day, LocalTime start, LocalTime end,
                                   List<TimeBlock> blockList, List<Booking> sameDay, Long ignoreBookingId, boolean includePending) {
        for (var b : blockList) {
            if (!b.appliesTo(photographer)) continue;
            if (b.overlaps(day, start, end)) return Optional.of("time you've blocked off" + (b.getNote() == null ? "" : " (" + b.getNote() + ")"));
        }
        for (var b : sameDay) {
            if (b.getId() != null && b.getId().equals(ignoreBookingId)) continue;
            if (b.getStatus() == BookingStatus.DECLINED) continue;
            if (b.getStatus() == BookingStatus.PENDING && !includePending) continue;
            if (b.getStartTime() == null || b.getEndTime() == null) continue; // legacy row without a time range
            // A booking on another photographer doesn't block this one; an unassigned booking blocks everyone.
            if (!occupies(b, photographer, photographers)) continue;
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
    public record Reservation(LocalTime end, String photographer) {}

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public LocalTime reserve(LocalDate day, LocalTime start, int minutes, Long ignoreBookingId, boolean includePending,
                             boolean enforceHours) {
        return reserve(day, start, minutes, ignoreBookingId, includePending, enforceHours, null).end();
    }

    /**
     * Same, for a photographer ("" or null = no preference; ignored when nobody is bookable). The
     * reservation names who the slot was found for, so the booking can be assigned to them.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Reservation reserve(LocalDate day, LocalTime start, int minutes, Long ignoreBookingId, boolean includePending,
                               boolean enforceHours, String photographer) {
        days.ensure(day);
        days.lock(day);
        LocalTime end = start.plusMinutes(minutes);
        if (!end.isAfter(start)) { // wrapped past midnight (or zero length): never bookable
            throw new ConflictException("That session would run past midnight. Please pick an earlier start.");
        }
        var candidates = candidates(photographer);
        if (enforceHours) {
            // Public requests: inside the hours of whoever takes it, on the slot grid, with notice, and not absurdly far
            // out — exactly the rules the slot list was built with, so a hand-crafted request can't do more than the form.
            var studio = hours.findAll().stream().collect(Collectors.toMap(AvailabilityHours::getWeekday, Function.identity()));
            Map<String, Map<Integer, StaffHours>> own = new HashMap<>();
            int weekday = day.getDayOfWeek().getValue() % 7;
            candidates = candidates.stream().filter(c -> { var w = windowFor(c, weekday, studio, own); return w != null && w.contains(start, end); }).toList();
            if (candidates.isEmpty()) {
                throw new ConflictException("That time isn't within the booking hours. Please pick another.");
            }
            if (start.getMinute() % STEP.toMinutes() != 0 || start.getSecond() != 0) {
                throw new ConflictException("Start times are on the half hour. Please pick a time from the list.");
            }
            if (LocalDateTime.of(day, start).isBefore(clock.now().plus(NOTICE))) {
                throw new ConflictException("That time is too soon — we need at least " + NOTICE.toHours() + " hours' notice. Please pick a later time.");
            }
            if (day.isAfter(clock.today().plusMonths(MAX_AHEAD_MONTHS))) {
                throw new ConflictException("We're only taking bookings up to " + MAX_AHEAD_MONTHS + " months ahead.");
            }
        }
        var blockList = blocks.findAllByEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAscStartTimeAsc(day, day);
        var sameDay = bookings.findAllByDateBetweenAndStatusNot(day, day, BookingStatus.DECLINED);
        String who = freeFor(candidates, day, start, end, blockList, sameDay, ignoreBookingId, includePending);
        if (who == null) {
            String what = clash(candidates.get(0), photographers(), day, start, end, blockList, sameDay, ignoreBookingId, includePending).orElse("another session");
            throw new ConflictException(includePending
                    ? "That time was just taken. Please pick another available time."
                    : "This overlaps " + what + ". Move or decline the other one first.");
        }
        return new Reservation(end, who.isEmpty() ? null : who);
    }
}
