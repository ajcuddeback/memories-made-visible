package com.memoriesmadevisible.booking;

import com.memoriesmadevisible.notification.BookingEmailDrafts;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final BookingService service;
    private final com.memoriesmadevisible.auth.StaffAccess access;

    public AdminBookingController(BookingService service, com.memoriesmadevisible.auth.StaffAccess access) {
        this.service = service;
        this.access = access;
    }

    /** Owner/admin: everything. Employee: only the sessions they're assigned to. */
    @GetMapping
    public List<BookingDtos.Response> list(java.security.Principal me) {
        return access.limited(me) ? service.listAssignedTo(me.getName()) : service.list();
    }

    /** Who's working this session. */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PutMapping("/{id}/assignees")
    public BookingDtos.Response assign(@PathVariable Long id, @Valid @RequestBody BookingDtos.Assign body, java.security.Principal me) {
        return service.assign(id, body.lead(), body.second(), me.getName());
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PatchMapping("/{id}/quote")
    public BookingDtos.Response quote(@PathVariable Long id, @Valid @RequestBody BookingDtos.QuoteUpdate body) {
        return service.setQuote(id, body.quote());
    }

    /** Pre-filled email for an action, so the admin can review/edit before sending. */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
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

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping("/{id}/approve")
    public BookingDtos.Response approve(@PathVariable Long id, @Valid @RequestBody(required = false) BookingDtos.EmailRequest email) {
        return service.setStatus(id, BookingStatus.CONFIRMED, email);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping("/{id}/decline")
    public BookingDtos.Response decline(@PathVariable Long id, @Valid @RequestBody(required = false) BookingDtos.EmailRequest email) {
        return service.setStatus(id, BookingStatus.DECLINED, email);
    }

    /** Pre-filled "please confirm the new time" email for a move the admin is about to make. */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @GetMapping("/{id}/reschedule-draft")
    public BookingDtos.EmailDraft rescheduleDraft(@PathVariable Long id, @RequestParam BookingEmailDrafts.MoveReason reason,
                                                  @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
                                                  @RequestParam String time) {
        return service.rescheduleDraft(id, reason, date, time);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping("/{id}/reschedule")
    public BookingDtos.Response reschedule(@PathVariable Long id, @Valid @RequestBody BookingDtos.Reschedule body) {
        return service.reschedule(id, body);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping("/{id}/restore")
    public BookingDtos.Response restore(@PathVariable Long id) {
        return service.setStatus(id, BookingStatus.PENDING, null);
    }

    /** Send the customer a note without changing anything. */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping("/{id}/message")
    public ResponseEntity<Void> message(@PathVariable Long id, @Valid @RequestBody BookingDtos.EmailRequest email) {
        service.message(id, email);
        return ResponseEntity.accepted().build();
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
