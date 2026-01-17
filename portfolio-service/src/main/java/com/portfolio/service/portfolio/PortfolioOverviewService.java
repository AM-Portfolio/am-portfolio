package com.portfolio.service.portfolio;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.kafka.mapper.PortfolioMapperv1;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.v1.BrokerPortfolioSummary;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.service.calculator.PortfolioCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioOverviewService {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioMapperv1 portfolioMapper;
    private final PortfolioSummaryRedisService portfolioSummaryRedisService;
    private final PortfolioCalculator portfolioCalculator;

    public PortfolioSummaryV1 overviewPortfolio(String userId, TimeInterval interval) {
        log.info("Starting overviewPortfolio - User: {}, Interval: {}",
                userId, interval != null ? interval.getCode() : "null");

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
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }

        PortfolioSummaryV1 finalSummary = buildPortfolioSummary(portfolios, userId, null, interval);
        log.info("Completed overviewPortfolio for user: {}", userId);
        return finalSummary;
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
        log.info("Starting overviewPortfolio for specific portfolio - User: {}, Portfolio: {}, Interval: {}",
                userId, portfolioId, interval != null ? interval.getCode() : "null");

        // For specific portfolio, we don't use cache as it's more targeted
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

        log.info("Completed overviewPortfolio for user: {} and portfolio: {}", userId, portfolioId);
        return finalSummary;
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
            brokerSummaryMap.computeIfAbsent(portfolio.getBrokerType(), brokerType -> portfolioSummary);
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
        portfolioSummaryRedisService.cachePortfolioSummary(finalSummary, userId, interval);

        log.info("Completed overviewPortfolio for user: {}", userId);
        return finalSummary;
    }

    private PortfolioSummaryV1 getPortfolioSummary(List<PortfolioModelV1> portfolios) {
        log.debug("Calculating total portfolio value from {} portfolios", portfolios.size());

        var totalValue = portfolios.stream().mapToDouble(PortfolioModelV1::getTotalValue).sum();
        log.debug("Calculated total value: {}", totalValue);

        var equityHoldings = portfolioHoldingsService.getHoldings(portfolios);

        // Use calculator to generate the summary
        return portfolioCalculator.calculateSummary(equityHoldings, totalValue);
    }

    private Optional<PortfolioSummaryV1> getCachedSummary(String userId, TimeInterval interval) {
        log.debug("Checking cache for portfolio summary - User: {}, Interval: {}",
                userId, interval != null ? interval.getCode() : "null");

        Optional<PortfolioSummaryV1> cachedSummary = portfolioSummaryRedisService.getLatestSummary(userId, interval);
        if (cachedSummary.isPresent()) {
            log.info("Serving portfolio summary from cache - User: {}, Interval: {}",
                    userId, interval != null ? interval.getCode() : "null");
        } else {
            log.debug("No cached summary found for user: {}", userId);
        }

        return cachedSummary;
    }
}
