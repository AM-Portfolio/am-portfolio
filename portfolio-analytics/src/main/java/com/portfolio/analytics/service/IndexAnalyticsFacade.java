package com.portfolio.analytics.service;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Facade service for index analytics that delegates to the appropriate providers
 * This service replaces the original IndexAnalyticsService with a more scalable design
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexAnalyticsFacade {
    
    private final AnalyticsFactory analyticsFactory;
    
    /**
     * Generate sector heatmap for an index
     * @param indexSymbol The index symbol to generate heatmap for
     * @return Heatmap containing sector performances
     */
    public Heatmap generateSectorHeatmap(String indexSymbol) {
        log.info("Generating sector heatmap for index: {}", indexSymbol);
        return analyticsFactory.generateAnalytics(AnalyticsType.SECTOR_HEATMAP, indexSymbol);
    }
    
    /**
     * Get top gainers and losers for an index
     * @param indexSymbol The index symbol
     * @param limit Number of top gainers/losers to return
     * @return GainerLoser object containing top performers and underperformers
     */
    public GainerLoser getTopGainersLosers(String indexSymbol, int limit) {
        log.info("Getting top {} gainers and losers for index: {}", limit, indexSymbol);
        return analyticsFactory.generateAnalytics(AnalyticsType.TOP_MOVERS, indexSymbol, limit);
    }
    
    /**
     * Calculate sector and industry allocation percentages for an index
     * @param indexSymbol The index symbol
     * @return SectorAllocation containing sector and industry weights
     */
    public SectorAllocation calculateSectorAllocations(String indexSymbol) {
        log.info("Calculating sector allocations for index: {}", indexSymbol);
        return analyticsFactory.generateAnalytics(AnalyticsType.SECTOR_ALLOCATION, indexSymbol);
    }
    
    /**
     * Calculate market capitalization allocation for an index
     * @param indexSymbol The index symbol
     * @return MarketCapAllocation containing breakdown by market cap segments
     */
    public MarketCapAllocation calculateMarketCapAllocations(String indexSymbol) {
        log.info("Calculating market cap allocations for index: {}", indexSymbol);
        return analyticsFactory.generateAnalytics(AnalyticsType.MARKET_CAP_ALLOCATION, indexSymbol);
    }
    
    /**
     * Calculate advanced analytics combining multiple data points based on request parameters
     * @param request The advanced analytics request containing parameters and flags
     * @return AdvancedAnalyticsResponse with requested analytics components
     */
    public AdvancedAnalyticsResponse calculateAdvancedAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Calculating advanced analytics for index: {} from {} to {}", 
                request.getIndexSymbol(), request.getStartDate(), request.getEndDate());
        
        // Start building the response
        AdvancedAnalyticsResponse.AdvancedAnalyticsResponseBuilder responseBuilder = AdvancedAnalyticsResponse.builder()
                .indexSymbol(request.getIndexSymbol())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .timestamp(java.time.Instant.now());
        
        // Add comparison index if provided
        if (request.getComparisonIndexSymbol() != null && !request.getComparisonIndexSymbol().isEmpty()) {
            responseBuilder.comparisonIndexSymbol(request.getComparisonIndexSymbol());
        }
        
        // Include heatmap if requested
        if (request.isIncludeHeatmap()) {
            Heatmap heatmap = generateSectorHeatmap(request.getIndexSymbol());
            responseBuilder.heatmap(heatmap);
        }
        
        // Include top movers if requested
        if (request.isIncludeMovers()) {
            int limit = request.getMoversLimit() > 0 ? request.getMoversLimit() : 5; // Default to 5 if not specified
            GainerLoser movers = getTopGainersLosers(request.getIndexSymbol(), limit);
            responseBuilder.movers(movers);
        }
        
        // Include sector allocation if requested
        if (request.isIncludeSectorAllocation()) {
            SectorAllocation sectorAllocation = calculateSectorAllocations(request.getIndexSymbol());
            responseBuilder.sectorAllocation(sectorAllocation);
        }
        
        // Include market cap allocation if requested
        if (request.isIncludeMarketCapAllocation()) {
            MarketCapAllocation marketCapAllocation = calculateMarketCapAllocations(request.getIndexSymbol());
            responseBuilder.marketCapAllocation(marketCapAllocation);
        }
        
        return responseBuilder.build();
    }
}
