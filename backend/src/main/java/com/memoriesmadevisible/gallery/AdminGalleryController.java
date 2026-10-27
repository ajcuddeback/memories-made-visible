package com.memoriesmadevisible.gallery;

import com.memoriesmadevisible.booking.BookingDtos;
import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.booking.BookingStatus;
import com.memoriesmadevisible.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/galleries")
public class AdminGalleryController {

    private final GalleryRepository galleries;
    private final BookingRepository bookings;
    private final GalleryService service;
    private final com.memoriesmadevisible.auth.StaffAccess access;

    public AdminGalleryController(GalleryRepository galleries, BookingRepository bookings, GalleryService service,
                                  com.memoriesmadevisible.auth.StaffAccess access) {
        this.galleries = galleries;
        this.bookings = bookings;
        this.service = service;
        this.access = access;
    }

    /** Employees may only touch galleries of sessions they're assigned to. */
    private void requireAssigned(Long bookingId, java.security.Principal me) {
        if (!access.limited(me)) return;
        var b = bookings.findById(bookingId).orElse(null);
        if (b == null || !b.isAssignedTo(me.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your session");
        }
    }

    public record CreateRequest(@NotNull Long bookingId) {}
    public record StageUpdate(@NotNull Integer stage) {}

    @GetMapping
    public List<GalleryDtos.Response> list(java.security.Principal me) {
        var all = galleries.findAllByOrderByIdAsc();
        if (access.limited(me)) {
            var mine = new java.util.HashSet<Long>();
            bookings.findAll().stream().filter(b -> b.isAssignedTo(me.getName())).forEach(b -> mine.add(b.getId()));
            all = all.stream().filter(g -> mine.contains(g.getBookingId())).toList();
        }
        return all.stream().map(GalleryDtos::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GalleryDtos.Response create(@RequestBody CreateRequest body, java.security.Principal me) {
        requireAssigned(body.bookingId(), me);
        var booking = bookings.findById(body.bookingId())
                .orElseThrow(() -> new NotFoundException("Booking " + body.bookingId() + " not found"));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only confirmed sessions get a gallery.");
        }
        if (galleries.existsByBookingId(booking.getId())) {
            throw new IllegalArgumentException("That session already has a gallery.");
        }
        var g = new Gallery();
        g.setBookingId(booking.getId());
        return GalleryDtos.toResponse(galleries.save(g));
    }

    @PatchMapping("/{id}/stage")
    public GalleryDtos.Response stage(@PathVariable Long id, @RequestBody StageUpdate body, java.security.Principal me) {
        var g = galleries.findById(id).orElseThrow(() -> new NotFoundException("Gallery " + id + " not found"));
        requireAssigned(g.getBookingId(), me);
        g.setStage(body.stage());
        return GalleryDtos.toResponse(galleries.save(g));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var g = galleries.findById(id).orElseThrow(() -> new NotFoundException("Gallery " + id + " not found"));
        galleries.delete(g);
        return ResponseEntity.noContent().build();
    }

    /** The "photos are ready" email, pre-filled for the admin to edit before delivering. */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @GetMapping("/{id}/draft")
    public BookingDtos.EmailDraft draft(@PathVariable Long id) {
        return service.draft(id);
    }

    /** Mark delivered: record the link + final price and email the client (unless sendEmail=false). */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @PostMapping("/{id}/deliver")
    public GalleryDtos.Delivered deliver(@PathVariable Long id, @Valid @RequestBody GalleryDtos.DeliverRequest body) {
        return service.deliver(id, body);
    }
}
