package com.portfolio.api;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.service.IndexAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for index analytics
 */
@RestController
@RequestMapping("/api/v1/analytics/index")
@RequiredArgsConstructor
@Slf4j
public class IndexAnalyticsController {

    private final IndexAnalyticsService indexAnalyticsService;
    
    /**
     * Get sector heatmap for an index
     * @param indexSymbol The index symbol (e.g., "NIFTY 50", "NIFTY BANK")
     * @return Heatmap data
     */
    @GetMapping("/{indexSymbol}/heatmap")
    public ResponseEntity<Heatmap> getSectorHeatmap(@PathVariable String indexSymbol) {
        log.info("REST request to get sector heatmap for index: {}", indexSymbol);
        return ResponseEntity.ok(indexAnalyticsService.generateSectorHeatmap(indexSymbol));
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
        return ResponseEntity.ok(indexAnalyticsService.getTopGainersLosers(indexSymbol, limit));
    }
    
    /**
     * Get sector and industry allocation percentages for an index
     * @param indexSymbol The index symbol
     * @return SectorAllocation data
     */
    @GetMapping("/{indexSymbol}/allocation")
    public ResponseEntity<SectorAllocation> getSectorAllocation(@PathVariable String indexSymbol) {
        log.info("REST request to get sector allocation for index: {}", indexSymbol);
        return ResponseEntity.ok(indexAnalyticsService.calculateSectorAllocations(indexSymbol));
    }
}
