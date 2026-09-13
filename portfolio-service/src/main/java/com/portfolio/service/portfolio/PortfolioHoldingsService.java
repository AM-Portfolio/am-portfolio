package com.portfolio.service.portfolio;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.mapper.holdings.PortfolioHoldingsMapper;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;
import com.portfolio.service.calculator.PortfolioCalculator;

import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PortfolioHoldingsService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final PortfolioService portfolioService;
    private final PortfolioHoldingsMapper portfolioHoldingsMapper;
    @Nullable
    private final PortfolioHoldingsRedisService portfolioHoldingsRedisService;
    private final PortfolioCalculator portfolioCalculator;
    private final PortfolioHoldingsMongoService portfolioHoldingsMongoService;
    private final java.util.concurrent.Executor taskExecutor;

    public PortfolioHoldingsService(
            PortfolioService portfolioService,
            PortfolioHoldingsMapper portfolioHoldingsMapper,
            @Nullable PortfolioHoldingsRedisService portfolioHoldingsRedisService,
            PortfolioCalculator portfolioCalculator,
            PortfolioHoldingsMongoService portfolioHoldingsMongoService,
            @Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor,
            // kept for Spring wiring / future use — allocation applied in mapper only
            com.portfolio.service.basket.AllocationLedgerService allocationLedgerService) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingsMapper = portfolioHoldingsMapper;
        this.portfolioHoldingsRedisService = portfolioHoldingsRedisService;
        this.portfolioCalculator = portfolioCalculator;
        this.portfolioHoldingsMongoService = portfolioHoldingsMongoService;
        this.taskExecutor = taskExecutor;
    }

    @Value("${portfolio.redis.enabled:true}")
    private boolean isRedisEnabled;

    @Observed(name = "portfolio.get.holdings", contextualName = "get-portfolio-holdings")
    public PortfolioHoldings getPortfolioHoldings(String userId, TimeInterval interval) {
        return getPortfolioHoldings(userId, interval, true);
    }

    public PortfolioHoldings getPortfolioHoldings(String userId, TimeInterval interval, boolean enrich) {
        log.info("Starting getPortfolioHoldings - User: {}, Interval: {}, Enrich: {}", userId,
                interval != null ? interval.getCode() : "null", enrich);

        if (enrich) {
            Optional<PortfolioHoldings> cachedHoldings = getCachedHoldings(userId, interval, null);
            if (cachedHoldings.isPresent()) {
                return overlayAndReturn(cachedHoldings.get());
            }
        }

        log.info("Cache miss or skip for portfolio holdings - User: {}, fetching from source", userId);
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null || portfolios.isEmpty()) {
            log.info("No portfolios found for user: {} - Returning empty holdings", userId);
            return PortfolioHoldings.empty();
        }
        portfolios = portfolios.stream()
                .filter(p -> PortfolioKind.isBroker(p.getPortfolioKind()))
                .collect(Collectors.toList());
        log.info("Found {} BROKER portfolios for user All-view: {}", portfolios.size(), userId);

        return buildPortfolioHoldings(portfolios, userId, null, interval, enrich);
    }

    public PortfolioHoldings getPortfolioHoldings(String userId, String portfolioId, TimeInterval interval) {
        return getPortfolioHoldings(userId, portfolioId, interval, true);
    }

    public PortfolioHoldings getPortfolioHoldings(String userId, String portfolioId, TimeInterval interval,
            boolean enrich) {
        log.info("Starting getPortfolioHoldings for specific portfolio - User: {}, Portfolio: {}, Interval: {}",
                userId, portfolioId, interval != null ? interval.getCode() : "null");

        if (portfolioId == null || portfolioId.trim().isEmpty()) {
            throw new IllegalArgumentException("portfolioId cannot be blank");
        }

        if (enrich) {
            Optional<PortfolioHoldings> cachedHoldings = getCachedHoldings(userId, interval, portfolioId);
            if (cachedHoldings.isPresent()) {
                return overlayAndReturn(cachedHoldings.get());
            }
        }

        log.info("Cache miss for specific portfolio holdings - User: {}, Portfolio: {}", userId, portfolioId);
        UUID id;
        try {
            id = UUID.fromString(portfolioId);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid portfolioId: {}", portfolioId);
            return null;
        }
        PortfolioModelV1 portfolio = portfolioService.getPortfolioById(id);
        if (portfolio == null) {
            log.warn("No portfolio found with ID: {}", portfolioId);
            return null;
        }
        if (portfolio.getOwner() == null || !portfolio.getOwner().equals(userId)) {
            log.warn("Portfolio {} owner mismatch for user {}", portfolioId, userId);
            return null;
        }
        if (PortfolioKind.DELETED.equals(portfolio.getPortfolioKind())) {
            log.warn("Portfolio {} is DELETED", portfolioId);
            return null;
        }

        return buildPortfolioHoldings(List.of(portfolio), userId, portfolioId, interval, enrich);
    }

    private PortfolioHoldings overlayAndReturn(PortfolioHoldings cached) {
        List<EquityHoldings> list = cached.getEquityHoldings();
        if (list != null && !list.isEmpty()) {
            try {
                list = portfolioCalculator.repriceHoldings(list);
                portfolioCalculator.calculateWeights(list);
                cached.setEquityHoldings(list);
            } catch (Exception e) {
                log.warn("Price overlay failed; serving structure with cached prices: {}", e.getMessage());
            }
        }
        stampFreshness(cached, "TICK");
        log.info("Serving holdings with price overlay asOf={} freshness={}",
                cached.getAsOf(), cached.getPriceFreshness());
        return cached;
    }

    private void stampFreshness(PortfolioHoldings holdings, String priceSource) {
        LocalDateTime now = LocalDateTime.now(IST);
        holdings.setLastUpdated(now);
        holdings.setAsOf(now);
        holdings.setPriceSource(priceSource != null ? priceSource : "CACHE");
        boolean cashOpen = isCashOpenIst(now);
        holdings.setPriceFreshness(cashOpen ? "LIVE" : "AS_OF");
    }

    static boolean isCashOpenIst(LocalDateTime nowIst) {
        var t = nowIst.toLocalTime();
        var day = nowIst.getDayOfWeek();
        if (day.getValue() >= 6) {
            return false;
        }
        return !t.isBefore(java.time.LocalTime.of(9, 15)) && t.isBefore(java.time.LocalTime.of(15, 30));
    }

    private PortfolioHoldings buildPortfolioHoldings(List<PortfolioModelV1> portfolios, String userId,
            String portfolioId, TimeInterval interval, boolean enrich) {
        String context = portfolioId != null ? "portfolio: " + portfolioId : "all portfolios";
        log.debug("Building portfolio holdings for user: {} and {}", userId, context);

        var portfolioHoldings = portfolioHoldingsMapper.toPortfolioHoldingsV1(portfolios);
        // Allocation availableQuantity is set in PortfolioHoldingsMapper — do not re-query ledger here

        if (enrich) {
            log.info("Enriching stock prices for {} equity holdings for {}",
                    portfolioHoldings.getEquityHoldings() != null ? portfolioHoldings.getEquityHoldings().size() : 0,
                    context);
            var enrichedHoldings = portfolioCalculator.enrichHoldings(portfolioHoldings.getEquityHoldings());
            portfolioCalculator.calculateWeights(enrichedHoldings);
            portfolioHoldings.setEquityHoldings(enrichedHoldings);
            stampFreshness(portfolioHoldings, "OHLC");
        } else {
            stampFreshness(portfolioHoldings, "CACHE");
        }

        if (enrich) {
            List<EquityHoldings> eh = portfolioHoldings.getEquityHoldings();
            long priced = eh == null ? 0 : eh.stream()
                    .filter(h -> h.getCurrentPrice() != null && h.getCurrentPrice() > 0)
                    .count();
            int total = eh == null ? 0 : eh.size();
            boolean cacheOk = total == 0 || priced * 2 >= total; // ≥50% priced
            if (cacheOk) {
                log.info("Caching portfolio holdings for user: {} priced={}/{}", userId, priced, total);
                CompletableFuture.runAsync(() -> {
                    try {
                        if (isRedisEnabled && portfolioHoldingsRedisService != null) {
                            portfolioHoldingsRedisService.cachePortfolioHoldings(
                                    portfolioHoldings, userId, interval, portfolioId);
                        }
                        portfolioHoldingsMongoService.cachePortfolioHoldings(
                                portfolioHoldings, userId, interval, portfolioId);
                    } catch (Exception e) {
                        log.error("Failed to update persistent cache", e);
                    }
                }, taskExecutor);
            } else {
                log.warn("Skipping cache update for user {} — only {}/{} priced", userId, priced, total);
            }
        }

        return portfolioHoldings;
    }

    protected List<EquityHoldings> getHoldings(List<PortfolioModelV1> portfolios) {
        var equityHoldings = portfolioHoldingsMapper.toEquityHoldings(portfolios);
        equityHoldings = portfolioCalculator.enrichHoldings(equityHoldings);
        portfolioCalculator.calculateWeights(equityHoldings);
        return equityHoldings;
    }

    private Optional<PortfolioHoldings> getCachedHoldings(String userId, TimeInterval interval, String portfolioId) {
        Optional<PortfolioHoldings> cachedHoldings = Optional.empty();

        if (isRedisEnabled && portfolioHoldingsRedisService != null) {
            cachedHoldings = portfolioId == null
                    ? portfolioHoldingsRedisService.getLatestHoldings(userId, interval)
                    : portfolioHoldingsRedisService.getLatestHoldings(userId, interval, portfolioId);
            if (cachedHoldings.isPresent()) {
                log.info("Holdings structure from Redis — will overlay live prices - User: {}", userId);
                return cachedHoldings;
            }
        }

        cachedHoldings = portfolioHoldingsMongoService.getLatestHoldings(userId, interval, portfolioId);
        if (cachedHoldings.isPresent()) {
            log.info("Holdings structure from Mongo — will overlay live prices - User: {}", userId);
            // Async SWR rebuild of structure when older than 15m
            LocalDateTime last = cachedHoldings.get().getLastUpdated();
            if (last == null || last.isBefore(LocalDateTime.now().minusMinutes(15))) {
                triggerAsyncStructureRebuild(userId, interval, portfolioId);
            }
            return cachedHoldings;
        }

        return Optional.empty();
    }

    private void triggerAsyncStructureRebuild(String userId, TimeInterval interval, String portfolioId) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Async holdings cold rebuild for user: {}", userId);
                if (portfolioId != null && !portfolioId.isBlank()) {
                    UUID id = UUID.fromString(portfolioId);
                    PortfolioModelV1 p = portfolioService.getPortfolioById(id);
                    if (p != null && userId.equals(p.getOwner())) {
                        buildPortfolioHoldings(List.of(p), userId, portfolioId, interval, true);
                    }
                } else {
                    var portfolios = portfolioService.getPortfoliosByUserId(userId);
                    if (portfolios != null && !portfolios.isEmpty()) {
                        portfolios = portfolios.stream()
                                .filter(x -> PortfolioKind.isBroker(x.getPortfolioKind()))
                                .collect(Collectors.toList());
                        if (!portfolios.isEmpty()) {
                            buildPortfolioHoldings(portfolios, userId, null, interval, true);
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("Async rebuild failed for user: {}", userId, ex);
            }
        }, taskExecutor);
    }
}
