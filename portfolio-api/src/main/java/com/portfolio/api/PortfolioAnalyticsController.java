package com.portfolio.api;

import com.portfolio.analytics.service.providers.portfolio.PortfolioAnalyticsFacade;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Portfolio Analytics", description = "Advanced analytics endpoints for portfolio data")
public class PortfolioAnalyticsController {

    private final PortfolioAnalyticsFacade portfolioAnalyticsFacade;
    
    /**
     * Advanced analytics endpoint that combines multiple analytics features with timeframe support
     * @param portfolioId The portfolio ID to analyze
     * @param request The advanced analytics request parameters
     * @return Combined analytics data based on requested components
     */
    @Operation(summary = "Get advanced portfolio analytics", description = "Retrieves comprehensive analytics for a portfolio with customizable components and timeframes")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analytics data retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdvancedAnalyticsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
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
