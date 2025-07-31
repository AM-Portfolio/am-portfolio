package com.portfolio.analytics.service;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.TimeFrameRequest;
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
     * Generate sector heatmap for an index with time frame parameters
     * @param indexSymbol The index symbol to generate heatmap for
     * @param timeFrameRequest Time frame parameters (fromDate, toDate, timeFrame)
     * @return Heatmap containing sector performances
     */
    public Heatmap generateSectorHeatmap(String indexSymbol, TimeFrameRequest timeFrameRequest) {
        log.info("Generating sector heatmap for index: {} with time frame: {} to {}, interval: {}", 
                indexSymbol, timeFrameRequest.getFromDate(), timeFrameRequest.getToDate(), 
                timeFrameRequest.getTimeFrame());
        
        return analyticsFactory.generateAnalytics(AnalyticsType.SECTOR_HEATMAP, indexSymbol, timeFrameRequest);
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
     * Get top gainers and losers for an index with time frame parameters
     * @param indexSymbol The index symbol
     * @param limit Number of top gainers/losers to return
     * @param timeFrameRequest Time frame parameters (fromDate, toDate, timeFrame)
     * @return GainerLoser object containing top performers and underperformers
     */
    public GainerLoser getTopGainersLosers(String indexSymbol, int limit, TimeFrameRequest timeFrameRequest) {
        log.info("Getting top {} gainers and losers for index: {} with time frame: {} to {}, interval: {}", 
                limit, indexSymbol, timeFrameRequest.getFromDate(), timeFrameRequest.getToDate(), 
                timeFrameRequest.getTimeFrame());
        
        return analyticsFactory.generateAnalytics(AnalyticsType.TOP_MOVERS, indexSymbol, timeFrameRequest, limit);
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
     * Calculate sector and industry allocation percentages for an index with time frame parameters
     * @param indexSymbol The index symbol
     * @param timeFrameRequest Time frame parameters (fromDate, toDate, timeFrame)
     * @return SectorAllocation containing sector and industry weights
     */
    public SectorAllocation calculateSectorAllocations(String indexSymbol, TimeFrameRequest timeFrameRequest) {
        log.info("Calculating sector allocations for index: {} with time frame: {} to {}, interval: {}", 
                indexSymbol, timeFrameRequest.getFromDate(), timeFrameRequest.getToDate(), 
                timeFrameRequest.getTimeFrame());
        
        return analyticsFactory.generateAnalytics(AnalyticsType.SECTOR_ALLOCATION, indexSymbol, timeFrameRequest);
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
     * Calculate market capitalization allocation for an index with time frame parameters
     * @param indexSymbol The index symbol
     * @param timeFrameRequest Time frame parameters (fromDate, toDate, timeFrame)
     * @return MarketCapAllocation containing breakdown by market cap segments
     */
    public MarketCapAllocation calculateMarketCapAllocations(String indexSymbol, TimeFrameRequest timeFrameRequest) {
        log.info("Calculating market cap allocations for index: {} with time frame: {} to {}, interval: {}", 
                indexSymbol, timeFrameRequest.getFromDate(), timeFrameRequest.getToDate(), 
                timeFrameRequest.getTimeFrame());
        
        return analyticsFactory.generateAnalytics(AnalyticsType.MARKET_CAP_ALLOCATION, indexSymbol, timeFrameRequest);
    }
    
    /**
     * Calculate advanced analytics combining multiple data points based on request parameters
     * @param request The advanced analytics request containing parameters and flags
     * @return AdvancedAnalyticsResponse with requested analytics components
     */
    public AdvancedAnalyticsResponse calculateAdvancedAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Calculating advanced analytics for index: {} from {} to {}", 
                request.getCoreIdentifiers().getIndexSymbol(), request.getFromDate(), request.getToDate());
        
        // Start building the response
        AdvancedAnalyticsResponse.AdvancedAnalyticsResponseBuilder responseBuilder = AdvancedAnalyticsResponse.builder()
                .indexSymbol(request.getCoreIdentifiers().getIndexSymbol())
                .startDate(request.getFromDate())
                .endDate(request.getToDate())
                .timestamp(java.time.Instant.now());
        
        // Add comparison index if provided
        if (request.getCoreIdentifiers().getComparisonIndexSymbol() != null && !request.getCoreIdentifiers().getComparisonIndexSymbol().isEmpty()) {
            responseBuilder.comparisonIndexSymbol(request.getCoreIdentifiers().getComparisonIndexSymbol());
        }
        
        // Include heatmap if requested
        if (request.getFeatureToggles().isIncludeHeatmap()) {
            Heatmap heatmap = generateSectorHeatmap(request.getCoreIdentifiers().getIndexSymbol());
            responseBuilder.heatmap(heatmap);
        }
        
        // Include top movers if requested
        if (request.getFeatureToggles().isIncludeMovers()) {
            int limit = request.getFeatureConfiguration().getMoversLimit() != null && request.getFeatureConfiguration().getMoversLimit() > 0 
                    ? request.getFeatureConfiguration().getMoversLimit() : 5; // Default to 5 if not specified
            GainerLoser movers = getTopGainersLosers(request.getCoreIdentifiers().getIndexSymbol(), limit);
            responseBuilder.movers(movers);
        }
        
        // Include sector allocation if requested
        if (request.getFeatureToggles().isIncludeSectorAllocation()) {
            SectorAllocation sectorAllocation = calculateSectorAllocations(request.getCoreIdentifiers().getIndexSymbol());
            responseBuilder.sectorAllocation(sectorAllocation);
        }
        
        // Include market cap allocation if requested
        if (request.getFeatureToggles().isIncludeMarketCapAllocation()) {
            MarketCapAllocation marketCapAllocation = calculateMarketCapAllocations(request.getCoreIdentifiers().getIndexSymbol());
            responseBuilder.marketCapAllocation(marketCapAllocation);
        }
        
        return responseBuilder.build();
    }
}
