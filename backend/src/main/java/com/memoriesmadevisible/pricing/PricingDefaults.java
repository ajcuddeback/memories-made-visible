package com.memoriesmadevisible.pricing;

import java.util.List;

/** The studio's launch price list. "Reset to defaults" in the admin restores exactly this. */
public final class PricingDefaults {

    private PricingDefaults() {}

    public static List<SessionType> sessionTypes() {
        return List.of(
                type("families", "Families & solo portraits",
                        "Relaxed sessions at home, at the park or by the lake, for the whole family or just you.", "$150", 0,
                        new PricingTier("Solo", "30 minutes · 15 edited photos", "$150"),
                        new PricingTier("Family", "1 hour · up to 6 people · 30 edited photos", "$225")),
                type("couples", "Couples & engagements",
                        "Engagements, anniversaries and the everyday moments in between.", "$150", 1,
                        new PricingTier("Mini", "30 minutes · 20 edited photos", "$150"),
                        new PricingTier("Full session", "1 hour · 2 locations · 40 edited photos", "$225")),
                type("weddings", "Weddings",
                        "Wedding-day coverage with a lead and a second photographer.", "$800", 2,
                        new PricingTier("Intimate", "2 hours · lead photographer", "$800"),
                        new PricingTier("Classic", "6 hours · two photographers", "$1,800"),
                        new PricingTier("Full day", "8 hours · two photographers", "$2,400")),
                type("maternity", "Maternity & newborn",
                        "Calm, unhurried sessions for the newest chapter of your family.", "$200", 3,
                        new PricingTier("Maternity", "1 hour · 25 edited photos", "$200"),
                        new PricingTier("Newborn", "Up to 2 hours at home · 25 edited photos", "$250"),
                        new PricingTier("Bump to baby", "Both sessions", "$400")),
                type("seniors", "Seniors & graduation",
                        "Senior portraits and graduation milestones, styled to the student.", "$150", 4,
                        new PricingTier("Mini", "30 minutes · 1 outfit · 15 edited photos", "$150"),
                        new PricingTier("Classic", "1 hour · 3 outfits · 2 locations · 35 edited photos", "$250")),
                type("events", "Events",
                        "Birthdays, showers, parties and community events across Polk County.", "$150/hr", 5,
                        new PricingTier("Event coverage", "2-hour minimum", "$150/hr"),
                        new PricingTier("Second photographer", "Added coverage", "+$75/hr"))
        );
    }

    private static SessionType type(String key, String name, String desc, String from, int order, PricingTier... tiers) {
        var t = new SessionType(key, name, desc, from, order);
        t.replaceTiers(List.of(tiers));
        return t;
    }
}
