package com.am.common.amcommondata.model;

public enum MarketCapType {
    LARGE_CAP(1000000000000D, "LARGE_CAP"), // > 1 lakh crore
    MID_CAP(250000000000D, "MID_CAP"),    // 25k to 1 lakh crore
    SMALL_CAP(50000000000D, "SMALL_CAP"),   // 5k to 25k crore
    MICRO_CAP(0D, "MICRO_CAP");      // < 5k crore

    private final double minMarketCapInCrores;
    private final String name;

    MarketCapType(double minMarketCapInCrores, String name) {
        this.minMarketCapInCrores = minMarketCapInCrores;
        this.name = name;
    }

    public double getMinMarketCapInCrores() {
        return minMarketCapInCrores;
    }

    public String getName() {
        return name;
    }

    public static MarketCapType fromMarketCap(double marketCapInCrores) {
        if (marketCapInCrores >= LARGE_CAP.minMarketCapInCrores) {
            return LARGE_CAP;
        } else if (marketCapInCrores >= MID_CAP.minMarketCapInCrores) {
            return MID_CAP;
        } else if (marketCapInCrores >= SMALL_CAP.minMarketCapInCrores) {
            return SMALL_CAP;
        } else {
            return MICRO_CAP;
        }
    }

    /**
     * Parse a labeled cap type. Null/blank/unrecognized → null (do not invent MICRO_CAP).
     * Numeric classification still uses {@link #fromMarketCap(double)}.
     */
    public static MarketCapType fromMarketCap(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalized = name.trim().toUpperCase().replace(' ', '_');
        if (normalized.equals(LARGE_CAP.name) || normalized.contains("LARGE")) {
            return LARGE_CAP;
        }
        if (normalized.equals(MID_CAP.name) || normalized.contains("MID")) {
            return MID_CAP;
        }
        if (normalized.equals(SMALL_CAP.name) || normalized.contains("SMALL")) {
            return SMALL_CAP;
        }
        if (normalized.equals(MICRO_CAP.name) || normalized.contains("MICRO")) {
            return MICRO_CAP;
        }
        return null;
    }
}

