package com.portfolio.api;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;
import com.portfolio.analytics.service.IndexAnalyticsFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST controller for index analytics
 */
@RestController
@RequestMapping("/api/v1/analytics/index")
@RequiredArgsConstructor
@Slf4j
public class IndexAnalyticsController {

    private final IndexAnalyticsFacade indexAnalyticsFacade;
    
    /**
     * Get sector heatmap for an index
     * @param indexSymbol The index symbol (e.g., "NIFTY 50", "NIFTY BANK")
     * @return Heatmap data
     */
    @GetMapping("/{indexSymbol}/heatmap")
    public ResponseEntity<Heatmap> getSectorHeatmap(@PathVariable String indexSymbol) {
        log.info("REST request to get sector heatmap for index: {}", indexSymbol);
        return ResponseEntity.ok(indexAnalyticsFacade.generateSectorHeatmap(indexSymbol));
    }
    
    /**
     * Get top gainers and losers for an index
     * @param indexSymbol The index symbol
     * @param limit Number of top gainers/losers to return (default: 5)
     * @return GainerLoser data
     */
    @GetMapping("/{indexSymbol}/movers")
    public ResponseEntity<GainerLoser> getTopMovers(
            @PathVariable String indexSymbol,
            @RequestParam(defaultValue = "5") int limit) {
        log.info("REST request to get top {} movers for index: {}", limit, indexSymbol);
        return ResponseEntity.ok(indexAnalyticsFacade.getTopGainersLosers(indexSymbol, limit));
    }
    
    /**
     * Get sector and industry allocation percentages for an index
     * @param indexSymbol The index symbol
     * @return SectorAllocation data
     */
    @GetMapping("/{indexSymbol}/allocation")
    public ResponseEntity<SectorAllocation> getSectorAllocation(@PathVariable String indexSymbol) {
        log.info("REST request to get sector allocation for index: {}", indexSymbol);
        return ResponseEntity.ok(indexAnalyticsFacade.calculateSectorAllocations(indexSymbol));
    }
    
    /**
     * Get market capitalization allocation breakdown for an index
     * @param indexSymbol The index symbol
     * @return MarketCapAllocation data
     */
    @GetMapping("/{indexSymbol}/market-cap")
    public ResponseEntity<MarketCapAllocation> getMarketCapAllocation(@PathVariable String indexSymbol) {
        log.info("REST request to get market cap allocation for index: {}", indexSymbol);
        return ResponseEntity.ok(indexAnalyticsFacade.calculateMarketCapAllocations(indexSymbol));
    }
    
    /**
     * Advanced analytics endpoint that combines multiple analytics features with timeframe support
     * @param indexSymbol The index symbol to analyze
     * @param request The advanced analytics request parameters
     * @return Combined analytics data based on requested components
     */
    @PostMapping("/{indexSymbol}/advanced")
    public ResponseEntity<AdvancedAnalyticsResponse> getAdvancedAnalytics(
            @PathVariable String indexSymbol,
            @RequestBody AdvancedAnalyticsRequest request) {
        log.info("REST request for advanced analytics on index: {} with timeframe: {} to {}", 
                indexSymbol, request.getStartDate(), request.getEndDate());
        
        // Set the index symbol from the path parameter (overriding any value in the request)
        request.setIndexSymbol(indexSymbol);
        
        return ResponseEntity.ok(indexAnalyticsFacade.calculateAdvancedAnalytics(request));
    }
}
