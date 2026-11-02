package com.portfolio.model.portfolio;

import com.portfolio.model.resolver.TradingSymbolResolver;

import java.util.Locale;

/**
 * Shared product/sector classification for holdings + heatmap (no HTTP).
 */
public final class HoldingInstrumentClassifier {

    public static final String SECTOR_ETF = "Exchange Traded Funds (ETFs)";
    public static final String INDUSTRY_ETF = "ETFs & Index Funds";
    public static final String SECTOR_UNLISTED = "Private / Unlisted";
    public static final String PRODUCT_EQUITY = "EQUITY";
    public static final String PRODUCT_ETF = "ETF";
    public static final String PRODUCT_UNLISTED = "UNLISTED";
    public static final String PRICE_LIVE = "LIVE";
    public static final String PRICE_LAST = "LAST";
    public static final String PRICE_COST = "COST_BASIS";

    private HoldingInstrumentClassifier() {
    }

    public static boolean isMissingSector(String sector) {
        if (sector == null || sector.isBlank()) {
            return true;
        }
        String t = sector.trim();
        return "-".equals(t) || "Unknown".equalsIgnoreCase(t);
    }

    public static boolean looksLikeEtfOrScheme(String symbol, String isin) {
        if (looksLikeEtfSymbol(symbol)) {
            return true;
        }
        String id = firstNonBlank(isin, symbol);
        if (id == null) {
            return false;
        }
        String u = id.trim().toUpperCase(Locale.ROOT);
        return u.startsWith("INF");
    }

    public static boolean looksLikeEtfSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String u = symbol.trim().toUpperCase(Locale.ROOT);
        if (u.startsWith("INF")) {
            return true;
        }
        return u.endsWith("BEES")
                || u.contains("ETF")
                || u.endsWith("IETF")
                || (u.endsWith("GOLD") && u.length() <= 12);
    }

    /**
     * Unlisted / private: ISIN-shaped book key with no live quote, and not an ETF/scheme.
     */
    public static boolean looksUnlisted(String symbol, String isin, String priceSource) {
        if (looksLikeEtfOrScheme(symbol, isin)) {
            return false;
        }
        boolean isinShaped = TradingSymbolResolver.looksLikeIsin(symbol)
                || TradingSymbolResolver.looksLikeIsin(isin);
        if (!isinShaped) {
            return false;
        }
        return PRICE_COST.equals(priceSource) || priceSource == null;
    }

    public static String resolveSector(String currentSector, String symbol, String isin, String priceSource) {
        if (!isMissingSector(currentSector)) {
            return currentSector.trim();
        }
        if (looksLikeEtfOrScheme(symbol, isin)) {
            return SECTOR_ETF;
        }
        if (looksUnlisted(symbol, isin, priceSource)) {
            return SECTOR_UNLISTED;
        }
        return currentSector;
    }

    public static String resolveIndustry(String currentIndustry, String symbol, String isin, String priceSource) {
        if (!isMissingSector(currentIndustry)) {
            return currentIndustry.trim();
        }
        if (looksLikeEtfOrScheme(symbol, isin)) {
            return INDUSTRY_ETF;
        }
        if (looksUnlisted(symbol, isin, priceSource)) {
            return SECTOR_UNLISTED;
        }
        return currentIndustry;
    }

    public static String productClass(String symbol, String isin, String priceSource) {
        if (looksLikeEtfOrScheme(symbol, isin)) {
            return PRODUCT_ETF;
        }
        if (looksUnlisted(symbol, isin, priceSource)) {
            return PRODUCT_UNLISTED;
        }
        return PRODUCT_EQUITY;
    }

    /**
     * Reject dangerous name→ticker maps (e.g. Jio Platforms must never become JIOFIN).
     */
    public static boolean isUnsafeNameToTickerMap(String holdingName, String ticker) {
        if (holdingName == null || ticker == null) {
            return false;
        }
        String name = holdingName.toUpperCase(Locale.ROOT);
        String t = ticker.trim().toUpperCase(Locale.ROOT);
        if (name.contains("PLATFORMS") && "JIOFIN".equals(t)) {
            return true;
        }
        if (name.contains("JIO PLATFORMS") && t.startsWith("JIO") && !"JIOPLATFORMS".equals(t)) {
            return true;
        }
        return false;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
