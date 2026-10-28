package com.portfolio.analytics.intelligence;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticker heuristics for Stress β sleeves. Listed ETFs stay in NAV vs NIFTY 50;
 * raw index names are excluded; broad Nifty ETFs skip Banking/IT sector shocks.
 *
 * Configurable dynamically via {@link StressProperties} or direct registration.
 */
public final class IndexEtfSleeves {

    private static final Set<String> DEFAULT_RAW_INDEX = Set.of(
            "NIFTY",
            "NIFTY50",
            "SENSEX",
            "BANKNIFTY",
            "FINNIFTY",
            "MIDCPNIFTY",
            "NIFTYMIDCAP50",
            "NIFTYMIDCAP100",
            "NIFTYMIDCAP150",
            "NIFTY100",
            "NIFTY200",
            "NIFTY500",
            "BSE500");

    private static final Set<String> DEFAULT_BROAD_NIFTY_ETF = Set.of(
            "NIFTYBEES",
            "SETFNIF50",
            "NIFTYIETF",
            "JUNIORBEES",
            "NIF100BEES",
            "NEXT50",
            "NEXT50BEES",
            "MOM50BEES");

    private static final Set<String> CONFIGURED_RAW_INDEX = ConcurrentHashMap.newKeySet();
    private static final Set<String> CONFIGURED_BROAD_NIFTY_ETF = ConcurrentHashMap.newKeySet();

    private IndexEtfSleeves() {
    }

    /**
     * Register additional raw index tickers at runtime (e.g. loaded from application.yml).
     */
    public static void registerRawIndex(Collection<String> symbols) {
        if (symbols == null) {
            return;
        }
        for (String s : symbols) {
            String c = compact(s);
            if (c != null) {
                CONFIGURED_RAW_INDEX.add(c);
            }
        }
    }

    /**
     * Register additional broad Nifty ETF tickers at runtime (e.g. loaded from application.yml).
     */
    public static void registerBroadNiftyEtf(Collection<String> symbols) {
        if (symbols == null) {
            return;
        }
        for (String s : symbols) {
            String c = compact(s);
            if (c != null) {
                CONFIGURED_BROAD_NIFTY_ETF.add(c);
            }
        }
    }

    /** Visible for testing. */
    static void resetConfiguredForTesting() {
        CONFIGURED_RAW_INDEX.clear();
        CONFIGURED_BROAD_NIFTY_ETF.clear();
    }

    public static boolean isRawIndexAlias(String symbol) {
        String compact = compact(symbol);
        if (compact == null) {
            return false;
        }
        if (DEFAULT_RAW_INDEX.contains(compact) || CONFIGURED_RAW_INDEX.contains(compact)) {
            return true;
        }
        // ETFs are not raw index aliases
        if (compact.contains("BEES") || compact.contains("ETF") || compact.contains("IETF")) {
            return false;
        }
        // Generic index suffix/prefix matching for NSE/BSE indices
        return compact.endsWith("INDEX") || compact.endsWith("TRI");
    }

    /** Broad Nifty trackers — not a bank even if master sector is Financial Services. */
    public static boolean isBroadNiftyEtf(String symbol) {
        String compact = compact(symbol);
        if (compact == null || compact.isEmpty()) {
            return false;
        }
        if (DEFAULT_BROAD_NIFTY_ETF.contains(compact) || CONFIGURED_BROAD_NIFTY_ETF.contains(compact)) {
            return true;
        }
        if (compact.contains("BANK")
                || compact.contains("ITBEES")
                || compact.contains("PHARMA")
                || compact.contains("AUTO")
                || compact.contains("GOLD")
                || compact.contains("SILVER")
                || compact.contains("COMMODITY")
                || compact.contains("PSU")) {
            return false;
        }
        boolean niftyNamed = compact.contains("NIFTY") || compact.startsWith("SETFNIF");
        boolean etfSuffix = compact.contains("BEES") || compact.contains("ETF") || compact.contains("IETF");
        return niftyNamed && etfSuffix;
    }

    public static String compact(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return symbol.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "").replace("_", "");
    }
}
