package com.memoriesmadevisible.photo;

import com.memoriesmadevisible.pricing.PricingDtos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The photo slots on the website. The fixed ones (home, events, about) are
 * listed here; the portfolio ones are derived from the pricing categories —
 * three per category, `pf-<category key>-1..3` — so adding a category in the
 * admin automatically gives it portfolio slots and a filter.
 */
public final class PhotoSlots {

    private PhotoSlots() {}

    public record Slot(String id, String label) {}
    public record Group(String title, List<Slot> slots) {}

    public static final int PER_CATEGORY = 3;
    private static final Pattern PORTFOLIO = Pattern.compile("^pf-([a-z0-9-]+)-([1-3])$");

    public static final Group FIXED = new Group("Home, events & about", List.of(
            new Slot("home-hero", "Home · hero"),
            new Slot("home-feat-1", "Home · favorite 1"),
            new Slot("home-feat-2", "Home · favorite 2"),
            new Slot("home-feat-3", "Home · favorite 3"),
            new Slot("home-erica", "Home · Erica"),
            new Slot("events-set-1", "Events · set"),
            new Slot("events-set-2", "Events · prints"),
            new Slot("about-erica", "About · Erica"),
            new Slot("about-austin", "About · Austin")));

    private static final Set<String> FIXED_IDS = Set.copyOf(FIXED.slots().stream().map(Slot::id).toList());

    public static List<Group> groups(List<PricingDtos.SessionTypeView> categories) {
        List<Group> groups = new ArrayList<>();
        groups.add(FIXED);
        for (var c : categories) {
            List<Slot> slots = new ArrayList<>();
            for (int n = 1; n <= PER_CATEGORY; n++) {
                slots.add(new Slot("pf-" + c.key() + "-" + n, c.shortName() + " " + n));
            }
            groups.add(new Group("Portfolio · " + c.shortName(), slots));
        }
        return groups;
    }

    /** A slot is valid if it's a fixed one or a portfolio slot of an existing category. */
    public static boolean isValid(String slotId, java.util.function.Predicate<String> categoryExists) {
        if (slotId == null) return false;
        if (FIXED_IDS.contains(slotId)) return true;
        var m = PORTFOLIO.matcher(slotId);
        return m.matches() && categoryExists.test(m.group(1));
    }
}
