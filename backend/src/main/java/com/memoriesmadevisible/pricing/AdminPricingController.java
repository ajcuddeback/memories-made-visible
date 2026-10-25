package com.memoriesmadevisible.pricing;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/pricing")
public class AdminPricingController {

    private final PricingService pricing;

    public AdminPricingController(PricingService pricing) {
        this.pricing = pricing;
    }

    @PutMapping
    public List<PricingDtos.SessionTypeView> save(@RequestBody @Valid List<PricingDtos.TypeUpdate> updates) {
        return pricing.update(updates);
    }

    @PostMapping("/reset")
    public List<PricingDtos.SessionTypeView> reset() {
        return pricing.resetToDefaults();
    }
}
