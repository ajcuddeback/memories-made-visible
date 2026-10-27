package com.memoriesmadevisible;

import com.memoriesmadevisible.booking.BookingDtos;
import com.memoriesmadevisible.booking.BookingRepository;
import com.memoriesmadevisible.booking.BookingService;
import com.memoriesmadevisible.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The thing the day lock exists for: N requests for the same slot at the same instant → exactly one wins.
 * Runs against the same in-memory database as the other tests, so dates are chosen to stay out of their way.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingConcurrencyTest {

    @Autowired BookingService service;
    @Autowired BookingRepository bookings;
    @MockitoBean JavaMailSender mailSender;
    @MockitoBean S3Client s3Client;

    @Test
    void simultaneousRequestsForOneSlotLeaveExactlyOneBooking() throws Exception {
        org.mockito.Mockito.when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null));
        LocalDate day = LocalDate.now().plusDays(70);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) day = day.plusDays(1);
        final LocalDate d = day;

        int n = 8;
        var ready = new CountDownLatch(n);
        var go = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(n);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int who = i;
            Callable<String> task = () -> {
                ready.countDown();
                go.await();
                try {
                    service.create(new BookingDtos.CreateRequest("families", "Family", "Racer " + who, "racer" + who + "@example.com",
                            null, null, null, d, "10:00", null));
                    return "ok";
                } catch (ConflictException e) {
                    return "conflict";
                }
            };
            results.add(pool.submit(task));
        }
        ready.await();
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (var f : results) outcomes.add(get(f));
        pool.shutdown();

        assertThat(outcomes).containsOnlyOnce("ok");
        assertThat(outcomes.stream().filter("conflict"::equals).count()).isEqualTo(n - 1);
        assertThat(bookings.findAllByDateBetweenAndStatusNot(d, d, com.memoriesmadevisible.booking.BookingStatus.DECLINED))
                .filteredOn(b -> b.getStartTime() != null && b.getStartTime().toString().equals("10:00"))
                .hasSize(1);
    }

    private static String get(Future<String> f) throws InterruptedException {
        try {
            return f.get();
        } catch (ExecutionException e) {
            return "error: " + e.getCause();
        }
    }
}
