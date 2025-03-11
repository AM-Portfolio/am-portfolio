package com.portfolio.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.portfolio.builder.PortfolioAnalysisBuilder;
import com.portfolio.model.PortfolioAnalysis;
import com.portfolio.model.StockPerformance;
import com.portfolio.model.TimeInterval;
import com.portfolio.rediscache.service.PortfolioAnalysisRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalysisService {
    
    private final AMPortfolioService portfolioService;
    private final StockPerformanceService stockPerformanceService;
    private final PortfolioAnalysisBuilder portfolioAnalysisBuilder;
    private final PortfolioAnalysisRedisService portfolioAnalysisRedisService;

    public PortfolioAnalysis analyzePortfolio(
            String portfolioId, 
            String userId, 
            Integer pageNumber, 
            Integer pageSize,
            TimeInterval interval) {
        try {
            Optional<PortfolioAnalysis> cachedAnalysis = getCachedAnalysis(portfolioId, userId, interval);
            if (cachedAnalysis.isPresent()) {
                return cachedAnalysis.get();
            }

            Instant startProcessing = Instant.now();
            log.info("Starting fresh portfolio analysis - Portfolio: {}, User: {}, Interval: {}", 
                    portfolioId, userId, interval != null ? interval.getCode() : "null");
            
            List<StockPerformance> performances = getPortfolioPerformances(portfolioId, interval);
            if (performances == null) {
                return null;
            }

            PortfolioAnalysis analysis = portfolioAnalysisBuilder.buildAnalysis(portfolioId, 
                userId, 
                performances, 
                pageNumber, 
                pageSize, 
                interval,
                startProcessing
            );

            portfolioAnalysisRedisService.cachePortfolioAnalysis(analysis, portfolioId, userId, interval);
            return analysis;

        } catch (Exception e) {
            log.error("Error analyzing portfolio {}: {}", portfolioId, e.getMessage(), e);
            return null;
        }
    }

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
        PortfolioModel portfolio = portfolioService.getPortfolioById(UUID.fromString(portfolioId));
        if (portfolio == null) {
            log.error("Portfolio not found for ID: {}", portfolioId);
            return null;
        }

        List<AssetModel> equityHoldings = portfolio.getAssets().stream()
            .filter(asset -> AssetType.EQUITY.equals(asset.getAssetType()))
            .toList();

        return stockPerformanceService.calculateStockPerformances(equityHoldings, interval);
    }
}
