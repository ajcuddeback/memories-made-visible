package com.memoriesmadevisible.booking;

import com.memoriesmadevisible.notification.BookingEmailDrafts;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {}

    /** Public booking form. {@code website} is a honeypot: humans never see it, bots fill it. */
    public record CreateRequest(
            @NotBlank @Size(max = 40) String sessionType,
            /** Package (tier) name within the session type; decides the length and starting quote. */
            @Size(max = 80) String packageName,
            /** For hourly packages: how many hours (within the package's range). */
            Integer hours,
            /** Add-on names from the package's list. */
            List<@Size(max = 80) String> addons,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 200) String email,
            @Size(max = 40) String phone,
            @Size(max = 200) String location,
            @Size(max = 4000) String notes,
            @NotNull LocalDate date,
            /** Start time as "HH:mm", one of the slots /api/availability offered. */
            @NotBlank @Pattern(regexp = "^\\d{1,2}:\\d{2}$", message = "Pick a time from the list.") String time,
            @Size(max = 200) String website
    ) {}

    public record Response(
            Long id, String sessionType, String sessionTypeName, String packageName, Integer hours, List<String> addons,
            String name, String email, String phone,
            String location, String notes, LocalDate date, String time, String startTime, String endTime,
            BookingStatus status, BigDecimal quote, Instant createdAt
    ) {}

    /**
     * Admin: move a booking to another slot. The customer gets a "please confirm" email built
     * from {@code reason}'s template unless the admin edits it or sets {@code sendEmail=false}.
     */
    public record Reschedule(@NotNull LocalDate date, @NotBlank @Pattern(regexp = "^\\d{1,2}:\\d{2}$") String time,
                             @NotNull BookingEmailDrafts.MoveReason reason, Boolean sendEmail,
                             @Size(max = 200) String subject, @Size(max = 8000) String body) {
        public boolean shouldSend() { return sendEmail == null || sendEmail; }
    }

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
