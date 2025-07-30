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

    @Override
    public GainerLoser generateAnalytics(String indexSymbol, Object... params) {
        // Extract limit parameter if provided
        int limit = 5;
        if (params.length > 0 && params[0] instanceof Integer) {
            limit = (Integer) params[0];
        }
        
        log.info("Getting top {} gainers and losers for index: {}", limit, indexSymbol);
        
        var indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return GainerLoser.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .topGainers(Collections.emptyList())
                .topLosers(Collections.emptyList())
                .build();
        }
        
        var marketData = getMarketData(indexStockSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for index: {}", indexSymbol);
            return GainerLoser.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .topGainers(Collections.emptyList())
                .topLosers(Collections.emptyList())
                .build();
        }
        
        // Calculate performance for each stock
        Map<String, Double> symbolToPerformance = new HashMap<>();
        Map<String, Double> symbolToChangePercent = new HashMap<>();
        
        for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketData data = entry.getValue();
            
            double closePrice = data.getOhlc().getClose();
            double openPrice = data.getOhlc().getOpen();
            double lastPrice = data.getLastPrice();
            
            if (openPrice > 0) {
                double changePercent = ((lastPrice - openPrice) / openPrice) * 100;
                symbolToChangePercent.put(symbol, changePercent);
                
                // Performance score based on price movement relative to previous close
                double performanceScore = ((lastPrice - closePrice) / closePrice) * 100;
                symbolToPerformance.put(symbol, performanceScore);
            }
        }
        
        // Sort stocks by performance and get top gainers and losers
        List<Map.Entry<String, Double>> sortedByPerformance = new ArrayList<>(symbolToPerformance.entrySet());
        sortedByPerformance.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        
        // Get top gainers
        List<GainerLoser.StockMovement> gainers = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sortedByPerformance.size()); i++) {
            Map.Entry<String, Double> entry = sortedByPerformance.get(i);
            String symbol = entry.getKey();
            double performance = entry.getValue();
            double changePercent = symbolToChangePercent.getOrDefault(symbol, 0.0);
            
            if (performance <= 0) {
                break; // Stop if we reach non-gainers
            }
            
            gainers.add(GainerLoser.StockMovement.builder()
                .symbol(symbol)
                .changePercent(changePercent)
                .lastPrice(marketData.get(symbol).getLastPrice())
                .build());
        }
        
        // Get top losers (reverse sort)
        Collections.reverse(sortedByPerformance);
        List<GainerLoser.StockMovement> losers = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sortedByPerformance.size()); i++) {
            Map.Entry<String, Double> entry = sortedByPerformance.get(i);
            String symbol = entry.getKey();
            double performance = entry.getValue();
            double changePercent = symbolToChangePercent.getOrDefault(symbol, 0.0);
            
            if (performance >= 0) {
                break; // Stop if we reach non-losers
            }
            
            losers.add(GainerLoser.StockMovement.builder()
                .symbol(symbol)
                .changePercent(changePercent)
                .lastPrice(marketData.get(symbol).getLastPrice())
                .build());
        }
        
        return GainerLoser.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .topGainers(gainers)
            .topLosers(losers)
            .build();
    }
}
