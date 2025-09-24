package com.portfolio.api;

import com.portfolio.analytics.service.providers.index.IndexAnalyticsFacade;
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
 * REST controller for index analytics
 */
@RestController
@RequestMapping("/api/v1/analytics/index")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Index Analytics", description = "Advanced analytics endpoints for market indices data")
public class IndexAnalyticsController {

    private final IndexAnalyticsFacade indexAnalyticsFacade;
    
    /**
     * Advanced analytics endpoint that combines multiple analytics features with timeframe support
     * @param indexSymbol The index symbol to analyze
     * @param request The advanced analytics request parameters
     * @return Combined analytics data based on requested components
     */
    @Operation(summary = "Get advanced index analytics", description = "Retrieves comprehensive analytics for a market index with customizable components and timeframes")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analytics data retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdvancedAnalyticsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "404", description = "Index not found")
    })
    @PostMapping("/{indexSymbol}/advanced")
    public ResponseEntity<AdvancedAnalyticsResponse> getAdvancedAnalytics(
            @PathVariable String indexSymbol,
            @RequestBody AdvancedAnalyticsRequest request) {
        log.info("REST request for advanced analytics on index: {} with timeframe: {} to {}, timeFrame: {}", 
                indexSymbol, request.getFromDate(), request.getToDate(), request.getTimeFrame());
        
        request.getCoreIdentifiers().setIndexSymbol(indexSymbol);
        
        // Log pagination information if provided
        if (request.getPagination() != null) {
            if (request.getPagination().isReturnAllData()) {
                log.info("Request to return all data without pagination");
            } else {
                log.info("Pagination requested: page {}, size {}, sortBy {}, sortDirection {}",
                        request.getPagination().getPage(),
                        request.getPagination().getSize(),
                        request.getPagination().getSortBy(),
                        request.getPagination().getSortDirection());
            }
        }
        
        return ResponseEntity.ok(indexAnalyticsFacade.calculateAdvancedAnalytics(request));
    }
}
