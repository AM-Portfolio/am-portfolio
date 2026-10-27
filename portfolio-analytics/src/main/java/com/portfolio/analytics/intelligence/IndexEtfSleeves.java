package com.portfolio.analytics.intelligence;

import java.util.Locale;
import java.util.Set;

/**
 * Ticker heuristics for Stress β sleeves. Listed ETFs stay in NAV vs NIFTY 50;
 * raw index names are excluded; broad Nifty ETFs skip Banking/IT sector shocks.
 */
public final class IndexEtfSleeves {

    private static final Set<String> RAW_INDEX = Set.of(
            "NIFTY",
            "NIFTY50",
            "SENSEX",
            "BANKNIFTY",
            "FINNIFTY",
            "MIDCPNIFTY");

    private static final Set<String> BROAD_NIFTY_ETF = Set.of(
            "NIFTYBEES",
            "SETFNIF50",
            "NIFTYIETF",
            "JUNIORBEES",
            "NIF100BEES",
            "NEXT50",
            "NEXT50BEES",
            "MOM50BEES");

    private IndexEtfSleeves() {
    }

    public static boolean isRawIndexAlias(String symbol) {
        String compact = compact(symbol);
        return compact != null && RAW_INDEX.contains(compact);
    }

    /** Broad Nifty trackers — not a bank even if master sector is Financial Services. */
    public static boolean isBroadNiftyEtf(String symbol) {
        String compact = compact(symbol);
        if (compact == null || compact.isEmpty()) {
            return false;
        }
        if (BROAD_NIFTY_ETF.contains(compact)) {
            return true;
        }
        if (compact.contains("BANK")
                || compact.contains("ITBEES")
                || compact.contains("PHARMA")
                || compact.contains("AUTO")
                || compact.contains("GOLD")
                || compact.contains("PSU")) {
            return false;
        }
        boolean niftyNamed = compact.contains("NIFTY") || compact.startsWith("SETFNIF");
        boolean etfSuffix = compact.contains("BEES") || compact.contains("ETF") || compact.contains("IETF");
        return niftyNamed && etfSuffix;
    }

    private static String compact(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return symbol.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
    }
}
