package com.portfolio.service.portfolio;

import java.time.Instant;
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
import com.portfolio.redis.service.PortfolioAnalysisRedisService;
import com.portfolio.service.PortfolioStockPerformanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalysisService {
    
    private final PortfolioService portfolioService;
    private final PortfolioStockPerformanceService stockPerformanceService;
    private final PortfolioAnalysisBuilder portfolioAnalysisBuilder;
    private final PortfolioAnalysisRedisService portfolioAnalysisRedisService;

    public PortfolioAnalysis analyzePortfolio(
            String portfolioId, 
            String userId, 
            Integer pageNumber, 
            Integer pageSize,
            TimeInterval interval) {
        // Use OVERALL as default if interval is null
        TimeInterval effectiveInterval = interval != null ? interval : TimeInterval.OVERALL;
        log.debug("Analyzing portfolio - Portfolio: {}, User: {}, Interval: {}", 
            portfolioId, userId, effectiveInterval.getCode());
        
        try {
            Optional<PortfolioAnalysis> cachedAnalysis = getCachedAnalysis(portfolioId, userId, effectiveInterval);
            if (cachedAnalysis.isPresent()) {
                return cachedAnalysis.get();
            }

            Instant startProcessing = Instant.now();
            log.info("Starting fresh portfolio analysis - Portfolio: {}, User: {}, Interval: {}", 
                    portfolioId, userId, effectiveInterval.getCode());
            
            List<StockPerformance> performances = getPortfolioPerformances(portfolioId, effectiveInterval);
            if (performances == null) {
                return null;
            }

            PortfolioAnalysis analysis = portfolioAnalysisBuilder.buildAnalysis(portfolioId, 
                userId, 
                performances, 
                pageNumber, 
                pageSize, 
                effectiveInterval,
                startProcessing
            );

            portfolioAnalysisRedisService.cachePortfolioAnalysis(analysis, portfolioId, userId, effectiveInterval);
            return analysis;

        } catch (Exception e) {
            log.error("Error analyzing portfolio {}: {}", portfolioId, e.getMessage(), e);
            return null;
        }
    }

    private Optional<PortfolioAnalysis> getCachedAnalysis(String portfolioId, String userId, TimeInterval interval) {
        // Use OVERALL as default if interval is null
        TimeInterval effectiveInterval = interval != null ? interval : TimeInterval.OVERALL;
        log.debug("Checking cache for portfolio analysis - Portfolio: {}, User: {}, Interval: {}", 
            portfolioId, userId, effectiveInterval.getCode());
            
        Optional<PortfolioAnalysis> cachedAnalysis = 
            portfolioAnalysisRedisService.getLatestAnalysis(portfolioId, userId, effectiveInterval);
            
        if (cachedAnalysis.isPresent()) {
            log.info("Serving portfolio analysis from cache - Portfolio: {}, User: {}, Interval: {}", 
                portfolioId, userId, effectiveInterval.getCode());
        } else {
            log.debug("No cached analysis found for portfolio: {}, user: {}", portfolioId, userId);
        }
        
        return cachedAnalysis;
    }

    private List<StockPerformance> getPortfolioPerformances(String portfolioId, TimeInterval interval) {
        // Use OVERALL as default if interval is null
        TimeInterval effectiveInterval = interval != null ? interval : TimeInterval.OVERALL;
        log.debug("Fetching portfolio performances - Portfolio: {}, Interval: {}", 
            portfolioId, effectiveInterval.getCode());
            
        PortfolioModelV1 portfolio = portfolioService.getPortfolioById(UUID.fromString(portfolioId));
        if (portfolio == null) {
            log.warn("Portfolio not found: {}", portfolioId);
            return null;
        }
        
        log.debug("Portfolio found: {}, fetching stock performances", portfolioId);
        List<EquityModel> equities = getEquitiesFromPortfolio(portfolio);
        if (equities.isEmpty()) {
            log.info("No equities found in portfolio: {}", portfolioId);
            return List.of();
        }
        
        log.debug("Found {} equities in portfolio: {}", equities.size(), portfolioId);
        List<StockPerformance> performances = stockPerformanceService.calculateStockPerformances(
                equities, 
                effectiveInterval);
                
        log.debug("Retrieved {} stock performances for portfolio: {}", 
            performances != null ? performances.size() : 0, portfolioId);
        return performances;
    }

    private List<EquityModel> getEquitiesFromPortfolio(PortfolioModelV1 portfolio) {
        if (portfolio == null) {
            log.warn("Null portfolio provided to getEquitiesFromPortfolio");
            return List.of();
        }
        
        String portfolioId = portfolio.getId() != null ? portfolio.getId().toString() : "unknown";
        log.debug("Extracting equities from portfolio: {}", portfolioId);
        
        if (portfolio.getEquityModels() == null) {
            log.debug("No equity models found in portfolio: {}", portfolioId);
            return List.of();
        }
        
        // Count invalid equities for logging
        int totalEquities = (int) portfolio.getEquityModels().stream()
                .filter(equity -> equity != null && equity.getAssetType() == AssetType.EQUITY)
                .count();
        
        // Filter out null and invalid equities
        List<EquityModel> validEquities = portfolio.getEquityModels().stream()
                .filter(equity -> equity != null && equity.getAssetType() == AssetType.EQUITY)
                .filter(equity -> equity.getSymbol() != null && !equity.getSymbol().isEmpty())
                .toList();
        
        // Log only once with count of filtered equities
        int invalidCount = totalEquities - validEquities.size();
        if (invalidCount > 0) {
            log.warn("Filtered out {} equities with null or empty symbols from portfolio: {}", 
                    invalidCount, portfolioId);
        }
                
        log.debug("Extracted {} valid equities from portfolio: {}", validEquities.size(), portfolioId);
        return validEquities;
    }
}
