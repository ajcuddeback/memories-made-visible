package com.memoriesmadevisible.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class PricingDtos {

    private PricingDtos() {}

    public record Tier(@NotBlank @Size(max = 80) String name, @Size(max = 200) String detail,
                       @NotBlank @Size(max = 20) String price, List<@Size(max = 200) String> includes) {}

    public record SessionTypeView(String key, String name, String shortName, String description, String from,
                                  List<Tier> tiers) {}

    /** Admin save: the whole category, in the order the list arrives. */
    public record TypeUpdate(@NotBlank String key, @NotBlank @Size(max = 80) String name, @Size(max = 40) String shortName,
                             @Size(max = 300) String description, @NotBlank @Size(max = 20) String from,
                             @NotNull @Valid List<Tier> tiers) {}

    public record TypeCreate(@NotBlank @Size(max = 80) String name, @Size(max = 40) String shortName,
                             @Size(max = 300) String description, @NotBlank @Size(max = 20) String from) {}

    static SessionTypeView toView(SessionType t) {
        return new SessionTypeView(t.getKey(), t.getName(), t.displayShortName(), t.getDescription(), t.getFromPrice(),
                t.getTiers().stream().map(x -> new Tier(x.getName(), x.getDetail(), x.getPrice(), x.getIncludes())).toList());
    }
}
