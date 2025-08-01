package com.portfolio.analytics.service;

import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.PaginationRequest;
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
    public abstract T generateAnalytics(I identifier, AdvancedAnalyticsRequest request);
    
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
    public Map<String, MarketData> getMarketData(List<String> symbols) {
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
    public Map<String, MarketData> getHistoricalData(List<String> symbols, TimeFrameRequest timeFrameRequest) {
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
                    .filterType(FilterType.START_END)
                    .instrumentType(InstrumentType.STOCK)
                    .continuous(false)
                    .timeFrame(timeFrameRequest.getTimeFrame())
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
