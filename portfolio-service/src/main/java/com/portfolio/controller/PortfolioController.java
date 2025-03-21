package com.portfolio.controller;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.service.PortfolioDashboardService;

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
public class PortfolioController {
    
    private final PortfolioDashboardService portfolioDashboardService;
    private final PortfolioService portfolioService;

    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioModelV1> getPortfolioById(@PathVariable String portfolioId) {
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

    @GetMapping
    public ResponseEntity<List<PortfolioModelV1>> getPortfolios(@RequestParam String userId) {
        log.info("PortfolioController - getPortfolios called with userId: {}", userId);
        
        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        log.info("PortfolioController - getPortfolios - Found {} portfolios for user: {}", 
            portfolios != null ? portfolios.size() : 0, userId);
            
        return ResponseEntity.ok(portfolios);
    }

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
