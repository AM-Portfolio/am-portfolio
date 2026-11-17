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
    public static final String PRODUCT_MUTUAL_FUND = "MUTUAL_FUND";
    public static final String PRODUCT_UNLISTED = "UNLISTED";
    public static final String PRICE_LIVE = "LIVE";
    public static final String PRICE_LAST = "LAST";
    public static final String PRICE_COST = "COST_BASIS";
    public static final String PRICE_IMPORT = "IMPORT_MARK";

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
        return looksLikeListedEtfTicker(u);
    }

    /**
     * Listed ETF cash tickers (BEES / *ETF). Excludes bare INF scheme codes used as book keys
     * for mutual_fund_demat rows that must not take equity LTP.
     */
    public static boolean looksLikeListedEtfTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String u = symbol.trim().toUpperCase(Locale.ROOT);
        return u.endsWith("BEES")
                || u.contains("ETF")
                || u.endsWith("IETF")
                || (u.endsWith("GOLD") && u.length() <= 12);
    }

    public static boolean isMutualFundDematClass(String assetClass) {
        if (assetClass == null || assetClass.isBlank()) {
            return false;
        }
        String u = assetClass.trim().toUpperCase(Locale.ROOT);
        return u.contains("MUTUAL_FUND_DEMAT")
                || (u.contains("MUTUAL") && u.contains("DEMAT"))
                || u.equals("MUTUAL_FUND");
    }

    /**
     * Scheme MF demat (and unsafe AMC→equity maps) must never take equity cash LTP.
     * Listed ETF tickers / names (NIFTYBEES, MON100 + "…ETF…") still use LTP.
     * Do not blanket-quarantine every mutual_fund_demat row — CDSL stores ETFs under that class.
     */
    public static boolean quarantineEquityLtp(String assetClass, String symbol, String isin, String name) {
        if (isUnsafeNameToTickerMap(name, symbol)) {
            return true;
        }
        if (looksLikeListedEtfTicker(symbol) || nameSuggestsEtf(name)) {
            return false;
        }
        if (!isMutualFundDematClass(assetClass)) {
            return false;
        }
        if (nameSuggestsSchemeMf(name)) {
            return true;
        }
        String id = firstNonBlank(symbol, isin);
        return id != null && id.trim().toUpperCase(Locale.ROOT).startsWith("INF");
    }

    static boolean nameSuggestsEtf(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String u = name.toUpperCase(Locale.ROOT);
        return u.contains("ETF") || u.contains("BEES");
    }

    static boolean nameSuggestsSchemeMf(String name) {
        if (name == null || name.isBlank() || nameSuggestsEtf(name)) {
            return false;
        }
        String u = name.toUpperCase(Locale.ROOT);
        return u.contains("BANDHAN")
                || u.contains("MUTUAL FUND")
                || u.contains("MUTUALFUND");
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
        return productClass(null, symbol, isin, priceSource, null);
    }

    public static String productClass(
            String assetClass, String symbol, String isin, String priceSource, String name) {
        if (looksLikeListedEtfTicker(symbol) || nameSuggestsEtf(name)) {
            return PRODUCT_ETF;
        }
        if (quarantineEquityLtp(assetClass, symbol, isin, name)
                || (isMutualFundDematClass(assetClass) && !nameSuggestsEtf(name))) {
            return PRODUCT_MUTUAL_FUND;
        }
        if (looksLikeEtfOrScheme(symbol, isin)) {
            return PRODUCT_ETF;
        }
        if (looksUnlisted(symbol, isin, priceSource)) {
            return PRODUCT_UNLISTED;
        }
        return PRODUCT_EQUITY;
    }

    /**
     * Reject dangerous name→ticker maps (e.g. Jio Platforms must never become JIOFIN;
     * Bandhan AMC / MF scheme must never become RECLTD equity).
     */
    public static boolean isUnsafeNameToTickerMap(String holdingName, String ticker) {
        if (holdingName == null || ticker == null) {
            return false;
        }
        String name = holdingName.toUpperCase(Locale.ROOT);
        String t = ticker.trim().toUpperCase(Locale.ROOT);
        if (t.isBlank() || looksLikeListedEtfTicker(t)) {
            return false;
        }
        if (name.contains("PLATFORMS") && "JIOFIN".equals(t)) {
            return true;
        }
        if (name.contains("JIO PLATFORMS") && t.startsWith("JIO") && !"JIOPLATFORMS".equals(t)) {
            return true;
        }
        if (nameLooksLikeAmcOrMfScheme(name) && !nameMentionsTicker(name, t)) {
            return true;
        }
        return false;
    }

    static boolean nameLooksLikeAmcOrMfScheme(String nameUpper) {
        if (nameUpper == null || nameUpper.isBlank()) {
            return false;
        }
        // ETF product names often include "AMC LTD" — those are not scheme-MF poison.
        if (nameUpper.contains("ETF") || nameUpper.contains("BEES")) {
            return false;
        }
        return nameUpper.contains("BANDHAN")
                || nameUpper.contains("MUTUAL FUND")
                || nameUpper.contains("MUTUALFUND");
    }

    static boolean nameMentionsTicker(String nameUpper, String tickerUpper) {
        if (nameUpper == null || tickerUpper == null || tickerUpper.length() < 3) {
            return false;
        }
        String compact = nameUpper.replaceAll("[^A-Z0-9]", "");
        return compact.contains(tickerUpper) || nameUpper.contains(tickerUpper);
    }

    /**
     * When market-data cannot map an ISIN, derive a display ticker from broker names.
     * Groww ETF rows often look like {@code TATAAML-TATAGOLD}; unlisted names like
     * {@code JIO PLATFORMS LIMITED} compress to {@code JIOPLATFORMS} (never JIOFIN).
     *
     * <p>Broker series suffixes ({@code -EQ}, {@code EQ}, {@code -BE}) are stripped first so
     * {@code PHYSICSWALLAH-EQ} becomes {@code PHYSICSWALLAH}, not {@code EQ}.
     */
    public static String suggestDisplayTickerFromName(String holdingName) {
        if (holdingName == null || holdingName.isBlank()) {
            return null;
        }
        String raw = stripBrokerSeriesSuffix(holdingName.trim());
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int dash = raw.lastIndexOf('-');
        if (dash >= 0 && dash < raw.length() - 1) {
            String tail = raw.substring(dash + 1).trim().toUpperCase(Locale.ROOT);
            if (isPlausibleDisplayTicker(tail) && !isUnsafeNameToTickerMap(holdingName, tail)) {
                return tail;
            }
        }
        String compact = raw.toUpperCase(Locale.ROOT)
                .replaceAll("\\b(LIMITED|LTD\\.?|PVT\\.?|PRIVATE|LLC|INC\\.?)\\b", " ")
                .replaceAll("[^A-Z0-9]+", "")
                .trim();
        if (isPlausibleDisplayTicker(compact) && !isUnsafeNameToTickerMap(holdingName, compact)) {
            return compact;
        }
        return null;
    }

    /** Strip trailing broker equity series markers before ticker derivation / NAME search. */
    public static String stripBrokerSeriesSuffix(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value
                .replaceAll("(?i)\\s*-\\s*(EQ|BE)\\s*$", "")
                .replaceAll("(?i)\\s+(EQ|BE)\\s*$", "")
                .trim();
    }

    public static boolean isPlausibleDisplayTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return false;
        }
        String t = ticker.trim().toUpperCase(Locale.ROOT);
        if (t.length() < 2 || t.length() > 20) {
            return false;
        }
        if (TradingSymbolResolver.looksLikeIsin(t)) {
            return false;
        }
        return t.matches("^[A-Z][A-Z0-9]*$");
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
