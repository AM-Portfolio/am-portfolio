package com.portfolio.service.portfolio;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.mapper.holdings.PortfolioHoldingsMapper;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;
import com.portfolio.service.calculator.PortfolioCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioHoldingsService {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsMapper portfolioHoldingsMapper;
    private final PortfolioHoldingsRedisService portfolioHoldingsRedisService;
    private final PortfolioCalculator portfolioCalculator;

    public PortfolioHoldings getPortfolioHoldings(String userId, TimeInterval interval) {
        return getPortfolioHoldings(userId, interval, true);
    }

    public PortfolioHoldings getPortfolioHoldings(String userId, TimeInterval interval, boolean enrich) {
        log.info("Starting getPortfolioHoldings - User: {}, Interval: {}, Enrich: {}", userId,
                interval != null ? interval.getCode() : "null", enrich);

        // If enrichment is disabled, we skip cache as cache usually stores
        // enriched/full data
        if (enrich) {
            Optional<PortfolioHoldings> cachedHoldings = getCachedHoldings(userId, interval);
            if (cachedHoldings.isPresent()) {
                log.info("Returning cached portfolio holdings for user: {}", userId);
                return cachedHoldings.get();
            }
        }

        log.info("Cache miss or skip for portfolio holdings - User: {}, fetching from source", userId);
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null || portfolios.isEmpty()) {
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }
        log.info("Found {} portfolios for user: {}", portfolios.size(), userId);

        var portfolioHoldings = buildPortfolioHoldings(portfolios, userId, null, interval, enrich);

        log.info("Completed getPortfolioHoldings for user: {}", userId);
        return portfolioHoldings;
    }

    /**
     * Retrieves the portfolio holdings for a specific portfolio of the given user
     * and time interval.
     * 
     * @param userId      the ID of the user
     * @param portfolioId the ID of the specific portfolio to filter by
     * @param interval    the time interval
     * @return the portfolio holdings for the specific portfolio
     */
    public PortfolioHoldings getPortfolioHoldings(String userId, String portfolioId, TimeInterval interval) {
        return getPortfolioHoldings(userId, portfolioId, interval, true);
    }

    /**
     * Retrieves the portfolio holdings for a specific portfolio of the given user
     * and time interval, with optional enrichment.
     * 
     * @param userId      the ID of the user
     * @param portfolioId the ID of the specific portfolio to filter by
     * @param interval    the time interval
     * @param enrich      whether to enrich holdings with market data
     * @return the portfolio holdings for the specific portfolio
     */
    public PortfolioHoldings getPortfolioHoldings(String userId, String portfolioId, TimeInterval interval,
            boolean enrich) {
        log.info("Starting getPortfolioHoldings for specific portfolio - User: {}, Portfolio: {}, Interval: {}",
                userId, portfolioId, interval != null ? interval.getCode() : "null");

        // For specific portfolio, we don't use cache as it's more targeted
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null || portfolios.isEmpty()) {
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }
        log.info("Found {} portfolios for user: {}", portfolios.size(), userId);

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

        var portfolioHoldings = buildPortfolioHoldings(filteredPortfolios, userId, portfolioId, interval, enrich);

        // Note: We do not cache specific portfolio holdings to avoid stale data issues

        log.info("Completed getPortfolioHoldings for user: {} and portfolio: {}", userId, portfolioId);
        return portfolioHoldings;
    }

    /**
     * Builds portfolio holdings from filtered portfolios with enrichment
     * 
     * @param portfolios  the list of portfolios to process
     * @param userId      the user ID for logging
     * @param portfolioId the portfolio ID for logging (null if processing all
     *                    portfolios)
     * @return the complete portfolio holdings with enriched data
     */
    private PortfolioHoldings buildPortfolioHoldings(List<PortfolioModelV1> portfolios, String userId,
            String portfolioId, TimeInterval interval, boolean enrich) {
        String context = portfolioId != null ? "portfolio: " + portfolioId : "all portfolios";
        log.debug("Building portfolio holdings for user: {} and {}", userId, context);

        var portfolioHoldings = portfolioHoldingsMapper.toPortfolioHoldingsV1(portfolios);

        if (enrich) {
            log.info("Enriching stock prices and performance data for {} equity holdings for {}",
                    portfolioHoldings.getEquityHoldings() != null ? portfolioHoldings.getEquityHoldings().size() : 0,
                    context);

            var enrichedHoldings = portfolioCalculator.enrichHoldings(portfolioHoldings.getEquityHoldings());
            portfolioCalculator.calculateWeights(enrichedHoldings);
            portfolioHoldings.setEquityHoldings(enrichedHoldings);
        } else {
            log.info("Skipping enrichment for user: {} and {}", userId, context);
        }

        portfolioHoldings.setLastUpdated(LocalDateTime.now());

        log.debug("Completed building portfolio holdings for user: {} and {}", userId, context);

        // Store in cache only for all portfolios (not for specific portfolio) AND if
        // enriched
        if (enrich) {
            log.info("Caching portfolio holdings for user: {}", userId);
            portfolioHoldingsRedisService.cachePortfolioHoldings(portfolioHoldings, userId, interval);
        }

        log.info("Completed getPortfolioHoldings for user: {}", userId);
        return portfolioHoldings;
    }

    protected List<EquityHoldings> getHoldings(List<PortfolioModelV1> portfolios) {

        var equityHoldings = portfolioHoldingsMapper.toEquityHoldings(portfolios);

        log.info("Enriching stock prices and performance data for {} equity holdings",
                equityHoldings != null ? equityHoldings.size() : 0);

        equityHoldings = portfolioCalculator.enrichHoldings(equityHoldings);
        portfolioCalculator.calculateWeights(equityHoldings);
        return equityHoldings;
    }

    private Optional<PortfolioHoldings> getCachedHoldings(String userId, TimeInterval interval) {
        log.debug("Checking cache for portfolio holdings - User: {}, Interval: {}",
                userId, interval != null ? interval.getCode() : "null");

        Optional<PortfolioHoldings> cachedHoldings = portfolioHoldingsRedisService.getLatestHoldings(userId, interval);
        if (cachedHoldings.isPresent()) {
            log.info("Serving portfolio holdings from cache - User: {}, Interval: {}",
                    userId, interval != null ? interval.getCode() : "null");
        }
        return cachedHoldings;
    }
}
