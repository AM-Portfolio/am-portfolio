package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.analytics.service.utils.TopMoverUtils;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
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

    // Default limit for top movers is defined in TopMoverUtils
    
    @Override
    public GainerLoser generateAnalytics(String indexSymbol, AdvancedAnalyticsRequest request) {
        log.info("Getting top {} gainers and losers for index: {}", request.getFeatureConfiguration().getMoversLimit(), indexSymbol);

        Integer limit = request.getFeatureConfiguration().getMoversLimit();
        
        // Get index symbols and market data
        List<String> indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            return createEmptyResult(indexSymbol);
        }
        
        Map<String, MarketData> marketData = getMarketData(indexStockSymbols);
        if (marketData.isEmpty()) {
            return createEmptyResult(indexSymbol);
        }
        
        // Use TopMoverUtils to build the response with top gainers and losers
        GainerLoser gainerLoser = TopMoverUtils.buildTopMoversResponse(marketData, limit, indexSymbol, false);
        
        // Create maps for performance metrics (already calculated in buildTopMoversResponse)
        Map<String, Double> symbolToPerformance = new HashMap<>();
        Map<String, Double> symbolToChangePercent = new HashMap<>();
        
        // Calculate performance metrics to use for sector movements
        TopMoverUtils.calculatePerformanceMetrics(marketData, symbolToPerformance, symbolToChangePercent);
        
        // Calculate sector movements using utility
        List<GainerLoser.SectorMovement> sectorMovements = TopMoverUtils.calculateSectorMovements(
            indexStockSymbols, marketData, symbolToPerformance, symbolToChangePercent, securityDetailsService, limit);
        
        // Add sector movements to the response
        return GainerLoser.builder()
            .indexSymbol(indexSymbol)
            .timestamp(gainerLoser.getTimestamp())
            .topGainers(gainerLoser.getTopGainers())
            .topLosers(gainerLoser.getTopLosers())
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
        return 5; // Default limit
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

    @Override
    public GainerLoser generateAnalytics(AdvancedAnalyticsRequest request) {
        return generateAnalytics(request.getCoreIdentifiers().getIndexSymbol(), request);
    }
    
    // Performance metrics, top gainers/losers, and stock movement methods have been moved to TopMoverUtils
}
