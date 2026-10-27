package com.memoriesmadevisible.booking;

import com.memoriesmadevisible.availability.AvailabilityDtos;
import com.memoriesmadevisible.availability.AvailabilityService;
import com.memoriesmadevisible.common.NotFoundException;
import com.memoriesmadevisible.notification.BookingEmailDrafts;
import com.memoriesmadevisible.notification.BookingNotificationService;
import com.memoriesmadevisible.config.AppProperties;
import com.memoriesmadevisible.pricing.PricingService;
import com.memoriesmadevisible.publish.PublishService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final PricingService pricing;
    private final BookingNotificationService notifications;
    private final PublishService publish;
    private final AvailabilityService availability;
    private final String siteUrl;

    public BookingService(BookingRepository bookings, PricingService pricing, BookingNotificationService notifications,
                          PublishService publish, AvailabilityService availability, AppProperties props) {
        this.bookings = bookings;
        this.pricing = pricing;
        this.notifications = notifications;
        this.publish = publish;
        this.availability = availability;
        this.siteUrl = props.notifications().siteUrl();
    }

    @Transactional
    public BookingDtos.Response create(BookingDtos.CreateRequest req) {
        // The form shows the published price list, so validate and quote against that, not the draft.
        var type = publish.publishedType(req.sessionType())
                .orElseThrow(() -> new IllegalArgumentException("Unknown session type: " + req.sessionType()));

        var b = new Booking();
        b.setSessionTypeKey(type.key());
        b.setName(req.name().trim());
        b.setEmail(req.email().trim());
        b.setPhone(blankToNull(req.phone()));
        b.setLocation(blankToNull(req.location()));
        b.setNotes(blankToNull(req.notes()));
        b.setDate(req.date());
        // Lock the day, make sure the slot is still open (a pending request holds its slot), then write.
        LocalTime start = AvailabilityDtos.parse(req.time());
        LocalTime end = availability.reserve(req.date(), start, availability.durationFor(type.key()), null, true, true);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setTime(AvailabilityDtos.label(start));
        b.setStatus(BookingStatus.PENDING);
        // Starting quote is the session type's "from" price; the admin tunes it before approving.
        b.setQuote(PricingService.parseAmount(type.from()));
        b = bookings.save(b);

        notifications.notifyNewRequest(b, type.name());
        return toResponse(b);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.Response> list() {
        return bookings.findAllByOrderByDateAscTimeAsc().stream().map(this::toResponse).toList();
    }

    /** What the studio would send for this action, pre-filled, for the admin to edit. */
    @Transactional(readOnly = true)
    public BookingDtos.EmailDraft draft(Long id, BookingEmailDrafts.Action action) {
        var b = find(id);
        var d = BookingEmailDrafts.draft(action, b, typeName(b), siteUrl);
        return new BookingDtos.EmailDraft(d.subject(), d.body(), b.getEmail());
    }

    /**
     * Change status and, when it's a real transition the customer should hear about, email
     * them — using the admin's edited subject/body if given, the default draft otherwise.
     */
    @Transactional
    public BookingDtos.Response setStatus(Long id, BookingStatus status, BookingDtos.EmailRequest email) {
        var b = find(id);
        var previous = b.getStatus();
        if (status == BookingStatus.CONFIRMED && previous != BookingStatus.CONFIRMED && b.getStartTime() != null) {
            // Approving claims the slot for real: it must not overlap a confirmed session or blocked time.
            availability.reserve(b.getDate(), b.getStartTime(), minutes(b), b.getId(), false, false);
        }
        b.setStatus(status);
        b = bookings.save(b);
        var action = previous == status ? null : BookingEmailDrafts.actionFor(previous, status);
        if (action != null && (email == null || email.shouldSend())) {
            String typeName = typeName(b);
            var dflt = BookingEmailDrafts.draft(action, b, typeName, siteUrl);
            String subject = email != null && notBlank(email.subject()) ? email.subject().strip() : dflt.subject();
            String body = email != null && notBlank(email.body()) ? email.body().strip() : dflt.body();
            notifications.sendCustomerEmail(b, typeName, subject, body, action == BookingEmailDrafts.Action.APPROVE);
        }
        return toResponse(b);
    }

    /** A free-form email to the customer about this booking; no status change. */
    @Transactional(readOnly = true)
    public void message(Long id, BookingDtos.EmailRequest email) {
        if (email == null || !notBlank(email.subject()) || !notBlank(email.body())) {
            throw new IllegalArgumentException("A subject and a message are needed.");
        }
        var b = find(id);
        notifications.sendCustomerEmail(b, typeName(b), email.subject().strip(), email.body().strip(),
                b.getStatus() == BookingStatus.CONFIRMED);
    }

    private String typeName(Booking b) {
        return pricing.findType(b.getSessionTypeKey()).map(t -> t.getName()).orElse(b.getSessionTypeKey());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** The "please confirm the new time" email, as it would read for this move, for the admin to edit. */
    @Transactional(readOnly = true)
    public BookingDtos.EmailDraft rescheduleDraft(Long id, BookingEmailDrafts.MoveReason reason, java.time.LocalDate date, String time) {
        var b = find(id);
        // Preview on a detached copy so nothing is written.
        var preview = new Booking();
        preview.setName(b.getName());
        preview.setDate(date);
        preview.setTime(AvailabilityDtos.label(AvailabilityDtos.parse(time)));
        var d = BookingEmailDrafts.reschedule(reason, preview, typeName(b), b.getDate(), b.getTime());
        return new BookingDtos.EmailDraft(d.subject(), d.body(), b.getEmail());
    }

    /**
     * Admin moves a session to a free slot and the customer is asked to confirm the new time
     * (a move is never silent unless the admin explicitly turns the email off).
     */
    @Transactional
    public BookingDtos.Response reschedule(Long id, BookingDtos.Reschedule r) {
        var b = find(id);
        var oldDate = b.getDate();
        var oldTime = b.getTime();
        LocalTime start = AvailabilityDtos.parse(r.time());
        LocalTime end = availability.reserve(r.date(), start, minutes(b), b.getId(),
                b.getStatus() != BookingStatus.DECLINED, false);
        b.setDate(r.date());
        b.setStartTime(start);
        b.setEndTime(end);
        b.setTime(AvailabilityDtos.label(start));
        b = bookings.save(b);
        if (r.shouldSend()) {
            String typeName = typeName(b);
            var dflt = BookingEmailDrafts.reschedule(r.reason(), b, typeName, oldDate, oldTime);
            String subject = notBlank(r.subject()) ? r.subject().strip() : dflt.subject();
            String body = notBlank(r.body()) ? r.body().strip() : dflt.body();
            notifications.sendCustomerEmail(b, typeName, subject, body, b.getStatus() == BookingStatus.CONFIRMED);
        }
        return toResponse(b);
    }

    private int minutes(Booking b) {
        if (b.getStartTime() != null && b.getEndTime() != null && b.getEndTime().isAfter(b.getStartTime())) {
            return (int) java.time.Duration.between(b.getStartTime(), b.getEndTime()).toMinutes();
        }
        return availability.durationFor(b.getSessionTypeKey());
    }

    @Transactional
    public BookingDtos.Response setQuote(Long id, BigDecimal quote) {
        var b = find(id);
        b.setQuote(quote == null ? BigDecimal.ZERO : quote.max(BigDecimal.ZERO));
        return toResponse(bookings.save(b));
    }

    @Transactional
    public void delete(Long id) {
        bookings.delete(find(id));
    }

    private Booking find(Long id) {
        return bookings.findById(id).orElseThrow(() -> new NotFoundException("Booking " + id + " not found"));
    }

    public BookingDtos.Response toResponse(Booking b) {
        String typeName = pricing.findType(b.getSessionTypeKey()).map(t -> t.getName()).orElse(b.getSessionTypeKey());
        return new BookingDtos.Response(b.getId(), b.getSessionTypeKey(), typeName, b.getName(), b.getEmail(),
                b.getPhone(), b.getLocation(), b.getNotes(), b.getDate(), b.getTime(),
                AvailabilityDtos.hm(b.getStartTime()), AvailabilityDtos.hm(b.getEndTime()), b.getStatus(), b.getQuote(),
                b.getCreatedAt());
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
