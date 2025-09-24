package com.portfolio.api;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.service.PortfolioDashboardService;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio Management", description = "Endpoints for managing user portfolios")
public class PortfolioController {
    
    private final PortfolioDashboardService portfolioDashboardService;
    private final PortfolioService portfolioService;

    @Operation(summary = "Get portfolio by ID", description = "Retrieves detailed portfolio information for a specific portfolio ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Portfolio found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PortfolioModelV1.class))),
        @ApiResponse(responseCode = "400", description = "Invalid portfolio ID format"),
        @ApiResponse(responseCode = "404", description = "Portfolio not found")
    })
    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioModelV1> getPortfolioById(
            @Parameter(description = "Portfolio ID (UUID format)") @PathVariable String portfolioId) {
        log.info("PortfolioController - getPortfolioById called with portfolioId: {}", portfolioId);
        
        try {
            PortfolioModelV1 portfolio = portfolioService.getPortfolioById(UUID.fromString(portfolioId));
            log.info("PortfolioController - getPortfolioById - Portfolio found: {}", portfolio != null ? "yes" : "no");
            return ResponseEntity.ok(portfolio);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioById - Invalid portfolio ID: {}", portfolioId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get all portfolios for user", description = "Retrieves all portfolios associated with a specific user ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of portfolios retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "No portfolios found for user")
    })
    @GetMapping
    public ResponseEntity<List<PortfolioModelV1>> getPortfolios(
            @Parameter(description = "User ID to fetch portfolios for") @RequestParam String userId) {
        log.info("PortfolioController - getPortfolios called with userId: {}", userId);
        
        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        log.info("PortfolioController - getPortfolios - Found {} portfolios for user: {}", 
            portfolios != null ? portfolios.size() : 0, userId);
            
        return ResponseEntity.ok(portfolios);
    }

    @Hidden
    @Operation(summary = "Get portfolio analysis", description = "Retrieves detailed analysis for a specific portfolio (hidden from API docs)")
    @GetMapping("/{portfolioId}/analysis")
    public ResponseEntity<PortfolioAnalysis> getPortfolioAnalysis(
            @PathVariable String portfolioId,
            @RequestParam String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        log.info("PortfolioController - getPortfolioAnalysis called - Portfolio: {}, User: {}, Page: {}, Size: {}, Interval: {}", 
            portfolioId, userId, page, size, interval != null ? interval : "null");
        
        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioAnalysis analysis = portfolioDashboardService.analyzePortfolio(
                portfolioId, userId, page, size, timeInterval);
                
            if (analysis == null) {
                log.warn("PortfolioController - getPortfolioAnalysis - No analysis found for portfolio: {}", portfolioId);
                return ResponseEntity.notFound().build();
            }
            
            log.info("PortfolioController - getPortfolioAnalysis - Successfully retrieved analysis for portfolio: {}", portfolioId);
            return ResponseEntity.ok(analysis);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioAnalysis - Invalid interval: {}", interval, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get portfolio summary", description = "Retrieves a summary of all portfolios for a user with performance metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Portfolio summary retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "No portfolio summary found for user")
    })
    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryV1> getPortfolioSummary(
            @RequestParam String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        log.info("PortfolioController - getPortfolioSummary called - User: {}, Page: {}, Size: {}, Interval: {}", 
            userId, page, size, interval != null ? interval : "null");
        
        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioSummaryV1 portfolioSummary = portfolioDashboardService.overviewPortfolio(userId, timeInterval);
            
            if (portfolioSummary == null) {
                log.warn("PortfolioController - getPortfolioSummary - No summary found for user: {}", userId);
                return ResponseEntity.notFound().build();
            }
            
            log.info("PortfolioController - getPortfolioSummary - Successfully retrieved summary for user: {}", userId);
            return ResponseEntity.ok(portfolioSummary);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioSummary - Invalid interval: {}", interval, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get portfolio holdings", description = "Retrieves all holdings across portfolios for a user with current values")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Portfolio holdings retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "No holdings found for user")
    })
    @GetMapping("/holdings")
    public ResponseEntity<PortfolioHoldings> getPortfolioHoldings(
            @RequestParam String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        log.info("PortfolioController - getPortfolioHoldings called - User: {}, Page: {}, Size: {}, Interval: {}", 
            userId, page, size, interval != null ? interval : "null");
        
        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioHoldings portfolioHoldings = portfolioDashboardService.getPortfolioHoldings(userId, timeInterval);
            
            if (portfolioHoldings == null) {
                log.warn("PortfolioController - getPortfolioHoldings - No holdings found for user: {}", userId);
                return ResponseEntity.notFound().build();
            }
            
            log.info("PortfolioController - getPortfolioHoldings - Successfully retrieved holdings for user: {}", userId);
            return ResponseEntity.ok(portfolioHoldings);
        } catch (IllegalArgumentException e) {
            log.error("PortfolioController - getPortfolioHoldings - Invalid interval: {}", interval, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
