package com.memoriesmadevisible.notification;

import com.memoriesmadevisible.booking.Booking;
import com.memoriesmadevisible.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * All outbound email. Every message is branded HTML with a plain-text
 * alternative. Sends run async so a slow SMTP server never delays a response.
 */
@Service
public class BookingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationService.class);

    private final JavaMailSender mail;
    private final AppProperties.Notifications cfg;

    public BookingNotificationService(JavaMailSender mail, AppProperties props) {
        this.mail = mail;
        this.cfg = props.notifications();
    }

    /** New request from the website: studio gets the details; customer optionally gets an acknowledgement. */
    @Async
    public void notifyNewRequest(Booking b, String sessionTypeName) {
        if (cfg.to() == null || cfg.to().isBlank()) {
            log.warn("New booking #{} from {} but app.notifications.to is not configured — no email sent.",
                    b.getId(), b.getEmail());
            return;
        }
        try {
            var r = EmailTemplates.render(siteUrl(),
                    "New session request",
                    """
                    %s just requested a %s session through the website.

                    Review it in the studio admin to set a quote and approve or decline — the customer will get an email either way.
                    """.formatted(b.getName(), sessionTypeName).strip(),
                    List.of(new EmailTemplates.Detail("Session", sessionTypeName),
                            new EmailTemplates.Detail("Date", BookingEmailDrafts.LONG_DATE.format(b.getDate())),
                            new EmailTemplates.Detail("Time", b.getTime()),
                            new EmailTemplates.Detail("Location", b.getLocation()),
                            new EmailTemplates.Detail("Name", b.getName()),
                            new EmailTemplates.Detail("Email", b.getEmail()),
                            new EmailTemplates.Detail("Phone", b.getPhone()),
                            new EmailTemplates.Detail("Starting quote", b.getQuote() == null ? null : BookingEmailDrafts.money(b.getQuote())),
                            new EmailTemplates.Detail("Notes", b.getNotes())),
                    "Open in studio admin", siteUrl() + "/studio/requests",
                    "Replying to this email goes straight to " + b.getEmail() + ".");
            send(cfg.from(), studioInbox(), b.getEmail(), null,
                    "New session request: " + sessionTypeName + " · " + b.getName(), r);
            log.info("Booking #{} notification sent to {}", b.getId(), cfg.to());
        } catch (Exception e) {
            log.error("Failed to send booking notification for #{}", b.getId(), e);
        }

        if (cfg.sendCustomerConfirmation()) {
            try {
                var r = EmailTemplates.render(siteUrl(),
                        "We've got your request",
                        """
                        Hi %s,

                        Thank you for your request for a %s session. This isn't a confirmed booking just yet — Erica will be in touch shortly to confirm the date and plan the details with you.
                        """.formatted(BookingEmailDrafts.firstName(b.getName()), sessionTypeName).strip(),
                        BookingEmailDrafts.details(b, sessionTypeName, false),
                        null, null,
                        "Reply to this email any time if you have questions.");
                send(customerSender(), new String[]{b.getEmail()}, studioInbox()[0], null,
                        "We received your session request — Memories Made Visible", r);
            } catch (Exception e) {
                log.error("Failed to send customer acknowledgement for #{}", b.getId(), e);
            }
        }
    }

    /**
     * An email the admin reviewed in the studio (approval, decline, cancellation, or a free-form
     * note). The studio inbox is BCC'd so there's a record of what was sent.
     */
    @Async
    public void sendCustomerEmail(Booking b, String sessionTypeName, String subject, String body, boolean includePrice) {
        sendCustomerEmail(b, sessionTypeName, subject, body, includePrice, null, null);
    }

    /** Same, with a button (e.g. "View your photos" → the gallery link). */
    @Async
    public void sendCustomerEmail(Booking b, String sessionTypeName, String subject, String body, boolean includePrice,
                                  String ctaLabel, String ctaUrl) {
        try {
            var r = EmailTemplates.render(siteUrl(), subject, body,
                    BookingEmailDrafts.details(b, sessionTypeName, includePrice),
                    ctaLabel, ctaUrl, ctaUrl == null ? null : "If the button doesn't work, copy this link: " + ctaUrl);
            send(customerSender(), new String[]{b.getEmail()}, studioInbox()[0], studioInbox()[0], subject, r);
            log.info("Email \"{}\" sent to {} for booking #{}", subject, b.getEmail(), b.getId());
        } catch (Exception e) {
            log.error("Failed to send email \"{}\" for booking #{}", subject, b.getId(), e);
        }
    }

    private void send(String from, String[] to, String replyTo, String bcc, String subject, EmailTemplates.Rendered r)
            throws MessagingException, java.io.UnsupportedEncodingException {
        MimeMessage msg = mail.createMimeMessage();
        var h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
        h.setFrom(from, "Memories Made Visible");
        h.setTo(to);
        if (replyTo != null && !replyTo.isBlank()) h.setReplyTo(replyTo);
        if (bcc != null && !bcc.isBlank()) h.setBcc(bcc);
        h.setSubject(subject);
        h.setText(r.text(), r.html());
        mail.send(msg);
    }

    private String customerSender() {
        return cfg.customerFrom() == null || cfg.customerFrom().isBlank() ? cfg.from() : cfg.customerFrom();
    }

    private String[] studioInbox() {
        if (cfg.to() == null || cfg.to().isBlank()) return new String[]{customerSender()};
        return cfg.to().split("\\s*,\\s*");
    }

    private String siteUrl() {
        return cfg.siteUrl() == null ? "" : cfg.siteUrl().replaceAll("/+$", "");
    }
}
