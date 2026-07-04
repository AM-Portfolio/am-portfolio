package com.portfolio.analytics.service;

import com.portfolio.analytics.model.AnalyticsType;
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

import java.time.LocalDate;
import java.time.ZoneId;
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
            // Use the smart cache-first method (Redis -> OHLC fallback -> EOD fallback)
            // This is fast (~1ms on cache hit, max 15s on cold cache with fallback)
            Map<String, MarketData> marketData = marketDataService.getMarketData(symbols);
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
        
        // --- SMART ROUTE: if fromDate == toDate == today, use live data ---
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        if (timeFrameRequest.getFromDate() != null && timeFrameRequest.getFromDate().isEqual(today) &&
            timeFrameRequest.getToDate() != null && timeFrameRequest.getToDate().isEqual(today)) {
            log.info("[SmartRoute] Same-day range detected for {} symbols, routing to cache-enabled Live Data instead of Historical", symbols.size());
            return marketDataService.getMarketData(symbols);
        }
        
        // Resolve potentially null dates using the TimeFrame
        TimeFrameRequest resolvedTf = resolveDates(timeFrameRequest);
        
        log.info("Fetching historical data for {} symbols with time frame: {} to {}, interval: {}", 
                symbols.size(), resolvedTf.getFromDate(), resolvedTf.getToDate(), 
                resolvedTf.getTimeFrame());
        
        try {
            // Create HistoricalDataRequest from TimeFrameRequest
            HistoricalDataRequest request = HistoricalDataRequest.builder()
                    .symbols(String.join(",", symbols))
                    .fromDate(resolvedTf.getFromDate() != null ? resolvedTf.getFromDate().toString() : null)
                    .toDate(resolvedTf.getToDate() != null ? resolvedTf.getToDate().toString() : null)
                    .filterType(FilterType.START_END.getValue())
                    .instrumentType(InstrumentType.EQ.getValue())
                    .continuous(false)
                    .interval(timeFrameRequest.getTimeFrame().getValue())
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

    /**
     * Helper method to calculate fromDate and toDate from timeFrame if they are null
     */
    private TimeFrameRequest resolveDates(TimeFrameRequest tfr) {
        if (tfr == null || tfr.getTimeFrame() == null) return tfr;
        if (tfr.getFromDate() != null && tfr.getToDate() != null) return tfr; // already set
        
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate from;
        switch (tfr.getTimeFrame()) {
            case MINUTE: case THREE_MIN: case FIVE_MIN: 
            case TEN_MIN: case FIFTEEN_MIN: case THIRTY_MIN:
            case HOUR: case FOUR_HOUR:
            case DAY:   from = today.minusDays(5); break;   // 5-day window for intraday/daily
            case WEEK:  from = today.minusWeeks(1); break;
            case MONTH: from = today.minusMonths(1); break;
            case YEAR:  from = today.minusYears(1); break;
            default:    from = today.minusDays(7); break;
        }
        return TimeFrameRequest.builder()
            .fromDate(from)
            .toDate(today)
            .timeFrame(tfr.getTimeFrame())
            .build();
    }
}
