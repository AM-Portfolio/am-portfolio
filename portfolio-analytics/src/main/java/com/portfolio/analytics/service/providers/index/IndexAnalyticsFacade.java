package com.portfolio.analytics.service.providers.index;

import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.AnalyticsFactory;
import com.portfolio.model.analytics.AnalyticsComponent;
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
    public Heatmap calculateIndexHeatmap(AdvancedAnalyticsRequest request) {
        log.info("Generating sector heatmap for index: {}", request.getCoreIdentifiers().getIndexSymbol());
        return analyticsFactory.generateIndexAnalytics(AnalyticsType.SECTOR_HEATMAP, request);
    }

    
    /**
     * Calculate sector and industry allocation percentages for an index
     * @param indexSymbol The index symbol
     * @return SectorAllocation containing sector and industry weights
     */
    public SectorAllocation calculateIndexSectorAllocations(AdvancedAnalyticsRequest request) {
        log.info("Calculating sector allocations for index: {}", request.getCoreIdentifiers().getIndexSymbol());
        return analyticsFactory.generateIndexAnalytics(AnalyticsType.SECTOR_ALLOCATION, request);
    }
    
    /**
     * Calculate market capitalization allocation for an index
     * @param indexSymbol The index symbol
     * @return MarketCapAllocation containing breakdown by market cap segments
     */
    public MarketCapAllocation calculateIndexMarketCapAllocations(AdvancedAnalyticsRequest request) {
        log.info("Calculating market cap allocations for index: {}", request.getCoreIdentifiers().getIndexSymbol());
        return analyticsFactory.generateIndexAnalytics(AnalyticsType.MARKET_CAP_ALLOCATION, request);
    }

    /**
     * Calculate top gainers and losers for an index
     * @param indexSymbol The index symbol
     * @return GainerLoser containing top gainers and losers
     */
    public GainerLoser calculateIndexTopGainersLosers(AdvancedAnalyticsRequest request) {
        log.info("Calculating top gainers and losers for index: {}", request.getCoreIdentifiers().getIndexSymbol());
        return analyticsFactory.generateIndexAnalytics(AnalyticsType.TOP_MOVERS, request);
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
        
        // Build analytics component with requested features
        AnalyticsComponent.AnalyticsComponentBuilder analyticsBuilder = AnalyticsComponent.builder();
        
        // Include heatmap if requested
        if (request.getFeatureToggles().isIncludeHeatmap()) {
            Heatmap heatmap = calculateIndexHeatmap(request);
            analyticsBuilder.heatmap(heatmap);
        }
        
        // Include top movers if requested
        if (request.getFeatureToggles().isIncludeMovers()) {
            GainerLoser movers = calculateIndexTopGainersLosers(request);
            analyticsBuilder.movers(movers);
        }
        
        // Include sector allocation if requested
        if (request.getFeatureToggles().isIncludeSectorAllocation()) {
            SectorAllocation sectorAllocation = calculateIndexSectorAllocations(request);
            analyticsBuilder.sectorAllocation(sectorAllocation);
        }
        
        // Include market cap allocation if requested
        if (request.getFeatureToggles().isIncludeMarketCapAllocation()) {
            MarketCapAllocation marketCapAllocation = calculateIndexMarketCapAllocations(request);
            analyticsBuilder.marketCapAllocation(marketCapAllocation);
        }
        
        // Add the analytics component to the response
        responseBuilder.analytics(analyticsBuilder.build());
        
        return responseBuilder.build();
    }
}
