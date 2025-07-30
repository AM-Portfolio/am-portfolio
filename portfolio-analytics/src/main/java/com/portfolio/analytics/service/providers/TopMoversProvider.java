package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.market.MarketData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provider for top movers (gainers and losers) analytics
 */
@Service
@Slf4j
public class TopMoversProvider extends AbstractIndexAnalyticsProvider<GainerLoser> {

    public TopMoversProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(nseIndicesService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.TOP_MOVERS;
    }

    @Override
    public GainerLoser generateAnalytics(String indexSymbol) {
        // Default to 5 top movers
        return generateAnalytics(indexSymbol, 5);
    }

    // Default limit for top movers
    private static final int DEFAULT_LIMIT = 5;
    
    @Override
    public GainerLoser generateAnalytics(String indexSymbol, Object... params) {
        // Extract limit parameter if provided
        int limit = extractLimit(params);
        
        log.info("Getting top {} gainers and losers for index: {}", limit, indexSymbol);
        
        // Get index symbols and market data
        List<String> indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            return createEmptyResult(indexSymbol);
        }
        
        Map<String, MarketData> marketData = getMarketData(indexStockSymbols);
        if (marketData.isEmpty()) {
            return createEmptyResult(indexSymbol);
        }
        
        // Calculate performance metrics
        Map<String, Double> symbolToPerformance = new HashMap<>();
        Map<String, Double> symbolToChangePercent = new HashMap<>();
        calculatePerformanceMetrics(marketData, symbolToPerformance, symbolToChangePercent);
        
        // Get top gainers and losers
        List<GainerLoser.StockMovement> gainers = getTopGainers(marketData, symbolToPerformance, symbolToChangePercent, limit);
        List<GainerLoser.StockMovement> losers = getTopLosers(marketData, symbolToPerformance, symbolToChangePercent, limit);
        
        return GainerLoser.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .topGainers(gainers)
            .topLosers(losers)
            .build();
    }
    
    /**
     * Extract limit parameter from varargs
     */
    private int extractLimit(Object... params) {
        if (params.length > 0 && params[0] instanceof Integer) {
            return (Integer) params[0];
        }
        return DEFAULT_LIMIT;
    }
    
    /**
     * Create empty result when no data is available
     */
    private GainerLoser createEmptyResult(String indexSymbol) {
        return GainerLoser.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .topGainers(Collections.emptyList())
            .topLosers(Collections.emptyList())
            .build();
    }
    
    /**
     * Calculate performance metrics for each stock
     */
    private void calculatePerformanceMetrics(
            Map<String, MarketData> marketData, 
            Map<String, Double> symbolToPerformance, 
            Map<String, Double> symbolToChangePercent) {
        
        for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketData data = entry.getValue();
            
            double closePrice = data.getOhlc().getClose();
            double openPrice = data.getOhlc().getOpen();
            double lastPrice = data.getLastPrice();
            
            if (openPrice > 0) {
                // Calculate intraday change percentage
                double changePercent = ((lastPrice - openPrice) / openPrice) * 100;
                symbolToChangePercent.put(symbol, changePercent);
                
                // Performance score based on price movement relative to previous close
                double performanceScore = ((lastPrice - closePrice) / closePrice) * 100;
                symbolToPerformance.put(symbol, performanceScore);
            }
        }
    }
    
    /**
     * Get top gainers based on performance metrics
     */
    private List<GainerLoser.StockMovement> getTopGainers(
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance, 
            Map<String, Double> symbolToChangePercent, 
            int limit) {
        
        // Sort stocks by performance (descending)
        List<String> topPerformers = symbolToPerformance.entrySet().stream()
            .filter(entry -> entry.getValue() > 0) // Only positive performers
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Create stock movement objects
        return createStockMovements(topPerformers, marketData, symbolToChangePercent);
    }
    
    /**
     * Get top losers based on performance metrics
     */
    private List<GainerLoser.StockMovement> getTopLosers(
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance, 
            Map<String, Double> symbolToChangePercent, 
            int limit) {
        
        // Sort stocks by performance (ascending)
        List<String> worstPerformers = symbolToPerformance.entrySet().stream()
            .filter(entry -> entry.getValue() < 0) // Only negative performers
            .sorted(Map.Entry.comparingByValue()) // Ascending order for worst performers
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Create stock movement objects
        return createStockMovements(worstPerformers, marketData, symbolToChangePercent);
    }
    
    /**
     * Create stock movement objects for a list of symbols
     */
    private List<GainerLoser.StockMovement> createStockMovements(
            List<String> symbols, 
            Map<String, MarketData> marketData, 
            Map<String, Double> symbolToChangePercent) {
        
        return symbols.stream()
            .map(symbol -> GainerLoser.StockMovement.builder()
                .symbol(symbol)
                .changePercent(symbolToChangePercent.getOrDefault(symbol, 0.0))
                .lastPrice(marketData.get(symbol).getLastPrice())
                .build())
            .collect(Collectors.toList());
    }
}
