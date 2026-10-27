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
    private final com.memoriesmadevisible.auth.StaffAccess access;

    public AdminAvailabilityController(AvailabilityService availability, com.memoriesmadevisible.common.StudioClock clock,
                                       com.memoriesmadevisible.auth.StaffAccess access) {
        this.availability = availability;
        this.clock = clock;
        this.access = access;
    }

    @GetMapping("/hours")
    public List<AvailabilityDtos.Hours> hours() {
        return availability.hours();
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
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
    public AvailabilityDtos.Block addBlock(@Valid @RequestBody AvailabilityDtos.BlockCreate body, java.security.Principal me) {
        return availability.addBlock(body, me.getName(), access.limited(me));
    }

    @DeleteMapping("/blocks/{id}")
    public ResponseEntity<Void> removeBlock(@PathVariable Long id, java.security.Principal me) {
        availability.removeBlock(id, me.getName(), access.limited(me));
        return ResponseEntity.noContent().build();
    }
}
