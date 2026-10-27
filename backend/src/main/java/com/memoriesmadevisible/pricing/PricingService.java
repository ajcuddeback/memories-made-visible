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

    /** Rewrite the draft from a published snapshot (Discard changes). Keys are kept so bookings still resolve. */
    @Transactional
    public void restore(List<PricingDtos.SessionTypeView> live) {
        types.deleteAll();
        types.flush();
        List<SessionType> rows = new java.util.ArrayList<>();
        for (int i = 0; i < live.size(); i++) {
            var v = live.get(i);
            var t = new SessionType(v.key(), v.name(), v.shortName().equals(v.name()) ? null : v.shortName(),
                    v.description(), v.from(), i);
            t.setShowInPortfolio(v.showInPortfolio());
            if (v.durationMinutes() > 0) t.setDurationMinutes(v.durationMinutes());
            t.replaceTiers(v.tiers().stream().map(x -> tierFrom(x, v.durationMinutes())).toList());
            rows.add(t);
        }
        types.saveAll(rows);
    }

    /** A tier entity from its DTO (admin save or snapshot restore). */
    static PricingTier tierFrom(PricingDtos.Tier x, int defaultMinutes) {
        var t = new PricingTier(x.name().strip(), x.detail() == null ? "" : x.detail().strip(), x.price().strip(), x.includes(),
                x.durationMinutes() == null ? defaultMinutes : x.durationMinutes());
        t.setPricingModel(x.hourly() ? PricingTier.PricingModel.HOURLY : PricingTier.PricingModel.FLAT);
        t.setHourRange(x.minHours(), x.maxHours());
        t.setAddOns(x.addonsOrEmpty().stream().map(a -> new PricingTier.AddOn(a.name(), a.price(), a.perHour())).toList());
        var seen = new java.util.HashSet<String>();
        for (var a : t.getAddOns()) {
            if (!seen.add(a.name().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("\"" + x.name() + "\" has two add-ons called \"" + a.name() + "\".");
            }
        }
        return t;
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
        var t = new SessionType(key, c.name().strip(), blankToNull(c.shortName()),
                c.description() == null ? "" : c.description().strip(), c.from().strip(), order);
        t.setShowInPortfolio(c.showInPortfolio() == null || c.showInPortfolio());
        if (c.durationMinutes() != null) t.setDurationMinutes(c.durationMinutes());
        types.save(t);
        return list();
    }

    /**
     * Save the given categories. When the list covers every category it also sets the order;
     * a partial list leaves the existing order alone.
     */
    @Transactional
    public List<PricingDtos.SessionTypeView> update(List<PricingDtos.TypeUpdate> updates) {
        boolean reorder = updates.size() >= types.count();
        for (int i = 0; i < updates.size(); i++) {
            var u = updates.get(i);
            var t = types.findWithTiersByKey(u.key())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + u.key()));
            t.setName(u.name().strip());
            t.setShortName(blankToNull(u.shortName()));
            t.setDescription(u.description() == null ? "" : u.description().strip());
            t.setFromPrice(u.from().strip());
            t.setShowInPortfolio(u.showInPortfolio());
            if (u.durationMinutes() != null) t.setDurationMinutes(u.durationMinutes());
            var seen = new java.util.HashSet<String>();
            for (var x : u.tiers()) {
                if (!seen.add(x.name().strip().toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("\"" + u.name() + "\" has two packages called \"" + x.name().strip() + "\" — package names need to be unique.");
                }
            }
            if (reorder) t.setSortOrder(i);
            t.replaceTiers(u.tiers().stream()
                    .map(x -> tierFrom(x, t.getDurationMinutes()))
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
