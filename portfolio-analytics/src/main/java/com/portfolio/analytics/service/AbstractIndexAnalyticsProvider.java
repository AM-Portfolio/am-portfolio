package com.portfolio.analytics.service;

import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.marketdata.model.indices.IndexConstituent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for index analytics providers
 * @param <T> The type of analytics data returned
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractIndexAnalyticsProvider<T> implements AnalyticsProvider<T> {
    
    protected final NseIndicesService nseIndicesService;
    protected final MarketDataService marketDataService;
    
    /**
     * Fetch all stock symbols for a given index
     * @param indexSymbol The index symbol to fetch stock symbols for
     * @return List of stock symbols in the index
     */
    protected List<String> getIndexSymbols(String indexSymbol) {
        log.info("Fetching symbols for index: {}", indexSymbol);
        try {
            var indexConstituents = nseIndicesService.getIndexConstituents(indexSymbol);
            if (indexConstituents != null) {
                log.info("Found {} symbols for index {}", indexConstituents.size(), indexSymbol);
                return indexConstituents.stream()
                    .map(IndexConstituent::getSymbol)
                    .toList();
            } else {
                log.warn("No data found for index: {}", indexSymbol);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching symbols for index: {}", indexSymbol, e);
            return Collections.emptyList();
        }
    }
    
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
    
    @Override
    public T generateAnalytics(String symbol, Object... params) {
        // Default implementation delegates to the simpler method
        // Subclasses should override this if they need to handle additional parameters
        return generateAnalytics(symbol);
    }
}
