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
     * Get sector heatmap for an index
     * @param indexSymbol The index symbol (e.g., "NIFTY 50", "NIFTY BANK")
     * @param fromDate Optional start date for the analysis period
     * @param toDate Optional end date for the analysis period
     * @param timeFrame Optional time frame interval (e.g., DAY, HOUR)
     * @return Heatmap data
     */
    @GetMapping("/{indexSymbol}/heatmap")
    public ResponseEntity<Heatmap> getSectorHeatmap(
            @PathVariable String indexSymbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) TimeFrame timeFrame) {
        
        log.info("REST request to get sector heatmap for index: {} from {} to {} with timeFrame: {}", 
                indexSymbol, fromDate, toDate, timeFrame);
        
        // Create a TimeFrameRequest to pass the parameters
        TimeFrameRequest timeFrameRequest = TimeFrameRequest.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(timeFrame)
                .build();
                
        return ResponseEntity.ok(indexAnalyticsFacade.generateSectorHeatmap(indexSymbol, timeFrameRequest));
    }
    
    /**
     * Get top gainers and losers for an index
     * @param indexSymbol The index symbol
     * @param limit Optional limit parameter for number of top gainers/losers (default: 5)
     * @param fromDate Optional start date for the analysis period
     * @param toDate Optional end date for the analysis period
     * @param timeFrame Optional time frame interval (e.g., DAY, HOUR)
     * @return Top movers data
     */
    @GetMapping("/{indexSymbol}/movers")
    public ResponseEntity<GainerLoser> getTopGainersLosers(
            @PathVariable String indexSymbol,
            @RequestParam(required = false, defaultValue = "5") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) TimeFrame timeFrame) {
        
        log.info("REST request to get top {} gainers/losers for index: {} from {} to {} with timeFrame: {}", 
                limit, indexSymbol, fromDate, toDate, timeFrame);
        
        // Create a TimeFrameRequest to pass the parameters
        TimeFrameRequest timeFrameRequest = TimeFrameRequest.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .timeFrame(timeFrame)
                .build();
                
        return ResponseEntity.ok(indexAnalyticsFacade.getTopGainersLosers(indexSymbol, limit, timeFrameRequest));
    }
    
    /**
     * Get sector and industry allocation percentages for an index
     * @param indexSymbol The index symbol
     * @param fromDate Optional start date for the analysis period
     * @param toDate Optional end date for the analysis period
     * @param timeFrame Optional time frame interval (e.g., DAY, HOUR)
     * @return SectorAllocation data
     */
    @GetMapping("/{indexSymbol}/allocation")
    public ResponseEntity<SectorAllocation> getSectorAllocation(
            @PathVariable String indexSymbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) TimeFrame timeFrame) {
        
        log.info("REST request to get sector allocation for index: {} from {} to {} with timeFrame: {}", 
                indexSymbol, fromDate, toDate, timeFrame);
        
        if (fromDate != null || toDate != null || timeFrame != null) {
            // Create a TimeFrameRequest to pass the parameters
            TimeFrameRequest timeFrameRequest = TimeFrameRequest.builder()
                    .fromDate(fromDate)
                    .toDate(toDate)
                    .timeFrame(timeFrame)
                    .build();
            
            return ResponseEntity.ok(indexAnalyticsFacade.calculateSectorAllocations(indexSymbol, timeFrameRequest));
        } else {
            return ResponseEntity.ok(indexAnalyticsFacade.calculateSectorAllocations(indexSymbol));
        }
    }
    
    /**
     * Get market capitalization allocation breakdown for an index
     * @param indexSymbol The index symbol
     * @param fromDate Optional start date for the analysis period
     * @param toDate Optional end date for the analysis period
     * @param timeFrame Optional time frame interval (e.g., DAY, HOUR)
     * @return MarketCapAllocation data
     */
    @GetMapping("/{indexSymbol}/market-cap")
    public ResponseEntity<MarketCapAllocation> getMarketCapAllocation(
            @PathVariable String indexSymbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) TimeFrame timeFrame) {
        
        log.info("REST request to get market cap allocation for index: {} from {} to {} with timeFrame: {}", 
                indexSymbol, fromDate, toDate, timeFrame);
        
        if (fromDate != null || toDate != null || timeFrame != null) {
            // Create a TimeFrameRequest to pass the parameters
            TimeFrameRequest timeFrameRequest = TimeFrameRequest.builder()
                    .fromDate(fromDate)
                    .toDate(toDate)
                    .timeFrame(timeFrame)
                    .build();
            
            return ResponseEntity.ok(indexAnalyticsFacade.calculateMarketCapAllocations(indexSymbol, timeFrameRequest));
        } else {
            return ResponseEntity.ok(indexAnalyticsFacade.calculateMarketCapAllocations(indexSymbol));
        }
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
