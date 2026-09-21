package com.portfolio.analytics.intelligence;

import java.util.Locale;

/**
 * Shared sector matching for stress custom shocks and what-if SWITCH.
 * Short tokens (e.g. {@code IT}) use alias groups only — never substring —
 * so {@code Industrials} is not shocked when the user picks IT.
 */
public final class SectorMatcher {

    private SectorMatcher() {
    }

    public static boolean matches(String holdingSector, String target) {
        if (holdingSector == null || target == null) {
            return false;
        }
        String h = holdingSector.trim();
        String t = target.trim();
        if (h.isEmpty() || t.isEmpty()) {
            return false;
        }
        if (isUnusable(h) || isUnusable(t)) {
            return false;
        }

        if (matchesBanking(t) && matchesBanking(h)) {
            return true;
        }
        if (matchesIt(t) && matchesIt(h)) {
            return true;
        }
        if (matchesAuto(t) && matchesAuto(h)) {
            return true;
        }
        if (matchesPharma(t) && matchesPharma(h)) {
            return true;
        }
        if (matchesEnergy(t) && matchesEnergy(h)) {
            return true;
        }
        if (matchesFmcg(t) && matchesFmcg(h)) {
            return true;
        }

        String hl = h.toLowerCase(Locale.ROOT);
        String tl = t.toLowerCase(Locale.ROOT);
        if (hl.equals(tl)) {
            return true;
        }
        // Avoid "IT" ⊆ "Industrials" / "it" ⊆ "utilities".
        if (tl.length() <= 3) {
            return false;
        }
        return hl.contains(tl) || tl.contains(hl);
    }

    public static boolean isUnusable(String sector) {
        if (sector == null) {
            return true;
        }
        String s = sector.trim().toLowerCase(Locale.ROOT);
        return s.isEmpty()
                || s.equals("-")
                || s.equals("n/a")
                || s.equals("na")
                || s.equals("unknown")
                || s.equals("unknown sector")
                || s.equals("other")
                || s.equals("null");
    }

    public static boolean matchesBanking(String sector) {
        if (sector == null) {
            return false;
        }
        String s = sector.toLowerCase(Locale.ROOT);
        return s.contains("bank")
                || s.contains("financial")
                || s.contains("finance")
                || s.equals("bfsi");
    }

    public static boolean matchesIt(String sector) {
        if (sector == null) {
            return false;
        }
        String s = sector.toLowerCase(Locale.ROOT).trim();
        return s.equals("it")
                || s.equals("itech")
                || s.contains("information technology")
                || s.contains("technolog")
                || s.contains("software")
                || s.contains("computer");
    }

    public static boolean matchesAuto(String sector) {
        if (sector == null) {
            return false;
        }
        String s = sector.toLowerCase(Locale.ROOT);
        return s.contains("auto")
                || s.contains("vehicle");
    }

    public static boolean matchesPharma(String sector) {
        if (sector == null) {
            return false;
        }
        String s = sector.toLowerCase(Locale.ROOT);
        return s.contains("pharma")
                || s.contains("drug")
                || s.contains("healthcare")
                || s.contains("health care")
                || s.contains("biotech");
    }

    public static boolean matchesEnergy(String sector) {
        if (sector == null) {
            return false;
        }
        String s = sector.toLowerCase(Locale.ROOT);
        return s.contains("energy")
                || s.contains("oil")
                || s.contains("gas")
                || s.contains("power")
                || s.contains("petroleum");
    }

    /** FMCG / staples — short token {@code FMCG} must match full holding labels. */
    public static boolean matchesFmcg(String sector) {
        if (sector == null) {
            return false;
        }
        String s = sector.toLowerCase(Locale.ROOT).trim();
        return s.equals("fmcg")
                || s.contains("fast moving consumer")
                || s.contains("consumer staples");
    }
}
