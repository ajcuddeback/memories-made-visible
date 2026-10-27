package com.memoriesmadevisible.booking;

import com.memoriesmadevisible.common.NotFoundException;
import com.memoriesmadevisible.notification.BookingEmailDrafts;
import com.memoriesmadevisible.notification.BookingNotificationService;
import com.memoriesmadevisible.config.AppProperties;
import com.memoriesmadevisible.pricing.PricingService;
import com.memoriesmadevisible.publish.PublishService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final PricingService pricing;
    private final BookingNotificationService notifications;
    private final PublishService publish;
    private final String siteUrl;

    public BookingService(BookingRepository bookings, PricingService pricing, BookingNotificationService notifications,
                          PublishService publish, AppProperties props) {
        this.bookings = bookings;
        this.pricing = pricing;
        this.notifications = notifications;
        this.publish = publish;
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
        b.setTime(req.time().trim());
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
                b.getPhone(), b.getLocation(), b.getNotes(), b.getDate(), b.getTime(), b.getStatus(), b.getQuote(),
                b.getCreatedAt());
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
