package com.memoriesmadevisible.booking;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/{id}/approve")
    public BookingDtos.Response approve(@PathVariable Long id) {
        return service.setStatus(id, BookingStatus.CONFIRMED);
    }

    @PostMapping("/{id}/decline")
    public BookingDtos.Response decline(@PathVariable Long id) {
        return service.setStatus(id, BookingStatus.DECLINED);
    }

    @PostMapping("/{id}/restore")
    public BookingDtos.Response restore(@PathVariable Long id) {
        return service.setStatus(id, BookingStatus.PENDING);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
