package com.memoriesmadevisible.common;

import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.booking.BookingStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The studio's business metrics, published with the JVM/HTTP/DB ones at /actuator/prometheus. Every name starts
 * with {@code mmv_} so they're easy to find in Grafana. Counters are "since the app started"; Prometheus turns them
 * into rates. The two gauges are read from the database on each scrape (two cheap COUNT queries).
 */
@Component
public class StudioMetrics {

    private final MeterRegistry registry;

    public StudioMetrics(MeterRegistry registry, BookingRepository bookings, StudioClock clock) {
        this.registry = registry;
        Gauge.builder("mmv.bookings.pending", bookings, r -> r.countByStatus(BookingStatus.PENDING))
                .description("Session requests waiting for a decision").register(registry);
        Gauge.builder("mmv.bookings.upcoming", bookings,
                        r -> r.countByStatusAndDateGreaterThanEqual(BookingStatus.CONFIRMED, clock.today()))
                .description("Confirmed sessions from today onwards").register(registry);
    }

    /** A new request came in from the website. */
    public void bookingRequested() {
        counter("mmv.booking.requests", "Session requests submitted from the website").increment();
    }

    /** A request moved to another status (confirmed, declined, back to pending). */
    public void bookingStatus(BookingStatus to) {
        Counter.builder("mmv.booking.status.changes").tag("to", to.name().toLowerCase(Locale.ROOT))
                .description("Status changes made in the studio").register(registry).increment();
    }

    public void bookingRescheduled() {
        counter("mmv.booking.reschedules", "Sessions moved to another date or time").increment();
    }

    public void galleryDelivered() {
        counter("mmv.gallery.deliveries", "Galleries delivered to clients").increment();
    }

    /** An email attempt: kind is studio (new-request alert), customer, or staff (invites, resets). */
    public void email(String kind, boolean sent) {
        Counter.builder("mmv.emails").tag("kind", kind).tag("outcome", sent ? "sent" : "failed")
                .description("Emails the API tried to send").register(registry).increment();
    }

    public void login(boolean ok) {
        Counter.builder("mmv.logins").tag("outcome", ok ? "ok" : "failed")
                .description("Studio sign-in attempts").register(registry).increment();
    }

    /** A request was refused by one of the per-address limiters: booking, login, forgot, analytics. */
    public void rateLimited(String limiter) {
        Counter.builder("mmv.rate.limited").tag("limiter", limiter)
                .description("Requests refused by a rate limiter").register(registry).increment();
    }

    public void sitePublished() {
        counter("mmv.site.publishes", "Times the draft site went live").increment();
    }

    public void siteDiscarded() {
        counter("mmv.site.discards", "Times unpublished changes were thrown away").increment();
    }

    /** Visitor analytics events accepted into the Insights table. */
    public void analyticsEvents(int n) {
        counter("mmv.analytics.events", "Visitor analytics events recorded").increment(n);
    }

    private Counter counter(String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }
}
