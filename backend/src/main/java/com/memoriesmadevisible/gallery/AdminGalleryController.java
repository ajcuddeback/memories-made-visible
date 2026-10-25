package com.memoriesmadevisible.gallery;

import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.booking.BookingStatus;
import com.memoriesmadevisible.common.NotFoundException;
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

    public AdminGalleryController(GalleryRepository galleries, BookingRepository bookings) {
        this.galleries = galleries;
        this.bookings = bookings;
    }

    public record Response(Long id, Long bookingId, int stage) {}
    public record CreateRequest(@NotNull Long bookingId) {}
    public record StageUpdate(@NotNull Integer stage) {}

    @GetMapping
    public List<Response> list() {
        return galleries.findAllByOrderByIdAsc().stream().map(AdminGalleryController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@RequestBody CreateRequest body) {
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
        return toResponse(galleries.save(g));
    }

    @PatchMapping("/{id}/stage")
    public Response stage(@PathVariable Long id, @RequestBody StageUpdate body) {
        var g = galleries.findById(id).orElseThrow(() -> new NotFoundException("Gallery " + id + " not found"));
        g.setStage(body.stage());
        return toResponse(galleries.save(g));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        var g = galleries.findById(id).orElseThrow(() -> new NotFoundException("Gallery " + id + " not found"));
        galleries.delete(g);
        return ResponseEntity.noContent().build();
    }

    private static Response toResponse(Gallery g) {
        return new Response(g.getId(), g.getBookingId(), g.getStage());
    }
}
