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
import com.portfolio.service.StockPerformanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalysisService {
    
    private final PortfolioService portfolioService;
    private final StockPerformanceService stockPerformanceService;
    private final PortfolioAnalysisBuilder portfolioAnalysisBuilder;
    private final PortfolioAnalysisRedisService portfolioAnalysisRedisService;

    public PortfolioAnalysis analyzePortfolio(
            String portfolioId, 
            String userId, 
            Integer pageNumber, 
            Integer pageSize,
            TimeInterval interval) {
        log.debug("Analyzing portfolio - Portfolio: {}, User: {}, Interval: {}", 
            portfolioId, userId, interval != null ? interval.getCode() : "null");
        
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
        log.debug("Checking cache for portfolio analysis - Portfolio: {}, User: {}, Interval: {}", 
            portfolioId, userId, interval != null ? interval.getCode() : "null");
            
        Optional<PortfolioAnalysis> cachedAnalysis = 
            portfolioAnalysisRedisService.getLatestAnalysis(portfolioId, userId, interval);
            
        if (cachedAnalysis.isPresent()) {
            log.info("Serving portfolio analysis from cache - Portfolio: {}, User: {}, Interval: {}", 
                portfolioId, userId, interval != null ? interval.getCode() : "null");
        } else {
            log.debug("No cached analysis found for portfolio: {}, user: {}", portfolioId, userId);
        }
        
        return cachedAnalysis;
    }

    private List<StockPerformance> getPortfolioPerformances(String portfolioId, TimeInterval interval) {
        log.debug("Fetching portfolio performances - Portfolio: {}, Interval: {}", 
            portfolioId, interval != null ? interval.getCode() : "null");
            
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
                interval);
                
        log.debug("Retrieved {} stock performances for portfolio: {}", 
            performances != null ? performances.size() : 0, portfolioId);
        return performances;
    }

    private List<EquityModel> getEquitiesFromPortfolio(PortfolioModelV1 portfolio) {
        log.debug("Extracting equities from portfolio: {}", portfolio.getId());
        
        if (portfolio.getEquityModels() == null) {
            log.debug("No equity models found in portfolio: {}", portfolio.getId());
            return List.of();
        }
        
        List<EquityModel> equities = portfolio.getEquityModels().stream()
                .filter(equity -> equity.getAssetType() == AssetType.EQUITY)
                .toList();
                
        log.debug("Extracted {} equities from portfolio: {}", equities.size(), portfolio.getId());
        return equities;
    }
}
