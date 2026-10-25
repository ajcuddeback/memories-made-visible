package com.memoriesmadevisible.pricing;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class PricingService {

    private final SessionTypeRepository types;

    public PricingService(SessionTypeRepository types) {
        this.types = types;
    }

    /** First boot seeds the price list; after that the admin owns it. */
    @PostConstruct
    @Transactional
    public void ensureDefaults() {
        if (types.count() == 0) {
            types.saveAll(PricingDefaults.sessionTypes());
        }
    }

    @Transactional(readOnly = true)
    public List<PricingDtos.SessionTypeView> list() {
        return types.findAllByOrderBySortOrderAsc().stream().map(PricingDtos::toView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<SessionType> findType(String key) {
        return key == null ? Optional.empty() : types.findById(key.trim().toLowerCase());
    }

    @Transactional
    public List<PricingDtos.SessionTypeView> update(List<PricingDtos.TypeUpdate> updates) {
        for (var u : updates) {
            var t = types.findWithTiersByKey(u.key())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown session type: " + u.key()));
            t.setFromPrice(u.from().trim());
            t.replaceTiers(u.tiers().stream()
                    .map(x -> new PricingTier(x.name().trim(), x.detail() == null ? "" : x.detail().trim(), x.price().trim()))
                    .toList());
            types.save(t);
        }
        return list();
    }

    @Transactional
    public List<PricingDtos.SessionTypeView> resetToDefaults() {
        for (var dflt : PricingDefaults.sessionTypes()) {
            var t = types.findWithTiersByKey(dflt.getKey()).orElse(null);
            if (t == null) {
                types.save(dflt);
            } else {
                t.setFromPrice(dflt.getFromPrice());
                t.replaceTiers(dflt.getTiers().stream()
                        .map(x -> new PricingTier(x.getName(), x.getDetail(), x.getPrice())).toList());
                types.save(t);
            }
        }
        return list();
    }

    /** "$1,800" → 1800; "$150/hr" → 150; garbage → 0. */
    public static BigDecimal parseAmount(String display) {
        if (display == null) return BigDecimal.ZERO;
        String digits = display.replaceAll("[^0-9.]", "");
        if (digits.isEmpty() || digits.equals(".")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
