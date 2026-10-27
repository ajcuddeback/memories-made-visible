package com.memoriesmadevisible.pricing;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping
    public List<PricingDtos.SessionTypeView> create(@RequestBody @Valid PricingDtos.TypeCreate body) {
        return pricing.create(body);
    }

    @DeleteMapping("/{key}")
    public List<PricingDtos.SessionTypeView> delete(@PathVariable String key) {
        return pricing.delete(key);
    }

    @PostMapping("/reset")
    public List<PricingDtos.SessionTypeView> reset() {
        return pricing.resetToDefaults();
    }
}
