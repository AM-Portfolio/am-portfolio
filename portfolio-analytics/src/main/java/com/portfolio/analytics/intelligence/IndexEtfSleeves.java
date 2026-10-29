package com.portfolio.analytics.intelligence;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticker heuristics for Stress β sleeves. Listed ETFs stay in NAV vs NIFTY 50;
 * raw index names are excluded; broad Nifty ETFs skip Banking/IT sector shocks.
 *
 * Sleeve ticker lists come from {@link StressConfigService} (Mongo / YAML seed) —
 * no hardcoded DEFAULT sets in Java.
 */
public final class IndexEtfSleeves {

    private static final Set<String> CONFIGURED_RAW_INDEX = ConcurrentHashMap.newKeySet();
    private static final Set<String> CONFIGURED_BROAD_NIFTY_ETF = ConcurrentHashMap.newKeySet();

    private IndexEtfSleeves() {
    }

    /** Append raw index tickers (additive). Prefer {@link #replaceRawIndex} on reload. */
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

    /** Append broad Nifty ETF tickers (additive). Prefer {@link #replaceBroadNiftyEtf} on reload. */
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

    /** Replace raw-index registry (Mongo reload / deactivated rows). */
    public static void replaceRawIndex(Collection<String> symbols) {
        CONFIGURED_RAW_INDEX.clear();
        registerRawIndex(symbols);
    }

    /** Replace broad-ETF registry (Mongo reload / deactivated rows). */
    public static void replaceBroadNiftyEtf(Collection<String> symbols) {
        CONFIGURED_BROAD_NIFTY_ETF.clear();
        registerBroadNiftyEtf(symbols);
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
        if (CONFIGURED_RAW_INDEX.contains(compact)) {
            return true;
        }
        if (compact.contains("BEES") || compact.contains("ETF") || compact.contains("IETF")) {
            return false;
        }
        return compact.endsWith("INDEX") || compact.endsWith("TRI");
    }

    /** Broad Nifty trackers — not a bank even if master sector is Financial Services. */
    public static boolean isBroadNiftyEtf(String symbol) {
        String compact = compact(symbol);
        if (compact == null || compact.isEmpty()) {
            return false;
        }
        if (CONFIGURED_BROAD_NIFTY_ETF.contains(compact)) {
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
