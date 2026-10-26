package com.memoriesmadevisible.notification;

import com.memoriesmadevisible.booking.Booking;
import com.memoriesmadevisible.booking.BookingStatus;
import com.memoriesmadevisible.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Every new booking request emails the studio inbox ({@code app.notifications.to}).
 * Sends run async so a slow SMTP server never delays the customer's confirmation screen.
 */
@Service
public class BookingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US);

    private final JavaMailSender mail;
    private final AppProperties.Notifications cfg;

    public BookingNotificationService(JavaMailSender mail, AppProperties props) {
        this.mail = mail;
        this.cfg = props.notifications();
    }

    @Async
    public void notifyNewRequest(Booking b, String sessionTypeName) {
        if (cfg.to() == null || cfg.to().isBlank()) {
            log.warn("New booking #{} from {} but app.notifications.to is not configured — no email sent.",
                    b.getId(), b.getEmail());
            return;
        }
        try {
            mail.send(studioMessage(b, sessionTypeName));
            log.info("Booking #{} notification sent to {}", b.getId(), cfg.to());
        } catch (Exception e) {
            log.error("Failed to send booking notification for #{}", b.getId(), e);
        }
        if (cfg.sendCustomerConfirmation()) {
            try {
                mail.send(customerMessage(b, sessionTypeName));
            } catch (Exception e) {
                log.error("Failed to send customer confirmation for #{}", b.getId(), e);
            }
        }
    }

    /**
     * Admin approved, declined or cancelled a request: tell the customer, and BCC the
     * studio inbox so there's a record of what was sent. Restoring to pending sends nothing.
     */
    @Async
    public void notifyStatusChange(Booking b, String sessionTypeName, BookingStatus previous) {
        SimpleMailMessage msg = statusMessage(b, sessionTypeName, previous);
        if (msg == null) return;
        try {
            mail.send(msg);
            log.info("Booking #{} {} email sent to {}", b.getId(), b.getStatus(), b.getEmail());
        } catch (Exception e) {
            log.error("Failed to send {} email for booking #{}", b.getStatus(), b.getId(), e);
        }
    }

    SimpleMailMessage statusMessage(Booking b, String sessionTypeName, BookingStatus previous) {
        BookingStatus now = b.getStatus();
        if (now == previous || now == BookingStatus.PENDING) return null;

        var msg = customerEnvelope(b);
        if (cfg.to() != null && !cfg.to().isBlank()) {
            msg.setBcc(cfg.to().split("\\s*,\\s*")[0]);
        }
        String when = DATE.format(b.getDate()) + " at " + b.getTime();
        if (now == BookingStatus.CONFIRMED) {
            msg.setSubject("Your " + sessionTypeName + " session is confirmed — " + DATE.format(b.getDate()));
            msg.setText("""
                    Hi %s,

                    Great news — your %s session is confirmed for %s%s.
                    %s
                    Reply to this email with any questions, and we'll be in touch before the day to plan the details.

                    Erica & Austin
                    Memories Made Visible Photography
                    Winter Haven, FL · Serving all of Polk County
                    """.formatted(b.getName(), sessionTypeName, when,
                    b.getLocation() == null ? "" : " in " + b.getLocation(),
                    quoteLine(b.getQuote())));
        } else if (previous == BookingStatus.CONFIRMED) {
            msg.setSubject("Your " + sessionTypeName + " session on " + DATE.format(b.getDate()) + " has been cancelled");
            msg.setText("""
                    Hi %s,

                    Your %s session on %s has been cancelled. We're sorry for the change of plans.

                    If you'd like to pick a new date, you can send another request any time at %s/book,
                    or just reply to this email and we'll sort it out together.

                    Erica & Austin
                    Memories Made Visible Photography
                    """.formatted(b.getName(), sessionTypeName, when, siteUrl()));
        } else {
            msg.setSubject("About your " + sessionTypeName + " session request");
            msg.setText("""
                    Hi %s,

                    Thank you for your request for a %s session on %s. Unfortunately we're not able to
                    take that date.

                    We'd still love to photograph you — reply to this email with a couple of alternative
                    dates, or send a new request at %s/book, and we'll find a time that works.

                    Erica & Austin
                    Memories Made Visible Photography
                    """.formatted(b.getName(), sessionTypeName, when, siteUrl()));
        }
        return msg;
    }

    private SimpleMailMessage customerEnvelope(Booking b) {
        var msg = new SimpleMailMessage();
        String sender = cfg.customerFrom() == null || cfg.customerFrom().isBlank() ? cfg.from() : cfg.customerFrom();
        msg.setFrom(sender);
        msg.setReplyTo(cfg.to() == null || cfg.to().isBlank() ? sender : cfg.to().split("\\s*,\\s*")[0]);
        msg.setTo(b.getEmail());
        return msg;
    }

    private static String quoteLine(BigDecimal quote) {
        if (quote == null || quote.signum() <= 0) return "";
        return "Session price: " + NumberFormat.getCurrencyInstance(Locale.US).format(quote) + "\n";
    }

    private String siteUrl() {
        return cfg.siteUrl() == null ? "" : cfg.siteUrl().replaceAll("/+$", "");
    }

    SimpleMailMessage studioMessage(Booking b, String sessionTypeName) {
        var msg = new SimpleMailMessage();
        msg.setFrom(cfg.from());
        msg.setTo(cfg.to().split("\\s*,\\s*"));
        msg.setReplyTo(b.getEmail());
        msg.setSubject("New session request: " + sessionTypeName + " · " + b.getName());
        msg.setText("""
                A new session request just came in from the website.

                Session:   %s
                Date:      %s
                Time:      %s
                Name:      %s
                Email:     %s
                Phone:     %s
                Location:  %s

                Notes:
                %s

                Review it in the studio admin: %s
                """.formatted(
                sessionTypeName,
                DATE.format(b.getDate()),
                b.getTime(),
                b.getName(),
                b.getEmail(),
                orDash(b.getPhone()),
                orDash(b.getLocation()),
                orDash(b.getNotes()),
                adminUrl()));
        return msg;
    }

    SimpleMailMessage customerMessage(Booking b, String sessionTypeName) {
        var msg = new SimpleMailMessage();
        // Most mailbox providers (Namecheap Private Email included) only let the authenticated
        // mailbox send as itself, so the From stays on the SMTP account unless customerFrom is set
        // to an alias of it; replies still go to the bookings inbox.
        String sender = cfg.customerFrom() == null || cfg.customerFrom().isBlank() ? cfg.from() : cfg.customerFrom();
        msg.setFrom(sender);
        msg.setReplyTo(cfg.to() == null || cfg.to().isBlank() ? sender : cfg.to().split("\\s*,\\s*")[0]);
        msg.setTo(b.getEmail());
        msg.setSubject("We received your session request — Memories Made Visible");
        msg.setText("""
                Hi %s,

                Thank you for your request for a %s session on %s at %s.
                This isn't a confirmed booking yet — Erica will reach out shortly to confirm the date and plan the details.

                Memories Made Visible Photography
                Serving Polk County, Florida
                """.formatted(b.getName(), sessionTypeName, DATE.format(b.getDate()), b.getTime()));
        return msg;
    }

    private String adminUrl() {
        return siteUrl() + "/studio";
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
