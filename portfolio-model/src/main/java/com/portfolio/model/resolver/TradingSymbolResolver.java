package com.portfolio.model.resolver;

/**
 * Resolves broker/document identifiers (often ISIN) to exchange trading symbols (e.g. RELIANCE).
 *
 * <p>Used on every portfolio save path so Mongo never stores INE… as the primary symbol when
 * a ticker can be resolved from {@code market_data.upstock_instruments}.
 */
public interface TradingSymbolResolver {

    /**
     * @param symbol raw symbol from broker (may be ticker, ISIN, or prefixed exchange code)
     * @param isin   explicit ISIN when provided separately from symbol
     * @return trading ticker when resolvable; otherwise the best available identifier
     */
    String resolveTradingSymbol(String symbol, String isin);

    /**
     * Indian equity ISIN: 12 chars, starts with two letters (e.g. INE002A01018).
     */
    static boolean looksLikeIsin(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim().toUpperCase();
        return trimmed.length() == 12 && trimmed.matches("[A-Z]{2}[A-Z0-9]{10}");
    }
}
