package com.memoriesmadevisible;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prometheus metrics are served on the management port only, and the studio's own counters move when
 * the things they count happen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
class MetricsTest {

    @Autowired TestRestTemplate http;
    @Autowired MeterRegistry registry;
    @Autowired com.memoriesmadevisible.analytics.AnalyticsEventRepository events;
    @LocalServerPort int serverPort;
    @Value("${local.management.port}") int managementPort;

    @Test
    void prometheusScrapeIsOnTheManagementPortOnly() {
        var scrape = http.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus", String.class);
        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .contains("mmv_bookings_pending")
                .contains("mmv_bookings_upcoming")
                .contains("http_server_requests_seconds")
                .contains("jvm_memory_used_bytes")
                .contains("application=\"mmv\"");

        // The same path on the public port is locked, so nginx can never leak it by accident.
        var leak = http.getForEntity("http://localhost:" + serverPort + "/actuator/prometheus", String.class);
        assertThat(leak.getStatusCode().is2xxSuccessful()).isFalse();
        // Readiness (including the database) stays on the API port for the container healthcheck.
        var ready = http.getForEntity("http://localhost:" + serverPort + "/readyz", String.class);
        assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ready.getBody()).contains("UP");
    }

    @Test
    void businessCountersMove() {
        double failedBefore = registry.counter("mmv.logins", "outcome", "failed").count();
        http.postForEntity("http://localhost:" + serverPort + "/api/admin/auth/login",
                Map.of("name", "Austin", "password", "definitely-wrong"), String.class);
        assertThat(registry.counter("mmv.logins", "outcome", "failed").count()).isEqualTo(failedBefore + 1);

        double eventsBefore = registry.counter("mmv.analytics.events").count();
        var beacon = http.postForEntity("http://localhost:" + serverPort + "/api/analytics",
                Map.of("events", java.util.List.of(Map.of("kind", "view", "path", "/metrics-test", "visitor", "metrics-v", "session", "metrics-s"))), String.class);
        assertThat(beacon.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(registry.counter("mmv.analytics.events").count()).isGreaterThan(eventsBefore);
        // The in-memory database is shared with the other test classes; leave their counts alone.
        events.findAll().stream().filter(e -> "/metrics-test".equals(e.getPath())).forEach(events::delete);

        double okBefore = registry.counter("mmv.logins", "outcome", "ok").count();
        var login = http.postForEntity("http://localhost:" + serverPort + "/api/admin/auth/login",
                Map.of("name", "Austin", "password", "test-pass"), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registry.counter("mmv.logins", "outcome", "ok").count()).isEqualTo(okBefore + 1);
    }

}
