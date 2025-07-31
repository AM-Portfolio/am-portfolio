package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.analytics.service.utils.TopMoverUtils;
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
public class IndexTopMoversProvider extends AbstractIndexAnalyticsProvider<GainerLoser> {

    public IndexTopMoversProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
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

    // Default limit for top movers is defined in TopMoverUtils
    
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
        
        // Calculate performance metrics using utility
        Map<String, Double> symbolToPerformance = new HashMap<>();
        Map<String, Double> symbolToChangePercent = new HashMap<>();
        TopMoverUtils.calculatePerformanceMetrics(marketData, symbolToPerformance, symbolToChangePercent);
        
        // Get top gainers and losers using utility
        List<GainerLoser.StockMovement> gainers = TopMoverUtils.getTopGainers(marketData, symbolToPerformance, symbolToChangePercent, limit);
        List<GainerLoser.StockMovement> losers = TopMoverUtils.getTopLosers(marketData, symbolToPerformance, symbolToChangePercent, limit);
        
        // Calculate sector movements using utility
        List<GainerLoser.SectorMovement> sectorMovements = TopMoverUtils.calculateSectorMovements(
            indexStockSymbols, marketData, symbolToPerformance, symbolToChangePercent, securityDetailsService);
        
        return GainerLoser.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .topGainers(gainers)
            .topLosers(losers)
            .sectorMovements(sectorMovements)
            .build();
    }
    
    /**
     * Extract limit parameter from varargs
     */
    private int extractLimit(Object... params) {
        if (params.length > 0 && params[0] instanceof Integer) {
            return (Integer) params[0];
        }
        return TopMoverUtils.DEFAULT_LIMIT;
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
            .sectorMovements(Collections.emptyList())
            .build();
    }
    
    // Performance metrics, top gainers/losers, and stock movement methods have been moved to TopMoverUtils
}
