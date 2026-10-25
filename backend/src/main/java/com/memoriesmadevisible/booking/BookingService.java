package com.memoriesmadevisible.booking;

import com.memoriesmadevisible.common.NotFoundException;
import com.memoriesmadevisible.notification.BookingNotificationService;
import com.memoriesmadevisible.pricing.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final PricingService pricing;
    private final BookingNotificationService notifications;

    public BookingService(BookingRepository bookings, PricingService pricing, BookingNotificationService notifications) {
        this.bookings = bookings;
        this.pricing = pricing;
        this.notifications = notifications;
    }

    @Transactional
    public BookingDtos.Response create(BookingDtos.CreateRequest req) {
        var type = pricing.findType(req.sessionType())
                .orElseThrow(() -> new IllegalArgumentException("Unknown session type: " + req.sessionType()));

        var b = new Booking();
        b.setSessionTypeKey(type.getKey());
        b.setName(req.name().trim());
        b.setEmail(req.email().trim());
        b.setPhone(blankToNull(req.phone()));
        b.setLocation(blankToNull(req.location()));
        b.setNotes(blankToNull(req.notes()));
        b.setDate(req.date());
        b.setTime(req.time().trim());
        b.setStatus(BookingStatus.PENDING);
        // Starting quote is the session type's "from" price; the admin tunes it before approving.
        b.setQuote(PricingService.parseAmount(type.getFromPrice()));
        b = bookings.save(b);

        notifications.notifyNewRequest(b, type.getName());
        return toResponse(b);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.Response> list() {
        return bookings.findAllByOrderByDateAscTimeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public BookingDtos.Response setStatus(Long id, BookingStatus status) {
        var b = find(id);
        b.setStatus(status);
        return toResponse(bookings.save(b));
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
