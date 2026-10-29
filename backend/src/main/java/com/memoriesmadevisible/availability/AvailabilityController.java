package com.memoriesmadevisible.availability;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Public: which start times are open, for the booking form's calendar. Never says why a time is taken. */
@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService availability;

    public AvailabilityController(AvailabilityService availability) {
        this.availability = availability;
    }

    @GetMapping
    public List<AvailabilityDtos.DaySlots> slots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(name = "package", required = false) String packageName,
                                                 @RequestParam(required = false) Integer hours,
                                                 @RequestParam(required = false) String photographer) {
        return availability.slots(from, to, type, packageName, hours, photographer);
    }
}
