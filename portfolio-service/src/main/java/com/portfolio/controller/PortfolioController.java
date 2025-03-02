package com.portfolio.controller;

import com.am.common.amcommondata.domain.asset.Asset;
import com.am.common.amcommondata.domain.portfolio.Portfolio;
import com.am.common.amcommondata.model.enums.AssetType;
import com.portfolio.service.AMPortfolioService;
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

    @GetMapping("/{userId}/summary")
    public ResponseEntity<Portfolio> getPortfolioSummary(@PathVariable UUID userId) {
        //return ResponseEntity.ok(portfolioService.getPortfolioSummary(userId));
        return ResponseEntity.ok(null);
    }

    @GetMapping("/{userId}/holdings")
    public ResponseEntity<List<Asset>> getHoldingsByAssetType(
            @PathVariable UUID userId,
            @RequestParam(required = false) AssetType assetType) {
        //return ResponseEntity.ok(portfolioService.getHoldingsByAssetType(userId, assetType));
        return ResponseEntity.ok(null);
    }

}
