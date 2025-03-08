package com.portfolio.controller;

import com.am.common.amcommondata.domain.asset.Asset;
import com.am.common.amcommondata.domain.portfolio.Portfolio;
import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.portfolio.model.PortfolioAnalysis;
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
            @RequestParam(required = false) Integer size) {
        PortfolioAnalysis analysis = portfolioAnalysisService.analyzePortfolio(portfolioId, userId, page, size);
        if (analysis == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/{userId}/summary")
    public ResponseEntity<PortfolioModel> getPortfolioSummary(@PathVariable UUID userId) {
        return ResponseEntity.ok(null);
    }

    @GetMapping("/{userId}/holdings")
    public ResponseEntity<List<AssetModel>> getHoldingsByAssetType(
            @PathVariable UUID userId,
            @RequestParam(required = false) AssetType assetType) {
        return ResponseEntity.ok(null);
    }

}
