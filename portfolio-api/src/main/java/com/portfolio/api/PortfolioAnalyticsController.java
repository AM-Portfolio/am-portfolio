package com.portfolio.api;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.CoreIdentifiers;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;
import com.portfolio.analytics.service.PortfolioAnalyticsFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                portfolioId, request.getTimeFrame());

        request.getCoreIdentifiers().setPortfolioId(portfolioId);
        
        return ResponseEntity.ok(portfolioAnalyticsFacade.calculateAdvancedAnalytics(request));
    }
}
