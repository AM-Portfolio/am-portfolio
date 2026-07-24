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
import com.portfolio.service.portfolio.PortfolioHoldingsMongoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import io.micrometer.observation.annotation.Observed;

@Service
@Slf4j
public class PortfolioHoldingsService {

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsMapper portfolioHoldingsMapper;
    
    @org.springframework.lang.Nullable
    private final PortfolioHoldingsRedisService portfolioHoldingsRedisService;
    
    private final PortfolioCalculator portfolioCalculator;
    private final PortfolioHoldingsMongoService portfolioHoldingsMongoService;

    private final com.github.benmanes.caffeine.cache.Cache<String, PortfolioHoldings> holdingsL1 =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(60, java.util.concurrent.TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();

    public PortfolioHoldingsService(
            PortfolioService portfolioService,
            PortfolioHoldingsMapper portfolioHoldingsMapper,
            @org.springframework.lang.Nullable PortfolioHoldingsRedisService portfolioHoldingsRedisService,
            PortfolioCalculator portfolioCalculator,
            PortfolioHoldingsMongoService portfolioHoldingsMongoService) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingsMapper = portfolioHoldingsMapper;
        this.portfolioHoldingsRedisService = portfolioHoldingsRedisService;
        this.portfolioCalculator = portfolioCalculator;
        this.portfolioHoldingsMongoService = portfolioHoldingsMongoService;
    }
    
    @org.springframework.beans.factory.annotation.Value("${portfolio.redis.enabled:true}")
    private boolean isRedisEnabled;

    @Observed(name = "portfolio.get.holdings", contextualName = "get-portfolio-holdings")
    public PortfolioHoldings getPortfolioHoldings(String userId, TimeInterval interval) {
        return getPortfolioHoldings(userId, interval, true);
    }

    public PortfolioHoldings getPortfolioHoldings(String userId, TimeInterval interval, boolean enrich) {
        log.info("Starting getPortfolioHoldings - User: {}, Interval: {}, Enrich: {}", userId,
                interval != null ? interval.getCode() : "null", enrich);

        // If enrichment is disabled, we skip cache as cache usually stores
        // enriched/full data
        if (enrich) {
            Optional<PortfolioHoldings> cachedHoldings = getCachedHoldings(userId, interval, null);
            if (cachedHoldings.isPresent()) {
                log.info("Returning cached portfolio holdings for user: {}", userId);
                return cachedHoldings.get();
            }
        }

        log.info("Cache miss or skip for portfolio holdings - User: {}, fetching from source", userId);
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null || portfolios.isEmpty()) {
            log.info("No portfolios found for user: {} - Returning empty holdings", userId);
            PortfolioHoldings emptyHoldings = new PortfolioHoldings();
            emptyHoldings.setEquityHoldings(java.util.Collections.emptyList());
            return emptyHoldings;
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

        if (portfolioId == null || portfolioId.trim().isEmpty()) {
            log.warn("Blank portfolioId provided for specific portfolio request - User: {}", userId);
            throw new IllegalArgumentException("portfolioId cannot be blank");
        }

        if (enrich) {
            Optional<PortfolioHoldings> cachedHoldings = getCachedHoldings(userId, interval, portfolioId);
            if (cachedHoldings.isPresent()) {
                log.info("Returning cached portfolio holdings for user: {} and portfolio: {}", userId, portfolioId);
                return cachedHoldings.get();
            }
        }

        log.info("Cache miss for specific portfolio holdings - User: {}, Portfolio: {}, fetching from source", userId, portfolioId);
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

        // Store in cache if enriched
        if (enrich) {
            log.info("Caching portfolio holdings for user: {} and context: {}", userId, context);
            String cacheKey = userId + ":" + (portfolioId != null ? portfolioId : "ALL") + ":" + (interval != null ? interval.getCode() : "null");
            holdingsL1.put(cacheKey, portfolioHoldings);
            
            // Cache the enriched portfolio asynchronously
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    if (isRedisEnabled && portfolioHoldingsRedisService != null) {
                        portfolioHoldingsRedisService.cachePortfolioHoldings(portfolioHoldings, userId, interval, portfolioId);
                    }
                    portfolioHoldingsMongoService.cachePortfolioHoldings(portfolioHoldings, userId, interval, portfolioId);
                } catch (Exception e) {
                    log.error("Failed to update persistent cache", e);
                }
            });
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

    private Optional<PortfolioHoldings> getCachedHoldings(String userId, TimeInterval interval, String portfolioId) {
        log.debug("Checking cache for portfolio holdings - User: {}, Interval: {}, Portfolio: {}",
                userId, interval != null ? interval.getCode() : "null", portfolioId);
                
        String cacheKey = userId + ":" + (portfolioId != null ? portfolioId : "ALL") + ":" + (interval != null ? interval.getCode() : "null");
        PortfolioHoldings l1Cache = holdingsL1.getIfPresent(cacheKey);
        if (l1Cache != null) {
            log.info("Serving portfolio holdings from L1 cache - User: {}, Portfolio: {}", userId, portfolioId);
            return Optional.of(l1Cache);
        }

        Optional<PortfolioHoldings> cachedHoldings = Optional.empty();
        if (isRedisEnabled && portfolioHoldingsRedisService != null) {
            if (portfolioId == null) {
                cachedHoldings = portfolioHoldingsRedisService.getLatestHoldings(userId, interval);
            } else {
                cachedHoldings = portfolioHoldingsRedisService.getLatestHoldings(userId, interval, portfolioId);
            }
            if (cachedHoldings.isPresent()) {
                log.info("Serving portfolio holdings from Redis cache - User: {}, Interval: {}",
                        userId, interval != null ? interval.getCode() : "null");
                return cachedHoldings;
            }
        }

        // Tier 2: Check MongoDB if Redis missed (especially when Redis is disabled)
        if (!isRedisEnabled) {
            cachedHoldings = portfolioHoldingsMongoService.getLatestFreshHoldings(userId, interval, portfolioId);
            if (cachedHoldings.isPresent()) {
                log.info("Serving portfolio holdings from MongoDB cache - User: {}, Interval: {}",
                        userId, interval != null ? interval.getCode() : "null");
                return cachedHoldings;
            }
        }
        
        return Optional.empty();
    }
}
