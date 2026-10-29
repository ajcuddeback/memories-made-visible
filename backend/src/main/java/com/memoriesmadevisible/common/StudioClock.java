package com.memoriesmadevisible.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * "Today" and "now" in the studio's own time zone, not the server's. Containers run in UTC,
 * so without this a Florida customer could see tomorrow's slots at 8pm or lose today's at midnight UTC.
 */
@Component
public class StudioClock {

    private final ZoneId zone;

    public StudioClock(@Value("${app.time-zone:America/New_York}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() { return zone; }
    public LocalDate today() { return LocalDate.now(zone); }
    public LocalDateTime now() { return LocalDateTime.now(zone); }
}
