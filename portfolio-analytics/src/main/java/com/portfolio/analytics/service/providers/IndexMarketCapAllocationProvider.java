package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.MarketCapType;
import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.MarketCapUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Provider for market cap allocation analytics
 */
@Service
@Slf4j
public class IndexMarketCapAllocationProvider extends AbstractIndexAnalyticsProvider<MarketCapAllocation> {

    public IndexMarketCapAllocationProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(nseIndicesService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.MARKET_CAP_ALLOCATION;
    }

    
    @Override
    public MarketCapAllocation generateAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Generating market cap allocation for index: {}", request.getCoreIdentifiers().getIndexSymbol());
        return generateMarketCapAllocation(request.getCoreIdentifiers().getIndexSymbol(), request.getTimeFrameRequest());
    }

    
    /**
     * Common implementation for generating market cap allocation analytics
     * 
     * @param indexSymbol The index symbol to analyze
     * @param timeFrameRequest Optional time frame parameters (can be null)
     * @return Market cap allocation analytics
     */
    private MarketCapAllocation generateMarketCapAllocation(String indexSymbol, TimeFrameRequest timeFrameRequest) {
        // Default limit for top stocks per segment
        int limit = 5;
        log.info("Generating market cap allocation for index: {} with timeFrame: {}", indexSymbol, timeFrameRequest);
        
        // Get index symbols
        List<String> indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return createEmptyAllocation();
        }
        
        // Fetch market data using AnalyticsUtils
        Map<String, MarketData> marketData = AnalyticsUtils.fetchMarketData(this, indexStockSymbols, timeFrameRequest);
        if (marketData.isEmpty()) {
            log.warn("No market data available for index: {}", indexSymbol);
            return createEmptyAllocation();
        }
        
        // Process market data and create segments using MarketCapUtils
        Map<String, String> symbolToSegment = MarketCapUtils.classifySymbolsByMarketCap(indexStockSymbols, marketData);
        Map<String, Double> stockMarketCaps = MarketCapUtils.calculateMarketCaps(marketData);
        double totalMarketCap = MarketCapUtils.calculateTotalMarketCap(stockMarketCaps);
        
        // Group stocks by segment and calculate allocation
        Map<String, List<MarketData>> segmentMap = MarketCapUtils.groupMarketDataBySegment(marketData, symbolToSegment);
        List<MarketCapAllocation.CapSegment> segments = MarketCapUtils.createSegments(segmentMap, stockMarketCaps, totalMarketCap, indexStockSymbols, marketData, limit);
        
        // Sort segments by weight percentage (highest to lowest)
        segments.sort(Comparator.comparing(MarketCapAllocation.CapSegment::getWeightPercentage).reversed());
        
        log.info("Generated market cap allocation with {} segments for index: {}", segments.size(), indexSymbol);
        
        return MarketCapAllocation.builder()
          
            .timestamp(Instant.now())
            .segments(segments)
            .build();
    }
    
    /**
     * Create an empty allocation when no data is available
     */
    private MarketCapAllocation createEmptyAllocation() {
        return MarketCapAllocation.builder()
          
            .timestamp(Instant.now())
            .segments(Collections.emptyList())
            .build();
    }
    
    // All methods have been moved to MarketCapUtils utility class
}
