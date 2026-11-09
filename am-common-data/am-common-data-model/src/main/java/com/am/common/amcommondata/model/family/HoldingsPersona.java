package com.am.common.amcommondata.model.family;

/**
 * Scope for an all-books read.
 * {@code you} is the primary account (no familyMemberId).
 * A family member id is that member only.
 * Blank or {@code household} is every book on the login.
 */
public final class HoldingsPersona {

    public static final String YOU = "you";
    public static final String HOUSEHOLD = "household";

    private HoldingsPersona() {
    }

    public static boolean isHousehold(String persona) {
        if (persona == null || persona.isBlank()) {
            return true;
        }
        return HOUSEHOLD.equalsIgnoreCase(persona.trim());
    }

    /** Cache id for the all-books view. Null keeps the legacy household key. */
    public static String cacheId(String persona) {
        if (isHousehold(persona)) {
            return null;
        }
        String trimmed = persona.trim();
        if (YOU.equalsIgnoreCase(trimmed)) {
            return "all:you";
        }
        return "all:" + trimmed;
    }

    public static String personaFromCacheId(String cacheId) {
        if (cacheId == null || !cacheId.startsWith("all:")) {
            return null;
        }
        String rest = cacheId.substring(4);
        return rest.isBlank() ? null : rest;
    }

    public static boolean matches(String familyMemberId, String persona) {
        if (isHousehold(persona)) {
            return true;
        }
        boolean primary = familyMemberId == null || familyMemberId.isBlank();
        if (YOU.equalsIgnoreCase(persona.trim())) {
            return primary;
        }
        return !primary && persona.trim().equals(familyMemberId);
    }
}
