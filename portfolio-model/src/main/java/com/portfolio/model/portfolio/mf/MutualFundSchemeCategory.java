package com.portfolio.model.portfolio.mf;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * SEBI-style mutual fund scheme categories used for heatmap / holdings grouping.
 *
 * <p>Display names are the mosaic parent labels. Broad group is Equity / Debt / Hybrid / Other.
 */
public enum MutualFundSchemeCategory {
    // Equity
    MULTI_CAP("Multi Cap", "Equity"),
    LARGE_CAP("Large Cap", "Equity"),
    LARGE_AND_MID_CAP("Large & Mid Cap", "Equity"),
    MID_CAP("Mid Cap", "Equity"),
    SMALL_CAP("Small Cap", "Equity"),
    FLEXI_CAP("Flexi Cap", "Equity"),
    DIVIDEND_YIELD("Dividend Yield", "Equity"),
    VALUE("Value", "Equity"),
    CONTRA("Contra", "Equity"),
    FOCUSED("Focused", "Equity"),
    SECTORAL("Sectoral", "Equity"),
    THEMATIC("Thematic", "Equity"),
    ELSS("ELSS", "Equity"),

    // Debt
    OVERNIGHT("Overnight", "Debt"),
    LIQUID("Liquid", "Debt"),
    ULTRA_SHORT_DURATION("Ultra Short Duration", "Debt"),
    LOW_DURATION("Low Duration", "Debt"),
    MONEY_MARKET("Money Market", "Debt"),
    SHORT_DURATION("Short Duration", "Debt"),
    MEDIUM_DURATION("Medium Duration", "Debt"),
    MEDIUM_TO_LONG_DURATION("Medium to Long Duration", "Debt"),
    LONG_DURATION("Long Duration", "Debt"),
    DYNAMIC_BOND("Dynamic Bond", "Debt"),
    CORPORATE_BOND("Corporate Bond", "Debt"),
    CREDIT_RISK("Credit Risk", "Debt"),
    BANKING_AND_PSU("Banking and PSU", "Debt"),
    GILT("Gilt", "Debt"),
    FLOATER("Floater", "Debt"),

    // Hybrid
    CONSERVATIVE_HYBRID("Conservative Hybrid", "Hybrid"),
    BALANCED_HYBRID("Balanced Hybrid", "Hybrid"),
    AGGRESSIVE_HYBRID("Aggressive Hybrid", "Hybrid"),
    DYNAMIC_ASSET_ALLOCATION("Dynamic Asset Allocation", "Hybrid"),
    MULTI_ASSET_ALLOCATION("Multi Asset Allocation", "Hybrid"),
    ARBITRAGE("Arbitrage", "Hybrid"),
    EQUITY_SAVINGS("Equity Savings", "Hybrid"),

    // Other / passive / FoF
    INDEX("Index", "Other"),
    FOF_DOMESTIC("FoF Domestic", "Other"),
    FOF_OVERSEAS("FoF Overseas", "Other"),

    UNKNOWN("Other MF", "Other");

    private final String displayName;
    private final String broadGroup;

    MutualFundSchemeCategory(String displayName, String broadGroup) {
        this.displayName = displayName;
        this.broadGroup = broadGroup;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBroadGroup() {
        return broadGroup;
    }

    public static Optional<MutualFundSchemeCategory> fromDisplayName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String n = raw.trim();
        return Arrays.stream(values())
                .filter(c -> c.displayName.equalsIgnoreCase(n) || c.name().equalsIgnoreCase(n))
                .findFirst();
    }

    public static boolean isKnownDisplayName(String raw) {
        return fromDisplayName(raw).filter(c -> c != UNKNOWN).isPresent();
    }

    public static boolean isMutualFundsBucket(String sector) {
        if (sector == null || sector.isBlank()) {
            return false;
        }
        String u = sector.trim().toLowerCase(Locale.ROOT);
        return u.equals("mutual funds") || u.equals("mutual fund") || u.equals("mf");
    }
}
