package com.memoriesmadevisible.gallery;

import com.memoriesmadevisible.booking.BookingDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class GalleryDtos {

    private GalleryDtos() {}

    public record Response(Long id, Long bookingId, int stage, String link, Instant deliveredAt) {}

    /** Mark delivered. {@code finalPrice} null keeps the quote; {@code sendEmail=false} skips the email. */
    public record DeliverRequest(@NotBlank @Size(max = 500) String link, BigDecimal finalPrice, Boolean sendEmail,
                                 @Size(max = 200) String subject, @Size(max = 8000) String body) {
        public boolean shouldSend() { return sendEmail == null || sendEmail; }
    }

    /** The gallery and the booking (its quote may have changed) so the admin updates both at once. */
    public record Delivered(Response gallery, BookingDtos.Response booking) {}

    static Response toResponse(Gallery g) {
        return new Response(g.getId(), g.getBookingId(), g.getStage(), g.getLink(), g.getDeliveredAt());
    }
}
