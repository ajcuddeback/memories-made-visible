package com.memoriesmadevisible.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class BookingDtos {

    private BookingDtos() {}

    /** Public booking form. {@code website} is a honeypot: humans never see it, bots fill it. */
    public record CreateRequest(
            @NotBlank @Size(max = 40) String sessionType,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 200) String email,
            @Size(max = 40) String phone,
            @Size(max = 200) String location,
            @Size(max = 4000) String notes,
            @NotNull @FutureOrPresent LocalDate date,
            @NotBlank @Size(max = 40) String time,
            @Size(max = 200) String website
    ) {}

    public record Response(
            Long id, String sessionType, String sessionTypeName, String name, String email, String phone,
            String location, String notes, LocalDate date, String time, BookingStatus status,
            BigDecimal quote, Instant createdAt
    ) {}

    public record QuoteUpdate(@NotNull BigDecimal quote) {}

    /**
     * Optional body for approve/decline/restore and the free-form message endpoint. The
     * admin edits the drafted email in the studio; {@code sendEmail=false} skips it.
     */
    public record EmailRequest(Boolean sendEmail, @Size(max = 200) String subject, @Size(max = 8000) String body) {
        public boolean shouldSend() { return sendEmail == null || sendEmail; }
    }

    public record EmailDraft(String subject, String body, String to) {}
}
