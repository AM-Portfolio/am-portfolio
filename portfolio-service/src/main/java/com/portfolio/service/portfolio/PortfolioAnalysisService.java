package com.portfolio.service.portfolio;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
@Slf4j
public class PortfolioAnalysisService {
    
    private final PortfolioService portfolioService;
    private final StockPerformanceService stockPerformanceService;
    private final PortfolioAnalysisBuilder portfolioAnalysisBuilder;
    
    @org.springframework.lang.Nullable
    private final PortfolioAnalysisRedisService portfolioAnalysisRedisService;

    /** Fail before Cloudflare origin timeout (~100–125s). */
    private final Duration analysisBudget;

    public PortfolioAnalysisService(
            PortfolioService portfolioService,
            StockPerformanceService stockPerformanceService,
            PortfolioAnalysisBuilder portfolioAnalysisBuilder,
            @org.springframework.lang.Nullable PortfolioAnalysisRedisService portfolioAnalysisRedisService,
            @Value("${portfolio.analysis.budget-ms:90000}") long analysisBudgetMs) {
        this.portfolioService = portfolioService;
        this.stockPerformanceService = stockPerformanceService;
        this.portfolioAnalysisBuilder = portfolioAnalysisBuilder;
        this.portfolioAnalysisRedisService = portfolioAnalysisRedisService;
        this.analysisBudget = Duration.ofMillis(Math.max(5_000L, analysisBudgetMs));
    }

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
            log.info("Starting fresh portfolio analysis - Portfolio: {}, User: {}, Interval: {}, budgetMs={}", 
                    portfolioId, userId, interval != null ? interval.getCode() : "null", analysisBudget.toMillis());
            
            List<StockPerformance> performances = getPortfolioPerformances(portfolioId, interval);
            if (performances == null) {
                return null;
            }
            enforceBudget(startProcessing, "after performances");

            PortfolioAnalysis analysis = portfolioAnalysisBuilder.buildAnalysis(portfolioId, 
                userId, 
                performances, 
                pageNumber, 
                pageSize, 
                interval,
                startProcessing
            );
            long totalMs = Duration.between(startProcessing, Instant.now()).toMillis();
            log.info("Fresh portfolio analysis complete - Portfolio: {}, totalMs={}", portfolioId, totalMs);

            if (portfolioAnalysisRedisService != null) {
                portfolioAnalysisRedisService.cachePortfolioAnalysis(analysis, portfolioId, userId, interval);
            }
            return analysis;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error analyzing portfolio {}: {}", portfolioId, e.getMessage(), e);
            return null;
        }
    }

    private void enforceBudget(Instant startProcessing, String stage) {
        Duration elapsed = Duration.between(startProcessing, Instant.now());
        if (elapsed.compareTo(analysisBudget) > 0) {
            log.warn("Portfolio analysis budget exceeded at {} — elapsedMs={} budgetMs={}",
                    stage, elapsed.toMillis(), analysisBudget.toMillis());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Portfolio analysis exceeded time budget (" + analysisBudget.toMillis() + "ms) at " + stage);
        }
    }

    private Optional<PortfolioAnalysis> getCachedAnalysis(String portfolioId, String userId, TimeInterval interval) {
        log.debug("Checking cache for portfolio analysis - Portfolio: {}, User: {}, Interval: {}", 
            portfolioId, userId, interval != null ? interval.getCode() : "null");
            
        Optional<PortfolioAnalysis> cachedAnalysis = Optional.empty();
        if (portfolioAnalysisRedisService != null) {
            cachedAnalysis = portfolioAnalysisRedisService.getLatestAnalysis(portfolioId, userId, interval);
        }
            
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
