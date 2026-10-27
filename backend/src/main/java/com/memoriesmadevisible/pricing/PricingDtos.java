package com.memoriesmadevisible.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class PricingDtos {

    private PricingDtos() {}

    /** An optional extra; {@code perHour} extras are charged per hour of the session. */
    public record AddOn(@NotBlank @Size(max = 80) String name, @NotNull java.math.BigDecimal price, boolean perHour) {}

    /**
     * A package. FLAT: {@code durationMinutes} long for {@code price}. HOURLY: {@code price} is per
     * hour ("$150/hr"), the customer picks {@code minHours}…{@code maxHours}. Fields missing on old
     * snapshots arrive null and mean FLAT / category default.
     */
    public record Tier(@NotBlank @Size(max = 80) String name, @Size(max = 200) String detail,
                       @NotBlank @Size(max = 20) String price, List<@Size(max = 200) String> includes,
                       Integer durationMinutes, String pricingModel, Integer minHours, Integer maxHours,
                       List<@Valid AddOn> addons) {
        public boolean hourly() { return "HOURLY".equalsIgnoreCase(pricingModel); }
        public List<AddOn> addonsOrEmpty() { return addons == null ? List.of() : addons; }
    }

    public record SessionTypeView(String key, String name, String shortName, String description, String from,
                                  boolean showInPortfolio, int durationMinutes, List<Tier> tiers) {}

    /** Admin save: the whole category, in the order the list arrives. */
    public record TypeUpdate(@NotBlank String key, @NotBlank @Size(max = 80) String name, @Size(max = 40) String shortName,
                             @Size(max = 300) String description, @NotBlank @Size(max = 20) String from,
                             boolean showInPortfolio, Integer durationMinutes, @NotNull @Valid List<Tier> tiers) {}

    public record TypeCreate(@NotBlank @Size(max = 80) String name, @Size(max = 40) String shortName,
                             @Size(max = 300) String description, @NotBlank @Size(max = 20) String from,
                             Boolean showInPortfolio, Integer durationMinutes) {}

    public static SessionTypeView toView(SessionType t) {
        return new SessionTypeView(t.getKey(), t.getName(), t.displayShortName(), t.getDescription(), t.getFromPrice(),
                t.isShowInPortfolio(), t.getDurationMinutes(), t.getTiers().stream().map(x -> new Tier(x.getName(), x.getDetail(), x.getPrice(), x.getIncludes(), x.getDurationMinutes(),
                        x.getPricingModel().name(), x.getMinHours(), x.getMaxHours(),
                        x.getAddOns().stream().map(a -> new AddOn(a.name(), a.price(), a.perHour())).toList())).toList());
    }
}
