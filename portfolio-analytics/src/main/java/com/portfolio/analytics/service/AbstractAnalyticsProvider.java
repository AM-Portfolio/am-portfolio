package com.portfolio.analytics.service;

import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;

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
     * Generate analytics with time frame parameters
     * @param identifier The identifier (index symbol or portfolio ID)
     * @param timeFrameRequest Time frame parameters (fromDate, toDate, timeFrame)
     * @return Analytics data
     */
    public T generateAnalytics(I identifier, TimeFrameRequest timeFrameRequest) {
        // Default implementation delegates to the simpler method
        // Subclasses should override this if they need to handle time frame parameters
        log.info("Generating analytics for {} with time frame: {} to {}, interval: {}", 
                identifier, timeFrameRequest.getFromDate(), timeFrameRequest.getToDate(), 
                timeFrameRequest.getTimeFrame());
        return generateAnalytics(identifier);
    }
    
    /**
     * Generate analytics with time frame parameters and additional parameters
     * @param identifier The identifier (index symbol or portfolio ID)
     * @param timeFrameRequest Time frame parameters (fromDate, toDate, timeFrame)
     * @param params Additional parameters
     * @return Analytics data
     */
    public T generateAnalytics(I identifier, TimeFrameRequest timeFrameRequest, Object... params) {
        // Default implementation delegates to the time frame method
        // Subclasses should override this if they need to handle additional parameters
        return generateAnalytics(identifier, timeFrameRequest);
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
    protected Map<String, MarketData> getMarketData(List<String> symbols) {
        if (symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
        log.info("Fetching market data for {} symbols", symbols.size());
        try {
            Map<String, MarketData> marketData = marketDataService.getOhlcData(symbols);
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
    
    /**
     * Fetch historical market data for a list of stock symbols with time frame parameters
     * @param symbols List of stock symbols
     * @param timeFrameRequest Time frame parameters (fromDate, toDate, timeFrame)
     * @return Map of symbols to historical data responses
     */
    protected Map<String, MarketData> getHistoricalData(List<String> symbols, TimeFrameRequest timeFrameRequest) {
        if (symbols.isEmpty()) {
            return Collections.emptyMap();
        }
        
        log.info("Fetching historical data for {} symbols with time frame: {} to {}, interval: {}", 
                symbols.size(), timeFrameRequest.getFromDate(), timeFrameRequest.getToDate(), 
                timeFrameRequest.getTimeFrame());
        
        try {
            // Create HistoricalDataRequest from TimeFrameRequest
            HistoricalDataRequest request = HistoricalDataRequest.builder()
                    .symbols(symbols)
                    .fromDate(timeFrameRequest.getFromDate())
                    .toDate(timeFrameRequest.getToDate())
                    // Use the string value from TimeFrame enum
                    .build();
            
            Map<String, MarketData> historicalData = marketDataService.getHistoricalData(request);
            if (historicalData == null) {
                log.warn("Historical data service returned null response");
                return Collections.emptyMap();
            }
            return historicalData;
        } catch (Exception e) {
            log.error("Error fetching historical data: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
}
