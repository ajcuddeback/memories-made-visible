package com.memoriesmadevisible.availability;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/availability")
public class AdminAvailabilityController {

    private final AvailabilityService availability;
    private final com.memoriesmadevisible.common.StudioClock clock;

    public AdminAvailabilityController(AvailabilityService availability, com.memoriesmadevisible.common.StudioClock clock) {
        this.availability = availability;
        this.clock = clock;
    }

    @GetMapping("/hours")
    public List<AvailabilityDtos.Hours> hours() {
        return availability.hours();
    }

    @PutMapping("/hours")
    public List<AvailabilityDtos.Hours> saveHours(@RequestBody List<AvailabilityDtos.Hours> body) {
        return availability.saveHours(body);
    }

    /** Blocks touching [from, to]; defaults to a year back and two ahead. */
    @GetMapping("/blocks")
    public List<AvailabilityDtos.Block> blocks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = clock.today();
        return availability.blocks(from == null ? today.minusYears(1) : from, to == null ? today.plusYears(2) : to);
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityDtos.Block addBlock(@Valid @RequestBody AvailabilityDtos.BlockCreate body) {
        return availability.addBlock(body);
    }

    @DeleteMapping("/blocks/{id}")
    public ResponseEntity<Void> removeBlock(@PathVariable Long id) {
        availability.removeBlock(id);
        return ResponseEntity.noContent().build();
    }
}
