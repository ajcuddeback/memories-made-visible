package com.memoriesmadevisible.seed;

import com.memoriesmadevisible.booking.Booking;
import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.booking.BookingStatus;
import com.memoriesmadevisible.event.PopupEvent;
import com.memoriesmadevisible.event.PopupEventRepository;
import com.memoriesmadevisible.gallery.Gallery;
import com.memoriesmadevisible.gallery.GalleryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Fills an empty database with the sample bookings, pop-ups and galleries from
 * the design mock-ups so the admin has something to look at locally.
 * Enabled with {@code app.seed-demo-data=true} (on in the {@code dev} profile only).
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final BookingRepository bookings;
    private final PopupEventRepository events;
    private final GalleryRepository galleries;

    public DemoDataSeeder(BookingRepository bookings, PopupEventRepository events, GalleryRepository galleries) {
        this.bookings = bookings;
        this.events = events;
        this.galleries = galleries;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bookings.count() > 0 || events.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<Booking> seeded = bookings.saveAll(List.of(
                booking("families", "Morgan Ellis", today.plusDays(13), "4:00 PM", "Lake Mirror, Lakeland", BookingStatus.PENDING, 225,
                        "Family of five, two kids under 6. Would love some by the water."),
                booking("weddings", "Taylor & Jordan Reyes", today.plusMonths(5), "2:00 PM", "Winter Haven", BookingStatus.PENDING, 1800,
                        "Ceremony and reception at the same venue, about 120 guests."),
                booking("seniors", "Ava Thompson", today.plusDays(21), "Golden hour", "Bartow", BookingStatus.PENDING, 150, null),
                booking("weddings", "Hannah & Luke Price", today.minusDays(43), "2:00 PM", "Lakeland", BookingStatus.CONFIRMED, 1800, null),
                booking("seniors", "Mia Johnson", today.minusDays(64), "Golden hour", "Auburndale", BookingStatus.CONFIRMED, 250, null),
                booking("families", "Grace Kim", today.minusDays(28), "9:00 AM", "Lakeland", BookingStatus.CONFIRMED, 150, "Solo portraits"),
                booking("maternity", "Priya Shah", today.minusDays(21), "4:00 PM", "Winter Haven", BookingStatus.CONFIRMED, 200, null),
                booking("couples", "Chris & Dana Lee", today.minusDays(14), "Golden hour", "Lake Wales", BookingStatus.CONFIRMED, 225, null),
                booking("families", "The Ortiz family", today.minusDays(6), "9:00 AM", "Haines City", BookingStatus.CONFIRMED, 225, null),
                booking("maternity", "Baby Carter", today.plusDays(8), "11:00 AM", "Lakeland", BookingStatus.CONFIRMED, 250, "Newborn session at home"),
                booking("couples", "Sam & Riley Brooks", today.minusDays(99), "Golden hour", "Lakeland", BookingStatus.CONFIRMED, 150, null),
                booking("events", "Jenna Ward", today.plusDays(6), "2:00 PM", "Bartow", BookingStatus.DECLINED, 300, "Halloween party, 3 hours")
        ));

        events.saveAll(List.of(
                event(today.minusDays(7), "Fall Festival Pop-Up", "Pumpkin patch", "Market name · Lakeland", "9:00 AM – 1:00 PM", 64),
                event(today.plusDays(20), "Fall Harvest Pop-Up", "Autumn set", "Market name · Lakeland", "9:00 AM – 1:00 PM", 0),
                event(today.plusDays(42), "Holiday Portraits", "Winter wonderland", "Market name · Winter Haven", "10:00 AM – 2:00 PM", 0),
                event(today.plusDays(49), "Pets & Santa", "Holiday set", "Market name · Bartow", "10:00 AM – 3:00 PM", 0)
        ));

        // Galleries for the completed sessions, at various stages of delivery.
        int[] stages = {3, 3, 3, 2, 1, 1, 0};
        String[] names = {"Sam & Riley Brooks", "Mia Johnson", "Hannah & Luke Price", "Grace Kim", "Priya Shah", "Chris & Dana Lee", "The Ortiz family"};
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            seeded.stream().filter(b -> b.getName().equals(n)).findFirst().ifPresent(b -> {
                var g = new Gallery();
                g.setBookingId(b.getId());
                g.setStage(stages[indexOf(names, n)]);
                galleries.save(g);
            });
        }
        log.info("Seeded demo bookings, pop-ups and galleries.");
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private static Booking booking(String type, String name, LocalDate date, String time, String location,
                                   BookingStatus status, int quote, String notes) {
        var b = new Booking();
        b.setSessionTypeKey(type);
        b.setName(name);
        b.setEmail(name.split(" ")[0].toLowerCase().replaceAll("[^a-z]", "") + "@example.com");
        b.setPhone("(863) 555-01" + String.format("%02d", Math.abs(name.hashCode()) % 100));
        b.setLocation(location);
        b.setNotes(notes);
        b.setDate(date);
        // Demo rows are written straight to the table, so give them the time range a real request would get.
        java.time.LocalTime start = switch (time) {
            case "9:00 AM" -> java.time.LocalTime.of(9, 0);
            case "11:00 AM" -> java.time.LocalTime.of(11, 0);
            case "2:00 PM" -> java.time.LocalTime.of(14, 0);
            case "4:00 PM" -> java.time.LocalTime.of(16, 0);
            default -> java.time.LocalTime.of(17, 0); // "Golden hour"
        };
        int minutes = switch (type) { case "weddings" -> 240; case "events" -> 180; default -> 60; };
        b.setStartTime(start);
        b.setEndTime(start.plusMinutes(minutes));
        b.setTime(com.memoriesmadevisible.availability.AvailabilityDtos.label(start));
        b.setStatus(status);
        b.setQuote(BigDecimal.valueOf(quote));
        return b;
    }

    private static PopupEvent event(LocalDate date, String title, String theme, String venue, String time, int prints) {
        var e = new PopupEvent();
        e.setDate(date);
        e.setTitle(title);
        e.setTheme(theme);
        e.setVenue(venue);
        e.setTime(time);
        e.setPrints(prints);
        e.setPrintPrice(BigDecimal.TEN);
        return e;
    }
}
