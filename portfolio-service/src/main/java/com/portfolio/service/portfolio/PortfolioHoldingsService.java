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

import com.portfolio.service.basket.AllocationLedgerService;

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
    private final java.util.concurrent.Executor taskExecutor;
    private final AllocationLedgerService allocationLedgerService;



    public PortfolioHoldingsService(
            PortfolioService portfolioService,
            PortfolioHoldingsMapper portfolioHoldingsMapper,
            @org.springframework.lang.Nullable PortfolioHoldingsRedisService portfolioHoldingsRedisService,
            PortfolioCalculator portfolioCalculator,
            PortfolioHoldingsMongoService portfolioHoldingsMongoService,
            @org.springframework.beans.factory.annotation.Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor,
            AllocationLedgerService allocationLedgerService) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingsMapper = portfolioHoldingsMapper;
        this.portfolioHoldingsRedisService = portfolioHoldingsRedisService;
        this.portfolioCalculator = portfolioCalculator;
        this.portfolioHoldingsMongoService = portfolioHoldingsMongoService;
        this.taskExecutor = taskExecutor;
        this.allocationLedgerService = allocationLedgerService;
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
        // All view = BROKER only (exclude BASKET to avoid double-count)
        portfolios = portfolios.stream()
                .filter(p -> com.am.common.amcommondata.model.enums.PortfolioKind.isBroker(p.getPortfolioKind()))
                .collect(java.util.stream.Collectors.toList());
        log.info("Found {} BROKER portfolios for user All-view: {}", portfolios.size(), userId);

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

        if (portfolioHoldings.getEquityHoldings() != null) {
            for (EquityHoldings h : portfolioHoldings.getEquityHoldings()) {
                if (h.getIsin() != null) {
                    double activeAllocated = 0.0;
                    for (PortfolioModelV1 p : portfolios) {
                        activeAllocated += allocationLedgerService.sumActiveQuantityByBrokerPortfolioIdAndIsin(
                                p.getId().toString(), h.getIsin());
                    }
                    double rawQty = h.getQuantity() != null ? h.getQuantity() : 0.0;
                    h.setAvailableQuantity(Math.max(0.0, rawQty - activeAllocated));
                }
            }
        }

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

        // Store in cache if enriched and valid
        if (enrich) {
            boolean hasValidPrices = portfolioHoldings.getEquityHoldings().stream()
                .anyMatch(h -> h.getCurrentPrice() != null && h.getCurrentPrice() > 0);
            
            if (hasValidPrices || portfolioHoldings.getEquityHoldings().isEmpty()) {
                log.info("Caching portfolio holdings for user: {} and context: {}", userId, context);
                
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
                }, taskExecutor);
            } else {
                log.warn("Skipping cache update for user {} - Market data appears to be missing/failed", userId);
            }
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

        // Tier 2: Check MongoDB if Redis missed
        cachedHoldings = portfolioHoldingsMongoService.getLatestHoldings(userId, interval, portfolioId);
        if (cachedHoldings.isPresent()) {
            List<EquityHoldings> cachedList = cachedHoldings.get().getEquityHoldings();
            boolean hasLivePrices = cachedList != null
                && (cachedList.isEmpty()
                    || cachedList.stream()
                        .filter(h -> h.getCurrentPrice() != null && h.getCurrentPrice() > 0)
                        .count() >= cachedList.size() * 0.5);
            
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
            boolean isStale = cachedHoldings.get().getLastUpdated() == null || cachedHoldings.get().getLastUpdated().isBefore(cutoff);

            if (hasLivePrices && !isStale) {
                log.info("Serving valid and fresh portfolio holdings from MongoDB cache - User: {}", userId);
                return cachedHoldings;  // ✅ real data
            }
            
            log.warn("MongoDB holdings cache has stale prices or is older than 15 mins for User: {} — triggering async rebuild", userId);
            
            // Stale-While-Revalidate: Trigger an async refresh but return the stale data immediately
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    log.info("Async cache rebuild started for user: {}", userId);
                    var portfolios = portfolioService.getPortfoliosByUserId(userId);
                    if (portfolios != null && !portfolios.isEmpty()) {
                        if (portfolioId != null) {
                            portfolios = portfolios.stream()
                                    .filter(p -> p.getId() != null && p.getId().toString().equals(portfolioId))
                                    .collect(java.util.stream.Collectors.toList());
                        } else {
                            portfolios = portfolios.stream()
                                    .filter(p -> com.am.common.amcommondata.model.enums.PortfolioKind.isBroker(p.getPortfolioKind()))
                                    .collect(java.util.stream.Collectors.toList());
                        }
                        if (!portfolios.isEmpty()) {
                            buildPortfolioHoldings(portfolios, userId, portfolioId, interval, true);
                        }
                    }
                } catch (Exception ex) {
                    log.error("Async rebuild failed for user: {}", userId, ex);
                }
            }, taskExecutor);
            
            // Return stale data for immediate UI rendering
            return cachedHoldings;
        }
        
        return Optional.empty();
    }
}
