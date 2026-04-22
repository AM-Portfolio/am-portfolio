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

    public static MarketCapType fromMarketCap(String name) {
        if (name.equals(LARGE_CAP.name)) {
            return LARGE_CAP;
        } else if (name.equals(MID_CAP.name)) {
            return MID_CAP;
        } else if (name.equals(SMALL_CAP.name)) {
            return SMALL_CAP;
        } else {
            return MICRO_CAP;
        }
    }
}

