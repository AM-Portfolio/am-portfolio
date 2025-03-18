package com.portfolio.controller;

import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.portfolio.model.PortfolioAnalysis;
import com.portfolio.model.TimeInterval;
import com.portfolio.service.AMPortfolioService;
import com.portfolio.service.PortfolioAnalysisService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {
    
    private final AMPortfolioService portfolioService;
    private final PortfolioAnalysisService portfolioAnalysisService;

    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioModel> getPortfolioById(@PathVariable String portfolioId) {
        return ResponseEntity.ok(portfolioService.getPortfolioById(UUID.fromString(portfolioId)));
    }

    @GetMapping
    public ResponseEntity<List<PortfolioModel>> getPortfolios(@RequestParam String userId) {
        return ResponseEntity.ok(portfolioService.getPortfolios(userId));
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

}
