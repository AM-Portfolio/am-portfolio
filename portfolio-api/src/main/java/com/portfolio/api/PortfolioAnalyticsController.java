package com.portfolio.api;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;
import com.portfolio.analytics.service.PortfolioAnalyticsFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST controller for portfolio analytics
 */
@RestController
@RequestMapping("/api/v1/analytics/portfolio")
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalyticsController {

    private final PortfolioAnalyticsFacade portfolioAnalyticsFacade;
    
    /**
     * Get sector heatmap for a portfolio
     * @param portfolioId The portfolio ID
     * @return Heatmap data
     */
    @GetMapping("/{portfolioId}/heatmap")
    public ResponseEntity<Heatmap> getSectorHeatmap(@PathVariable String portfolioId) {
        log.info("REST request to get sector heatmap for portfolio: {}", portfolioId);
        return ResponseEntity.ok(portfolioAnalyticsFacade.generateSectorHeatmap(portfolioId));
    }
    
    /**
     * Get top gainers and losers for a portfolio
     * @param portfolioId The portfolio ID
     * @param limit Number of top gainers/losers to return (default: 5)
     * @return GainerLoser data
     */
    @GetMapping("/{portfolioId}/movers")
    public ResponseEntity<GainerLoser> getTopMovers(
            @PathVariable String portfolioId,
            @RequestParam(defaultValue = "5") int limit) {
        log.info("REST request to get top {} movers for portfolio: {}", limit, portfolioId);
        return ResponseEntity.ok(portfolioAnalyticsFacade.getTopGainersLosers(portfolioId, limit));
    }
    
    /**
     * Get sector and industry allocation percentages for a portfolio
     * @param portfolioId The portfolio ID
     * @return SectorAllocation data
     */
    @GetMapping("/{portfolioId}/allocation")
    public ResponseEntity<SectorAllocation> getSectorAllocation(@PathVariable String portfolioId) {
        log.info("REST request to get sector allocation for portfolio: {}", portfolioId);
        return ResponseEntity.ok(portfolioAnalyticsFacade.calculateSectorAllocations(portfolioId));
    }
    
    /**
     * Get market capitalization allocation breakdown for a portfolio
     * @param portfolioId The portfolio ID
     * @return MarketCapAllocation data
     */
    @GetMapping("/{portfolioId}/market-cap")
    public ResponseEntity<MarketCapAllocation> getMarketCapAllocation(@PathVariable String portfolioId) {
        log.info("REST request to get market cap allocation for portfolio: {}", portfolioId);
        return ResponseEntity.ok(portfolioAnalyticsFacade.calculateMarketCapAllocations(portfolioId));
    }
    
    /**
     * Advanced analytics endpoint that combines multiple analytics features with timeframe support
     * @param portfolioId The portfolio ID to analyze
     * @param request The advanced analytics request parameters
     * @return Combined analytics data based on requested components
     */
    @PostMapping("/{portfolioId}/advanced")
    public ResponseEntity<AdvancedAnalyticsResponse> getAdvancedAnalytics(
            @PathVariable String portfolioId,
            @RequestBody AdvancedAnalyticsRequest request) {
        log.info("REST request for advanced analytics on portfolio: {} with timeframe: {} to {}", 
                portfolioId, request.getStartDate(), request.getEndDate());
        
        // Set the portfolio ID from the path parameter (overriding any value in the request)
        request.setPortfolioId(portfolioId);
        
        return ResponseEntity.ok(portfolioAnalyticsFacade.calculateAdvancedAnalytics(request));
    }
}
