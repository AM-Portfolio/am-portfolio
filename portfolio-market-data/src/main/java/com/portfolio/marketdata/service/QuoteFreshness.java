package com.portfolio.marketdata.service;

/**
 * Controls how aggressively {@link MarketDataService#getMarketData} prefers
 * exchange OHLC over in-pod / Redis / Mongo ticks.
 */
public enum QuoteFreshness {
    /** Default waterfall: L1/Redis (cash open) → Mongo → OHLC. */
    NORMAL,
    /**
     * Live/1D heatmap path: after close, OHLC first (skip L1/Redis/Mongo);
     * during cash hours, skip L1 so mosaic tracks Redis tape.
     */
    SESSION_CLOSE
}
