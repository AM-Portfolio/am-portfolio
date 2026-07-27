package com.portfolio.service.portfolio;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.model.mapper.PortfolioMapperv1;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.v1.BrokerPortfolioSummary;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.service.calculator.PortfolioCalculator;
import com.am.observability.flow.FlowLogger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PortfolioOverviewService {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioMapperv1 portfolioMapper;
    
    @org.springframework.lang.Nullable
    private final PortfolioSummaryRedisService portfolioSummaryRedisService;
    


    private final PortfolioCalculator portfolioCalculator;
    private final FlowLogger flowLogger;

    public PortfolioOverviewService(
            PortfolioService portfolioService,
            PortfolioHoldingsService portfolioHoldingsService,
            PortfolioMapperv1 portfolioMapper,
            @org.springframework.lang.Nullable PortfolioSummaryRedisService portfolioSummaryRedisService,
            PortfolioCalculator portfolioCalculator,
            FlowLogger flowLogger) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingsService = portfolioHoldingsService;
        this.portfolioMapper = portfolioMapper;
        this.portfolioSummaryRedisService = portfolioSummaryRedisService;
        this.portfolioCalculator = portfolioCalculator;
        this.flowLogger = flowLogger;
    }

    public PortfolioSummaryV1 overviewPortfolio(String userId, TimeInterval interval) {
        try (var span = flowLogger.start("overviewPortfolio", "user", userId, "interval", interval != null ? interval.getCode() : "null")) {
        Optional<PortfolioSummaryV1> cachedSummary = getCachedSummary(userId, interval);
        if (cachedSummary.isPresent()) {
            log.info("Returning cached portfolio summary for user: {}", userId);
            return cachedSummary.get();
        }

        log.info("Cache miss for portfolio summary - User: {}, fetching from source", userId);
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        log.info("Retrieved {} portfolios for user: {}",
                portfolios != null ? portfolios.size() : 0, userId);

        if (portfolios == null || portfolios.isEmpty()) {
            log.info("No portfolios found for user: {} - Returning empty summary", userId);
            com.portfolio.model.portfolio.v1.PortfolioSummaryV1 emptySummary = new com.portfolio.model.portfolio.v1.PortfolioSummaryV1();
            emptySummary.setCurrentValue(0.0);
            emptySummary.setInvestmentValue(0.0);
            emptySummary.setTotalGainLoss(0.0);
            emptySummary.setTotalGainLossPercentage(0.0);
            emptySummary.setTodayGainLoss(0.0);
            emptySummary.setTodayGainLossPercentage(0.0);
            emptySummary.setTotalAssets(0);
            emptySummary.setGainersCount(0);
            emptySummary.setLosersCount(0);
            emptySummary.setTodayGainersCount(0);
            emptySummary.setTodayLosersCount(0);
            emptySummary.setBrokerPortfolios(new java.util.HashMap<>());
            emptySummary.setMarketCapHoldings(new java.util.HashMap<>());
            emptySummary.setSectorialHoldings(new java.util.HashMap<>());
            return emptySummary;
        }

        PortfolioSummaryV1 finalSummary = buildPortfolioSummary(portfolios, userId, null, interval);
        return finalSummary;
        } catch (Exception e) {
            log.error("Error in overviewPortfolio", e);
            throw e;
        }
    }

    /**
     * Provides an overview of a specific portfolio for the given user, portfolio ID
     * and time interval.
     * 
     * @param userId      the ID of the user
     * @param portfolioId the ID of the specific portfolio to filter by
     * @param interval    the time interval
     * @return the portfolio summary for the specific portfolio
     */
    public PortfolioSummaryV1 overviewPortfolio(String userId, String portfolioId, TimeInterval interval) {
        try (var span = flowLogger.start("overviewPortfolioSpecific", "user", userId, "portfolio", portfolioId, "interval", interval != null ? interval.getCode() : "null")) {
            if (portfolioId == null || portfolioId.trim().isEmpty()) {
            log.warn("Blank portfolioId provided for specific portfolio overview - User: {}", userId);
            throw new IllegalArgumentException("portfolioId cannot be blank");
        }
        Optional<PortfolioSummaryV1> cachedSummary = Optional.empty();
        if (portfolioSummaryRedisService != null) {
            cachedSummary = portfolioSummaryRedisService.getLatestSummary(userId, interval, portfolioId);
            if (cachedSummary.isPresent()) {
                log.info("Returning cached portfolio summary for user: {} and portfolio: {}", userId, portfolioId);
                return cachedSummary.get();
            }
        }

        log.info("Cache miss for specific portfolio summary - User: {}, Portfolio: {}, fetching from source", userId, portfolioId);
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        log.info("Retrieved {} portfolios for user: {}",
                portfolios != null ? portfolios.size() : 0, userId);

        if (portfolios == null || portfolios.isEmpty()) {
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }

        // Filter for the specific portfolio
        var filteredPortfolios = portfolios.stream()
                .filter(portfolio -> portfolio.getId() != null && portfolio.getId().toString().equals(portfolioId))
                .collect(java.util.stream.Collectors.toList());

        if (filteredPortfolios.isEmpty()) {
            log.warn("No portfolio found with ID: {} for user: {}", portfolioId, userId);
            return null;
        }

        log.info("Found {} matching portfolio(s) for ID: {} and user: {}",
                filteredPortfolios.size(), portfolioId, userId);

        PortfolioSummaryV1 finalSummary = buildPortfolioSummary(filteredPortfolios, userId, portfolioId, interval);
        return finalSummary;
        } catch (Exception e) {
            log.error("Error in specific overviewPortfolio", e);
            throw e;
        }
    }

    /**
     * Refreshes the portfolio summary for a user, bypassing the cache to get live
     * data.
     */
    public PortfolioSummaryV1 refreshPortfolio(String userId, TimeInterval interval) {
        log.info("Starting refreshPortfolio (Live Update) - User: {}, Interval: {}",
                userId, interval != null ? interval.getCode() : "null");

        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null || portfolios.isEmpty()) {
            return null;
        }

        return buildPortfolioSummary(portfolios, userId, null, interval);
    }

    /**
     * Builds portfolio summary from filtered portfolios with broker grouping
     * 
     * @param portfolios  the list of portfolios to process
     * @param userId      the user ID for logging
     * @param portfolioId the portfolio ID for logging (null if processing all
     *                    portfolios)
     * @return the complete portfolio summary
     */
    private PortfolioSummaryV1 buildPortfolioSummary(List<PortfolioModelV1> portfolios, String userId,
            String portfolioId, TimeInterval interval) {
        String context = portfolioId != null ? "portfolio: " + portfolioId : "all portfolios";

        // Group by broker and create summary
        Map<BrokerType, BrokerPortfolioSummary> brokerSummaryMap = new HashMap<>();
        log.debug("Grouping portfolios by broker for user: {} and {}", userId, context);

        for (var portfolio : portfolios) {
            log.debug("Processing portfolio: ID={}, Broker={}, Value={}",
                    portfolio.getId(), portfolio.getBrokerType(), portfolio.getTotalValue());

            var portfolioSummary = portfolioMapper.toPortfolioModelV1(portfolio);
            brokerSummaryMap.merge(portfolio.getBrokerType(), portfolioSummary,
                (existing, incoming) -> {
                    double inc = incoming.getInvestmentValue() != null ? incoming.getInvestmentValue() : 0.0;
                    double ex  = existing.getInvestmentValue() != null ? existing.getInvestmentValue() : 0.0;
                    existing.setInvestmentValue(ex + inc);
                    return existing;
                });
        }

        log.debug("Created broker summary map with {} entries for {}", brokerSummaryMap.size(), context);

        // Create final summary
        log.debug("Creating final portfolio summary for user: {} and {}", userId, context);
        PortfolioSummaryV1 finalSummary = getPortfolioSummary(portfolios);
        finalSummary.setBrokerPortfolios(brokerSummaryMap);

        log.info("Total portfolio value for user {} and {}: {}",
                userId, context, finalSummary.getInvestmentValue());

        // Store in cache
        log.debug("Caching portfolio summary for user: {}", userId);
        if (portfolioSummaryRedisService != null) {
            portfolioSummaryRedisService.cachePortfolioSummary(finalSummary, userId, interval, portfolioId);
        }

        log.info("Completed overviewPortfolio for user: {}", userId);
        return finalSummary;
    }

    private PortfolioSummaryV1 getPortfolioSummary(List<PortfolioModelV1> portfolios) {
        log.debug("Calculating total portfolio value from {} portfolios", portfolios.size());

        var totalValue = portfolios.stream()
                .mapToDouble(p -> p.getTotalValue() != null ? p.getTotalValue() : 0.0)
                .sum();
        log.debug("Calculated total value: {}", totalValue);

        var equityHoldings = portfolioHoldingsService.getHoldings(portfolios);
        
        var investmentValue = equityHoldings.stream()
                .mapToDouble(h -> h.getInvestmentCost() != null ? h.getInvestmentCost() : 0.0)
                .sum();

        // Use calculator to generate the summary
        PortfolioSummaryV1 summary = portfolioCalculator.calculateSummary(equityHoldings, totalValue);
        summary.setInvestmentValue(investmentValue);
        return summary;
    }

    private Optional<PortfolioSummaryV1> getCachedSummary(String userId, TimeInterval interval) {
        log.debug("Checking cache for portfolio summary - User: {}, Interval: {}",
                userId, interval != null ? interval.getCode() : "null");

        Optional<PortfolioSummaryV1> cachedSummary = Optional.empty();
        if (portfolioSummaryRedisService != null) {
            cachedSummary = portfolioSummaryRedisService.getLatestSummary(userId, interval);
            if (cachedSummary.isPresent()) {
                log.info("Serving portfolio summary from Redis cache - User: {}, Interval: {}",
                        userId, interval != null ? interval.getCode() : "null");
                return cachedSummary;
            }
        }
        log.debug("No cached summary found for user: {}", userId);

        return cachedSummary;
    }
}
