package com.memoriesmadevisible.pricing;

import java.util.List;

/** The studio's launch price list. "Reset to defaults" in the admin restores exactly this. */
public final class PricingDefaults {

    private PricingDefaults() {}

    public static List<SessionType> sessionTypes() {
        return List.of(
                type("families", "Families & solo portraits", "Families & solo",
                        "Relaxed sessions at home, at the park or by the lake, for the whole family or just you.", "$150", 0,
                        new PricingTier("Solo", "30 minutes · 15 edited photos", "$150", List.of(
                                "30-minute session at one location", "15 professionally edited photos",
                                "Private online gallery", "Print release", "Travel within Polk County"), 30),
                        new PricingTier("Family", "1 hour · up to 6 people · 30 edited photos", "$225", List.of(
                                "1-hour session at one location", "Up to 6 people", "30 professionally edited photos",
                                "Private online gallery", "Print release", "Travel within Polk County"), 60)),
                type("couples", "Couples & engagements", "Couples",
                        "Engagements, anniversaries and the everyday moments in between.", "$150", 1,
                        new PricingTier("Mini", "30 minutes · 20 edited photos", "$150", List.of(
                                "30-minute session", "20 professionally edited photos", "Private online gallery", "Print release"), 30),
                        new PricingTier("Full session", "1 hour · 2 locations · 40 edited photos", "$225", List.of(
                                "1-hour session", "Up to 2 locations", "40 professionally edited photos",
                                "Private online gallery", "Print release"), 60)),
                type("weddings", "Weddings", "Weddings",
                        "Wedding-day coverage with a lead and a second photographer.", "$800", 2,
                        new PricingTier("Intimate", "2 hours · lead photographer", "$800", List.of(
                                "2 hours of coverage", "Lead photographer", "Private online gallery", "Print release"), 120),
                        new PricingTier("Classic", "6 hours · two photographers", "$1,800", List.of(
                                "6 hours of coverage", "Lead and second photographer", "Getting ready through reception",
                                "Private online gallery", "Print release"), 360),
                        new PricingTier("Full day", "8 hours · two photographers", "$2,400", List.of(
                                "8 hours of coverage", "Lead and second photographer", "Full day, first look to send-off",
                                "Private online gallery", "Print release"), 480)),
                type("maternity", "Maternity & newborn", "Maternity & newborn",
                        "Calm, unhurried sessions for the newest chapter of your family.", "$200", 3,
                        new PricingTier("Maternity", "1 hour · 25 edited photos", "$200", List.of(
                                "1-hour session", "25 professionally edited photos", "Private online gallery", "Print release"), 60),
                        new PricingTier("Newborn", "Up to 2 hours at home · 25 edited photos", "$250", List.of(
                                "Up to 2 hours in your home", "Baby-led pace with feeding breaks", "25 professionally edited photos",
                                "Private online gallery", "Print release"), 120),
                        new PricingTier("Bump to baby", "Both sessions", "$400", List.of(
                                "Maternity session", "Newborn session", "50 professionally edited photos across both",
                                "Private online gallery", "Print release"), 60)),
                type("seniors", "Seniors & graduation", "Seniors",
                        "Senior portraits and graduation milestones, styled to the student.", "$150", 4,
                        new PricingTier("Mini", "30 minutes · 1 outfit · 15 edited photos", "$150", List.of(
                                "30-minute session", "1 outfit", "15 professionally edited photos", "Private online gallery", "Print release"), 30),
                        new PricingTier("Classic", "1 hour · 3 outfits · 2 locations · 35 edited photos", "$250", List.of(
                                "1-hour session", "Up to 3 outfits", "Up to 2 locations", "35 professionally edited photos",
                                "Private online gallery", "Print release"), 360)),
                type("events", "Events", "Events",
                        "Birthdays, showers, parties and community events across Polk County.", "$150/hr", 5,
                        hourly(new PricingTier("Event coverage", "By the hour · 2-hour minimum", "$150/hr", List.of(
                                "Candid and group photos", "Private online gallery", "Print release"), 120), 2, 8,
                                new PricingTier.AddOn("Second photographer", new java.math.BigDecimal("75"), true, true)))
        );
    }

    private static PricingTier hourly(PricingTier t, int minHours, int maxHours, PricingTier.AddOn... addOns) {
        t.setPricingModel(PricingTier.PricingModel.HOURLY);
        t.setHourRange(minHours, maxHours);
        t.setAddOns(List.of(addOns));
        return t;
    }

    private static final java.util.Map<String, Integer> DURATION = java.util.Map.of(
            "families", 60, "couples", 60, "weddings", 240, "maternity", 60, "seniors", 60, "events", 180);

    private static SessionType type(String key, String name, String shortName, String desc, String from, int order,
                                    PricingTier... tiers) {
        var t = new SessionType(key, name, shortName, desc, from, order);
        t.setDurationMinutes(DURATION.getOrDefault(key, 60));
        t.replaceTiers(List.of(tiers));
        return t;
    }
}
