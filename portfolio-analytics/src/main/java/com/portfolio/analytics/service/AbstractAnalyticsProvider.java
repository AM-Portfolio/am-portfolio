package com.portfolio.analytics.service;

import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Common base class for all analytics providers (index and portfolio)
 * @param <T> The type of analytics data returned
 * @param <I> The type of identifier (String for both index symbol and portfolio ID)
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAnalyticsProvider<T, I> {
    
    protected final MarketDataService marketDataService;
    protected final SecurityDetailsService securityDetailsService;
    
    /**
     * Get the type of analytics this provider handles
     * @return AnalyticsType enum value
     */
    public abstract AnalyticsType getType();
    
    /**
     * Generate analytics for the given identifier
     * @param identifier The identifier (index symbol or portfolio ID)
     * @return Analytics data
     */
    public abstract T generateAnalytics(I identifier);
    
    /**
     * Generate analytics with additional parameters
     * @param identifier The identifier (index symbol or portfolio ID)
     * @param params Additional parameters
     * @return Analytics data
     */
    public T generateAnalytics(I identifier, Object... params) {
        // Default implementation delegates to the simpler method
        // Subclasses should override this if they need to handle additional parameters
        return generateAnalytics(identifier);
    }
    
    /**
     * Get symbols for the given identifier
     * @param identifier The identifier (index symbol or portfolio ID)
     * @return List of stock symbols
     */
    protected abstract List<String> getSymbols(I identifier);
    
    /**
     * Fetch market data for a list of stock symbols
     * @param symbols List of stock symbols
     * @return Map of symbols to market data responses
     */
    protected Map<String, MarketDataResponse> getMarketData(List<String> symbols) {
        if (symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
        log.info("Fetching market data for {} symbols", symbols.size());
        try {
            Map<String, MarketDataResponse> marketData = marketDataService.getOhlcData(symbols);
            if (marketData == null) {
                log.warn("Market data service returned null response");
                return Collections.emptyMap();
            }
            return marketData;
        } catch (Exception e) {
            log.error("Error fetching market data: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
}
