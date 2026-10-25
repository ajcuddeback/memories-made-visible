package com.memoriesmadevisible.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class PricingDtos {

    private PricingDtos() {}

    public record Tier(@NotBlank @Size(max = 80) String name, @Size(max = 200) String detail,
                       @NotBlank @Size(max = 20) String price) {}

    public record SessionTypeView(String key, String name, String description, String from, List<Tier> tiers) {}

    /** Admin save: only the editable bits (from price + packages) per type. */
    public record TypeUpdate(@NotBlank String key, @NotBlank @Size(max = 20) String from,
                             @NotNull @Valid List<Tier> tiers) {}

    static SessionTypeView toView(SessionType t) {
        return new SessionTypeView(t.getKey(), t.getName(), t.getDescription(), t.getFromPrice(),
                t.getTiers().stream().map(x -> new Tier(x.getName(), x.getDetail(), x.getPrice())).toList());
    }
}
