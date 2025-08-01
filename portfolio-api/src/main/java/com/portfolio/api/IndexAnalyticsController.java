package com.portfolio.api;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;
import com.portfolio.analytics.service.IndexAnalyticsFacade;
import com.portfolio.model.market.TimeFrame;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
     * Advanced analytics endpoint that combines multiple analytics features with timeframe support
     * @param indexSymbol The index symbol to analyze
     * @param request The advanced analytics request parameters
     * @return Combined analytics data based on requested components
     */
    @PostMapping("/{indexSymbol}/advanced")
    public ResponseEntity<AdvancedAnalyticsResponse> getAdvancedAnalytics(
            @PathVariable String indexSymbol,
            @RequestBody AdvancedAnalyticsRequest request) {
        log.info("REST request for advanced analytics on index: {} with timeframe: {} to {}, timeFrame: {}", 
                indexSymbol, request.getFromDate(), request.getToDate(), request.getTimeFrame());
        
        // Set the index symbol from the path parameter (overriding any value in the request)
        if (request.getCoreIdentifiers() == null) {
            // Initialize CoreIdentifiers if it's null
            request.setCoreIdentifiers(new AdvancedAnalyticsRequest.CoreIdentifiers());
        }
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
