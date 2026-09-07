package com.portfolio.basket.model;

/**
 * Opportunities computation profile.
 * DISCOVER = list/latency path (no bulk live prices).
 * FULL = preview-grade path (prices + full enrich).
 */
public enum OpportunityMode {
    DISCOVER,
    FULL;

    public static OpportunityMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULL;
        }
        try {
            return OpportunityMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FULL;
        }
    }

    public boolean isDiscover() {
        return this == DISCOVER;
    }
}
