package com.memoriesmadevisible.gallery;

import com.memoriesmadevisible.booking.Booking;
import com.memoriesmadevisible.booking.BookingDtos;
import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.booking.BookingService;
import com.memoriesmadevisible.common.NotFoundException;
import com.memoriesmadevisible.config.AppProperties;
import com.memoriesmadevisible.notification.BookingEmailDrafts;
import com.memoriesmadevisible.notification.BookingNotificationService;
import com.memoriesmadevisible.pricing.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/** Delivering a finished gallery: record the link and final price, and email the client. */
@Service
public class GalleryService {

    private final com.memoriesmadevisible.common.StudioMetrics metrics;

    private final GalleryRepository galleries;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final PricingService pricing;
    private final BookingNotificationService notifications;
    private final String siteUrl;

    public GalleryService(GalleryRepository galleries, BookingRepository bookings, BookingService bookingService,
                          PricingService pricing, BookingNotificationService notifications, AppProperties props,
                          com.memoriesmadevisible.common.StudioMetrics metrics) {
        this.metrics = metrics;
        this.galleries = galleries;
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.pricing = pricing;
        this.notifications = notifications;
        this.siteUrl = props.notifications().siteUrl();
    }

    /** The "your photos are ready" email, pre-filled for the admin to review. */
    @Transactional(readOnly = true)
    public BookingDtos.EmailDraft draft(Long galleryId) {
        var g = find(galleryId);
        var b = booking(g);
        var d = BookingEmailDrafts.draft(BookingEmailDrafts.Action.DELIVER, b, typeName(b), siteUrl);
        return new BookingDtos.EmailDraft(d.subject(), d.body(), b.getEmail());
    }

    /**
     * Mark delivered: store the gallery link, update the booking to the final price (what the
     * client actually paid, so revenue is right) and email the link unless told not to.
     */
    @Transactional
    public GalleryDtos.Delivered deliver(Long galleryId, GalleryDtos.DeliverRequest req) {
        var g = find(galleryId);
        var b = booking(g);
        String link = req.link() == null ? "" : req.link().strip();
        if (!(link.startsWith("https://") || link.startsWith("http://"))) {
            throw new IllegalArgumentException("The gallery link needs to be a full web address starting with https://");
        }
        g.setLink(link);
        g.setStage(Gallery.MAX_STAGE);
        g.setDeliveredAt(Instant.now());
        g = galleries.save(g);
        metrics.galleryDelivered();

        if (req.finalPrice() != null) {
            b.setQuote(req.finalPrice().max(BigDecimal.ZERO));
            b = bookings.save(b);
        }

        if (req.shouldSend()) {
            String typeName = typeName(b);
            var dflt = BookingEmailDrafts.draft(BookingEmailDrafts.Action.DELIVER, b, typeName, siteUrl);
            String subject = notBlank(req.subject()) ? req.subject().strip() : dflt.subject();
            String body = notBlank(req.body()) ? req.body().strip() : dflt.body();
            notifications.sendCustomerEmail(b, typeName, subject, body, false, "View your photos", link);
        }
        return new GalleryDtos.Delivered(GalleryDtos.toResponse(g), bookingService.toResponse(b));
    }

    private Gallery find(Long id) {
        return galleries.findById(id).orElseThrow(() -> new NotFoundException("Gallery " + id + " not found"));
    }

    private Booking booking(Gallery g) {
        return bookings.findById(g.getBookingId())
                .orElseThrow(() -> new NotFoundException("The session for this gallery no longer exists."));
    }

    private String typeName(Booking b) {
        return pricing.findType(b.getSessionTypeKey()).map(t -> t.getName()).orElse(b.getSessionTypeKey());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
