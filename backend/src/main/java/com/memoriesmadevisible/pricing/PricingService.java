package com.memoriesmadevisible.pricing;

import com.memoriesmadevisible.common.NotFoundException;
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

    @Transactional(readOnly = true)
    public boolean exists(String key) {
        return key != null && types.existsById(key);
    }

    /** New category appended to the end; key is a slug of the name, de-duplicated. */
    @Transactional
    public List<PricingDtos.SessionTypeView> create(PricingDtos.TypeCreate c) {
        String base = SessionType.slugify(c.name());
        String key = base;
        for (int i = 2; types.existsById(key); i++) key = base + "-" + i;
        int order = (int) types.count();
        types.save(new SessionType(key, c.name().strip(), blankToNull(c.shortName()),
                c.description() == null ? "" : c.description().strip(), c.from().strip(), order));
        return list();
    }

    /** Full save: every category in the list, in list order. Categories not in the list are left alone. */
    @Transactional
    public List<PricingDtos.SessionTypeView> update(List<PricingDtos.TypeUpdate> updates) {
        for (int i = 0; i < updates.size(); i++) {
            var u = updates.get(i);
            var t = types.findWithTiersByKey(u.key())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + u.key()));
            t.setName(u.name().strip());
            t.setShortName(blankToNull(u.shortName()));
            t.setDescription(u.description() == null ? "" : u.description().strip());
            t.setFromPrice(u.from().strip());
            t.setSortOrder(i);
            t.replaceTiers(u.tiers().stream()
                    .map(x -> new PricingTier(x.name().strip(), x.detail() == null ? "" : x.detail().strip(),
                            x.price().strip(), x.includes()))
                    .toList());
            types.save(t);
        }
        return list();
    }

    /** Removes a category and its packages. Existing bookings keep their record (they store the key). */
    @Transactional
    public List<PricingDtos.SessionTypeView> delete(String key) {
        var t = types.findById(key).orElseThrow(() -> new NotFoundException("Category " + key + " not found"));
        types.delete(t);
        return list();
    }

    /** Back to the launch price list: defaults restored, anything else removed. */
    @Transactional
    public List<PricingDtos.SessionTypeView> resetToDefaults() {
        types.deleteAll();
        types.flush();
        types.saveAll(PricingDefaults.sessionTypes());
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
