package com.portfolio.controller;

import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.service.PortfolioAnalysisService;
import com.portfolio.service.PortfolioOverviewService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {
    
    private final PortfolioService portfolioService;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final PortfolioOverviewService portfolioOverviewService;

    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioModelV1> getPortfolioById(@PathVariable String portfolioId) {
        return ResponseEntity.ok(portfolioService.getPortfolioById(UUID.fromString(portfolioId)));
    }

    @GetMapping
    public ResponseEntity<List<PortfolioModelV1>> getPortfolios(@RequestParam String userId) {
        return ResponseEntity.ok(portfolioService.getPortfoliosByUserId(userId));
    }

    @GetMapping("/{portfolioId}/analysis")
    public ResponseEntity<PortfolioAnalysis> getPortfolioAnalysis(
            @PathVariable String portfolioId,
            @RequestParam String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioAnalysis analysis = portfolioAnalysisService.analyzePortfolio(
                portfolioId, userId, page, size, timeInterval);
            if (analysis == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(analysis);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryV1> getPortfolioSummary(
            @RequestParam String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioSummaryV1 portfolioSummary = portfolioOverviewService.overviewPortfolio(userId);
            if (portfolioSummary == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(portfolioSummary);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/holdings")
    public ResponseEntity<PortfolioHoldings> getPortfolioHoldings(
            @RequestParam String userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String interval) {
        try {
            TimeInterval timeInterval = TimeInterval.fromCode(interval);
            PortfolioHoldings portfolioHoldings = portfolioOverviewService.getPortfolioHoldings(userId);
            if (portfolioHoldings == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(portfolioHoldings);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

}
