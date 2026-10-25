package com.memoriesmadevisible.booking;

import com.memoriesmadevisible.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/** Public: the "Book a session" form. */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;
    private final RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(15));

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody BookingDtos.CreateRequest req, HttpServletRequest http) {
        if (req.website() != null && !req.website().isBlank()) {
            // Honeypot tripped: pretend it worked so the bot moves on, but store nothing.
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true));
        }
        if (!limiter.tryAcquire(RateLimiter.clientKey(http))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many requests from this connection. Please try again later."));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }
}
