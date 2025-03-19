package com.portfolio.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.builder.PortfolioAnalysisBuilder;
import com.portfolio.model.StockPerformance;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.rediscache.service.PortfolioAnalysisRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioOverviewService {
    
    private final PortfolioService portfolioService;
    private final StockPerformanceService stockPerformanceService;
    private final PortfolioAnalysisBuilder portfolioAnalysisBuilder;
    private final PortfolioAnalysisRedisService portfolioAnalysisRedisService;

    // public PortfolioOverview overviewPortfolio(String userId) {
    //     var portfolios = portfolioService.getPortfolioByUserId(userId);
    //     if (portfolios == null) {
    //         return null;
    //     }
          
    // }

    private Optional<PortfolioAnalysis> getCachedAnalysis(String portfolioId, String userId, TimeInterval interval) {
        Optional<PortfolioAnalysis> cachedAnalysis = portfolioAnalysisRedisService.getLatestAnalysis(
            portfolioId, userId, interval);
        
        if (cachedAnalysis.isPresent()) {
            log.info("Serving portfolio analysis from cache - Portfolio: {}, User: {}, Interval: {}", 
                portfolioId, userId, interval != null ? interval.getCode() : "null");
        }
        return cachedAnalysis;
    }

    private List<StockPerformance> getPortfolioPerformances(String portfolioId, TimeInterval interval) {
        PortfolioModelV1 portfolio = portfolioService.getPortfolioById(UUID.fromString(portfolioId));
        if (portfolio == null) {
            log.error("Portfolio not found for ID: {}", portfolioId);
            return null;
        }

        List<EquityModel> equityHoldings = portfolio.getEquityModels().stream()
            .filter(asset -> AssetType.EQUITY.equals(asset.getAssetType()))
            .toList();

        return stockPerformanceService.calculateStockPerformances(equityHoldings, interval);
    }
}
