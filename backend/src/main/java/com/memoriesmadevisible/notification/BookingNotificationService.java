package com.memoriesmadevisible.notification;

import com.memoriesmadevisible.booking.Booking;
import com.memoriesmadevisible.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
        String sender = cfg.customerFrom() == null || cfg.customerFrom().isBlank() ? cfg.from() : cfg.customerFrom();
        msg.setFrom(sender);
        msg.setReplyTo(sender);
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
        String site = cfg.siteUrl() == null ? "" : cfg.siteUrl().replaceAll("/+$", "");
        return site + "/studio";
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
