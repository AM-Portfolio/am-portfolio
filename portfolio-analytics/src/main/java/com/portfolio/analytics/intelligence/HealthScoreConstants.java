package com.portfolio.analytics.intelligence;

/**
 * Locked MVP constants from docs/feature-portfolio-intelligence/E2E-BACKEND-PLAN.md.
 */
public final class HealthScoreConstants {

    private HealthScoreConstants() {}

    public static final double MIX_DIVERSIFICATION = 0.20;
    public static final double MIX_CONCENTRATION = 0.20;
    public static final double MIX_PERFORMANCE = 0.15;
    public static final double MIX_VOLATILITY = 0.15;
    public static final double MIX_LIQUIDITY = 0.10;
    public static final double MIX_BETA = 0.10;
    public static final double MIX_ALLOCATION = 0.05;
    public static final double MIX_RISK_RESILIENCE = 0.05;

    /** Diversification: nameScore = min(100, holdingsCount * NAME_KNOB) */
    public static final int NAME_KNOB = 8;
    /** Diversification: sectorScore = min(100, distinctSectors * SECTOR_KNOB) */
    public static final int SECTOR_KNOB = 12;

    public static final int MIN_HISTORY_POINTS = 20;

    /** volScore = clamp(100 - dailyVolPct * VOL_MULT, 0, 100) */
    public static final double VOL_MULT = 40.0;

    public static final int FOCUS_THRESHOLD = 70;

    public static final int BAND_CRITICAL_MAX = 39;
    public static final int BAND_WATCH_MAX = 64;
    public static final int BAND_HEALTHY_MAX = 84;

    public static final String BAND_CRITICAL = "Critical";
    public static final String BAND_WATCH = "Watch";
    public static final String BAND_HEALTHY = "Healthy";
    public static final String BAND_STRONG = "Strong";

    public static final String SEVERITY_OK = "OK";
    public static final String SEVERITY_FOCUS = "FOCUS";

    public static final String ID_DIVERSIFICATION = "DIVERSIFICATION";
    public static final String ID_CONCENTRATION = "CONCENTRATION";
    public static final String ID_PERFORMANCE = "PERFORMANCE";
    public static final String ID_VOLATILITY = "VOLATILITY";
    public static final String ID_LIQUIDITY = "LIQUIDITY";
    public static final String ID_BETA = "BETA";
    public static final String ID_ALLOCATION = "ALLOCATION";
    public static final String ID_RISK_RESILIENCE = "RISK_RESILIENCE";

    public static String bandFor(int health) {
        if (health <= BAND_CRITICAL_MAX) {
            return BAND_CRITICAL;
        }
        if (health <= BAND_WATCH_MAX) {
            return BAND_WATCH;
        }
        if (health <= BAND_HEALTHY_MAX) {
            return BAND_HEALTHY;
        }
        return BAND_STRONG;
    }

    public static String severityFor(int score) {
        return score >= FOCUS_THRESHOLD ? SEVERITY_OK : SEVERITY_FOCUS;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int roundInt(double value) {
        return (int) Math.round(value);
    }
}
