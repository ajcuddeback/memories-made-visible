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

    /** Portfolio photos per category are open-ended (up to this many); the admin always sees one empty slot to fill next. */
    public static final int MAX_PER_CATEGORY = 40;
    private static final Pattern PORTFOLIO = Pattern.compile("^pf-([a-z0-9-]+)-([1-9][0-9]?)$");

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

    /** Fixed slots, then per category every filled portfolio slot plus one empty one to add to. */
    public static List<Group> groups(List<PricingDtos.SessionTypeView> categories, Set<String> filledSlotIds) {
        List<Group> groups = new ArrayList<>();
        groups.add(FIXED);
        for (var c : categories) {
            if (!c.showInPortfolio()) continue;
            int highest = 0;
            for (String id : filledSlotIds) {
                var m = PORTFOLIO.matcher(id);
                if (m.matches() && m.group(1).equals(c.key())) highest = Math.max(highest, Integer.parseInt(m.group(2)));
            }
            int count = Math.min(MAX_PER_CATEGORY, highest + 1);
            List<Slot> slots = new ArrayList<>();
            for (int n = 1; n <= count; n++) {
                slots.add(new Slot("pf-" + c.key() + "-" + n, c.shortName() + " " + n));
            }
            groups.add(new Group("Portfolio · " + c.shortName(), slots));
        }
        return groups;
    }

    /** A slot is valid if it's a fixed one or a portfolio slot of an existing category (hidden ones keep their photos). */
    public static boolean isValid(String slotId, java.util.function.Predicate<String> categoryExists) {
        if (slotId == null) return false;
        if (FIXED_IDS.contains(slotId)) return true;
        var m = PORTFOLIO.matcher(slotId);
        return m.matches() && Integer.parseInt(m.group(2)) <= MAX_PER_CATEGORY && categoryExists.test(m.group(1));
    }
}
