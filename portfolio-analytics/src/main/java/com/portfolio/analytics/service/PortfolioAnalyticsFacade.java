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
 * Facade service for portfolio analytics that delegates to the appropriate providers
 * This service follows the same pattern as IndexAnalyticsFacade but for portfolio-specific analytics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalyticsFacade {
    
    private final AnalyticsFactory analyticsFactory;
    
    /**
     * Generate sector heatmap for a portfolio
     * @param portfolioId The portfolio ID to generate heatmap for
     * @return Heatmap containing sector performances
     */
    public Heatmap generateSectorHeatmap(String portfolioId) {
        log.info("Generating sector heatmap for portfolio: {}", portfolioId);
        return analyticsFactory.generatePortfolioAnalytics(AnalyticsType.SECTOR_HEATMAP, portfolioId);
    }
    
    /**
     * Get top gainers and losers for a portfolio
     * @param portfolioId The portfolio ID
     * @param limit Number of top gainers/losers to return
     * @return GainerLoser object containing top performers and underperformers
     */
    public GainerLoser getTopGainersLosers(String portfolioId, int limit) {
        log.info("Getting top {} gainers and losers for portfolio: {}", limit, portfolioId);
        return analyticsFactory.generatePortfolioAnalytics(AnalyticsType.TOP_MOVERS, portfolioId, limit);
    }
    
    /**
     * Calculate sector and industry allocation percentages for a portfolio
     * @param portfolioId The portfolio ID
     * @return SectorAllocation containing sector and industry weights
     */
    public SectorAllocation calculateSectorAllocations(String portfolioId) {
        log.info("Calculating sector allocations for portfolio: {}", portfolioId);
        return analyticsFactory.generatePortfolioAnalytics(AnalyticsType.SECTOR_ALLOCATION, portfolioId);
    }
    
    /**
     * Calculate market capitalization allocation for a portfolio
     * @param portfolioId The portfolio ID
     * @return MarketCapAllocation containing breakdown by market cap segments
     */
    public MarketCapAllocation calculateMarketCapAllocations(String portfolioId) {
        log.info("Calculating market cap allocations for portfolio: {}", portfolioId);
        return analyticsFactory.generatePortfolioAnalytics(AnalyticsType.MARKET_CAP_ALLOCATION, portfolioId);
    }
    
    /**
     * Calculate advanced analytics combining multiple data points based on request parameters
     * @param request The advanced analytics request containing parameters and flags
     * @return AdvancedAnalyticsResponse with requested analytics components
     */
    public AdvancedAnalyticsResponse calculateAdvancedAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Calculating advanced analytics for portfolio: {} from {} to {}", 
                request.getCoreIdentifiers().getPortfolioId(), request.getFromDate(), request.getToDate());
        
        // Start building the response
        AdvancedAnalyticsResponse.AdvancedAnalyticsResponseBuilder responseBuilder = AdvancedAnalyticsResponse.builder()
                .portfolioId(request.getCoreIdentifiers().getPortfolioId())
                .startDate(request.getFromDate())
                .endDate(request.getToDate())
                .timestamp(java.time.Instant.now());
        
        // Add comparison index if provided
        if (request.getCoreIdentifiers().getComparisonIndexSymbol() != null && !request.getCoreIdentifiers().getComparisonIndexSymbol().isEmpty()) {
            responseBuilder.comparisonIndexSymbol(request.getCoreIdentifiers().getComparisonIndexSymbol());
        }
        
        // Include heatmap if requested
        if (request.getFeatureToggles().isIncludeHeatmap()) {
            Heatmap heatmap = generateSectorHeatmap(request.getCoreIdentifiers().getPortfolioId());
            responseBuilder.heatmap(heatmap);
        }
        
        // Include top movers if requested
        if (request.getFeatureToggles().isIncludeMovers()) {
            int limit = request.getFeatureConfiguration().getMoversLimit() != null && request.getFeatureConfiguration().getMoversLimit() > 0 
                    ? request.getFeatureConfiguration().getMoversLimit() : 5; // Default to 5 if not specified
            GainerLoser movers = getTopGainersLosers(request.getCoreIdentifiers().getPortfolioId(), limit);
            responseBuilder.movers(movers);
        }
        
        // Include sector allocation if requested
        if (request.getFeatureToggles().isIncludeSectorAllocation()) {
            SectorAllocation sectorAllocation = calculateSectorAllocations(request.getCoreIdentifiers().getPortfolioId());
            responseBuilder.sectorAllocation(sectorAllocation);
        }
        
        // Include market cap allocation if requested
        if (request.getFeatureToggles().isIncludeMarketCapAllocation()) {
            MarketCapAllocation marketCapAllocation = calculateMarketCapAllocations(request.getCoreIdentifiers().getPortfolioId());
            responseBuilder.marketCapAllocation(marketCapAllocation);
        }
        
        return responseBuilder.build();
    }
}
