package com.memoriesmadevisible.notification;

import com.memoriesmadevisible.booking.Booking;
import com.memoriesmadevisible.booking.BookingStatus;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The default wording for every customer email. The admin sees these as an
 * editable draft before anything is sent, so keep them warm and short —
 * they're a starting point, not a contract.
 */
public final class BookingEmailDrafts {

    private BookingEmailDrafts() {}

    public enum Action { APPROVE, DECLINE, CANCEL, MESSAGE, DELIVER }

    public record Draft(String subject, String body) {}

    /** Why a session is being moved; each has its own wording. */
    public enum MoveReason { CLIENT_REQUEST, WEATHER, STUDIO, OTHER }

    /**
     * "Your session has moved — please confirm". The booking already holds the NEW date/time;
     * the old ones are passed in so the email can spell out the change.
     */
    public static Draft reschedule(MoveReason reason, Booking b, String sessionTypeName, java.time.LocalDate oldDate, String oldTime) {
        String first = firstName(b.getName());
        String was = LONG_DATE.format(oldDate) + " at " + oldTime;
        String now = LONG_DATE.format(b.getDate()) + " at " + b.getTime();
        String subject = "New time for your " + sessionTypeName + " session — please confirm";
        String opening = switch (reason) {
            case CLIENT_REQUEST -> "As you asked, we've moved your %s session from %s to %s.".formatted(sessionTypeName, was, now);
            case WEATHER -> "The forecast for %s isn't looking kind to photos, so we'd like to move your %s session to %s. Golden light is worth the wait!".formatted(was, sessionTypeName, now);
            case STUDIO -> "We're so sorry — something has come up on our end and we can't make %s. We've pencilled your %s session in for %s instead.".formatted(was, sessionTypeName, now);
            case OTHER -> "We'd like to move your %s session from %s to %s.".formatted(sessionTypeName, was, now);
        };
        String body = """
                Hi %s,

                %s

                Please reply to this email to confirm the new time works for you — if it doesn't, tell us what does and we'll find something that suits.

                Erica & Austin
                """.formatted(first, opening).strip();
        return new Draft(subject, body);
    }

    public static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US);
    static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMMM d", Locale.US);

    public static Action actionFor(BookingStatus from, BookingStatus to) {
        if (to == BookingStatus.CONFIRMED) return Action.APPROVE;
        if (to == BookingStatus.DECLINED) return from == BookingStatus.CONFIRMED ? Action.CANCEL : Action.DECLINE;
        return null; // back to pending: nothing to say
    }

    public static Draft draft(Action action, Booking b, String sessionTypeName, String siteUrl) {
        String first = firstName(b.getName());
        String date = LONG_DATE.format(b.getDate());
        String when = date + " at " + b.getTime();
        String site = siteUrl == null ? "" : siteUrl.replaceAll("/+$", "");
        return switch (action) {
            case APPROVE -> new Draft(
                    "Your " + sessionTypeName + " session is confirmed — " + SHORT_DATE.format(b.getDate()),
                    """
                    Hi %s,

                    Great news — your %s session is confirmed for %s%s. We can't wait.

                    We'll reach out before the day to talk through location, timing and what to wear. In the meantime, just reply to this email with any questions.

                    Erica & Austin
                    """.formatted(first, sessionTypeName, when, b.getLocation() == null ? "" : " in " + b.getLocation()).strip());
            case DECLINE -> new Draft(
                    "About your " + sessionTypeName + " session request",
                    """
                    Hi %s,

                    Thank you so much for thinking of us for your %s session. Unfortunately we're not able to take %s.

                    We'd still love to photograph you — reply with a couple of alternative dates, or send a new request at %s/book, and we'll find a time that works.

                    Erica & Austin
                    """.formatted(first, sessionTypeName, date, site).strip());
            case CANCEL -> new Draft(
                    "Your " + sessionTypeName + " session on " + SHORT_DATE.format(b.getDate()) + " has been cancelled",
                    """
                    Hi %s,

                    Your %s session on %s has been cancelled. We're sorry for the change of plans.

                    If you'd like to pick a new date, reply to this email or send another request at %s/book and we'll sort it out together.

                    Erica & Austin
                    """.formatted(first, sessionTypeName, when, site).strip());
            case DELIVER -> new Draft(
                    "Your " + sessionTypeName + " photos are ready!",
                    """
                    Hi %s,

                    Your photos from your %s session on %s are ready — and we loved every minute of it. Tap the button below to view and download your gallery.

                    Feel free to share the link with family and friends. If you'd like prints, or have any questions at all, just reply to this email.

                    Erica & Austin
                    """.formatted(first, sessionTypeName, date).strip());
            case MESSAGE -> new Draft(
                    "A note about your " + sessionTypeName + " session",
                    """
                    Hi %s,



                    Erica & Austin
                    """.formatted(first).stripTrailing());
        };
    }

    /** Details card rows for an email about this booking. */
    public static List<EmailTemplates.Detail> details(Booking b, String sessionTypeName, boolean includePrice) {
        List<EmailTemplates.Detail> d = new ArrayList<>();
        d.add(new EmailTemplates.Detail("Session", b.getPackageName() == null ? sessionTypeName : sessionTypeName + " · " + b.packageSummary()));
        d.add(new EmailTemplates.Detail("Date", LONG_DATE.format(b.getDate())));
        d.add(new EmailTemplates.Detail("Time", b.getTime()));
        if (b.getLead() != null) d.add(new EmailTemplates.Detail("Photographer", b.getLead()));
        if (b.getSecond() != null) d.add(new EmailTemplates.Detail("Second photographer", b.getSecond()));
        if (b.getLocation() != null && !b.getLocation().isBlank()) d.add(new EmailTemplates.Detail("Location", b.getLocation()));
        if (includePrice && b.getQuote() != null && b.getQuote().signum() > 0) {
            d.add(new EmailTemplates.Detail("Session price", money(b.getQuote())));
        }
        return d;
    }

    public static String money(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(v);
    }

    static String firstName(String name) {
        if (name == null || name.isBlank()) return "there";
        String n = name.strip();
        // "Taylor & Jordan Reyes" → "Taylor & Jordan"; "Morgan Ellis" → "Morgan"
        int amp = n.indexOf('&');
        if (amp > 0) {
            String[] parts = n.split("\\s+");
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                out.append(i > 0 ? " " : "").append(parts[i]);
                if (i > 0 && parts[i - 1].equals("&")) return out.toString();
            }
            return out.toString();
        }
        return n.split("\\s+")[0];
    }
}
