package com.memoriesmadevisible.photo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fixed set of photo slots on the website. The admin's "Photos" section
 * and the public pages both render from this list, so a slot id is a contract
 * between the two — add here first, then reference it from the frontend.
 */
public final class PhotoSlots {

    private PhotoSlots() {}

    public record Slot(String id, String label) {}
    public record Group(String title, List<Slot> slots) {}

    private static final List<Group> GROUPS;
    private static final Set<String> IDS;

    static {
        List<Group> groups = new ArrayList<>();
        groups.add(new Group("Home, events & about", List.of(
                new Slot("home-hero", "Home · hero"),
                new Slot("home-feat-1", "Home · favorite 1"),
                new Slot("home-feat-2", "Home · favorite 2"),
                new Slot("home-feat-3", "Home · favorite 3"),
                new Slot("home-erica", "Home · Erica"),
                new Slot("events-set-1", "Events · set"),
                new Slot("events-set-2", "Events · prints"),
                new Slot("about-erica", "About · Erica"),
                new Slot("about-austin", "About · Austin"))));
        String[][] cats = {
                {"weddings", "Weddings"}, {"families", "Families & solo"}, {"couples", "Couples"},
                {"maternity", "Maternity & newborn"}, {"seniors", "Seniors"}, {"events", "Events"}};
        for (String[] c : cats) {
            List<Slot> slots = new ArrayList<>();
            for (int n = 1; n <= 3; n++) {
                slots.add(new Slot("pf-" + c[0] + "-" + n, c[1] + " " + n));
            }
            groups.add(new Group("Portfolio · " + c[1], slots));
        }
        GROUPS = List.copyOf(groups);
        IDS = GROUPS.stream().flatMap(g -> g.slots().stream()).map(Slot::id).collect(Collectors.toUnmodifiableSet());
    }

    public static List<Group> groups() {
        return GROUPS;
    }

    public static boolean isValid(String slotId) {
        return slotId != null && IDS.contains(slotId);
    }
}
