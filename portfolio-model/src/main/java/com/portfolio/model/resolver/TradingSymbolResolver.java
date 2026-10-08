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
     * Resolves a batch list of broker/document identifiers (often ISIN) to exchange trading symbols.
     * Overridden by active resolvers to optimize network calls.
     *
     * @param isins List of ISIN codes of securities
     * @return Map mapping ISIN to resolved trading symbol
     */
    default java.util.Map<String, String> resolveTradingSymbols(java.util.List<String> isins) {
        return java.util.Map.of();
    }

    /**
     * Indian equity ISIN only: {@code IN} + 10 alphanumerics (e.g. INE002A01018).
     * Must not match 12-letter tickers like {@code VODAFONEIDEA}.
     */
    static boolean looksLikeIsin(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim().toUpperCase();
        return trimmed.matches("^IN[A-Z0-9]{10}$");
    }
}

