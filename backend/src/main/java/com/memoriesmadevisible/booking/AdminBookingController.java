package com.memoriesmadevisible.booking;

import com.memoriesmadevisible.notification.BookingEmailDrafts;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final BookingService service;

    public AdminBookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping
    public List<BookingDtos.Response> list() {
        return service.list();
    }

    @PatchMapping("/{id}/quote")
    public BookingDtos.Response quote(@PathVariable Long id, @Valid @RequestBody BookingDtos.QuoteUpdate body) {
        return service.setQuote(id, body.quote());
    }

    /** Pre-filled email for an action, so the admin can review/edit before sending. */
    @GetMapping("/{id}/draft")
    public BookingDtos.EmailDraft draft(@PathVariable Long id, @RequestParam("action") String action) {
        BookingEmailDrafts.Action a;
        try {
            a = BookingEmailDrafts.Action.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown action: " + action);
        }
        if (a == BookingEmailDrafts.Action.DELIVER) throw new IllegalArgumentException("Deliver from the gallery, not the booking.");
        return service.draft(id, a);
    }

    @PostMapping("/{id}/approve")
    public BookingDtos.Response approve(@PathVariable Long id, @Valid @RequestBody(required = false) BookingDtos.EmailRequest email) {
        return service.setStatus(id, BookingStatus.CONFIRMED, email);
    }

    @PostMapping("/{id}/decline")
    public BookingDtos.Response decline(@PathVariable Long id, @Valid @RequestBody(required = false) BookingDtos.EmailRequest email) {
        return service.setStatus(id, BookingStatus.DECLINED, email);
    }

    @PostMapping("/{id}/reschedule")
    public BookingDtos.Response reschedule(@PathVariable Long id, @Valid @RequestBody BookingDtos.Reschedule body) {
        return service.reschedule(id, body);
    }

    @PostMapping("/{id}/restore")
    public BookingDtos.Response restore(@PathVariable Long id) {
        return service.setStatus(id, BookingStatus.PENDING, null);
    }

    /** Send the customer a note without changing anything. */
    @PostMapping("/{id}/message")
    public ResponseEntity<Void> message(@PathVariable Long id, @Valid @RequestBody BookingDtos.EmailRequest email) {
        service.message(id, email);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
