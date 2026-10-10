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
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.am.common.amcommondata.model.PortfolioSnapshotModel;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;

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
    
    private final PortfolioSnapshotService portfolioSnapshotService;
    private final PortfolioSummaryMongoService portfolioSummaryMongoService;

    private final PortfolioCalculator portfolioCalculator;
    private final FlowLogger flowLogger;

    public PortfolioOverviewService(
            PortfolioService portfolioService,
            PortfolioHoldingsService portfolioHoldingsService,
            PortfolioMapperv1 portfolioMapper,
            @org.springframework.lang.Nullable PortfolioSummaryRedisService portfolioSummaryRedisService,
            PortfolioSnapshotService portfolioSnapshotService,
            PortfolioSummaryMongoService portfolioSummaryMongoService,
            PortfolioCalculator portfolioCalculator,
            FlowLogger flowLogger) {
        this.portfolioService = portfolioService;
        this.portfolioHoldingsService = portfolioHoldingsService;
        this.portfolioMapper = portfolioMapper;
        this.portfolioSummaryRedisService = portfolioSummaryRedisService;
        this.portfolioSnapshotService = portfolioSnapshotService;
        this.portfolioSummaryMongoService = portfolioSummaryMongoService;
        this.portfolioCalculator = portfolioCalculator;
        this.flowLogger = flowLogger;
    }

    public PortfolioSummaryV1 overviewPortfolio(String userId, TimeInterval interval) {
        try (var span = flowLogger.start("overviewPortfolio", "user", userId, "interval", interval != null ? interval.getCode() : "null")) {
        Optional<PortfolioSummaryV1> cachedSummary = getCachedSummary(userId, interval);
        if (cachedSummary.isPresent()) {
            log.info("Returning cached portfolio summary for user: {} (with price overlay)", userId);
            return repriceCachedSummary(cachedSummary.get(), userId, null, interval);
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
            Optional<PortfolioSummaryV1> cachedSummary = getCachedSummary(userId, interval, portfolioId);
            if (cachedSummary.isPresent()) {
                log.info("Returning cached portfolio summary for user: {} and portfolio: {} (with price overlay)",
                        userId, portfolioId);
                return repriceCachedSummary(cachedSummary.get(), userId, portfolioId, interval);
            }
            
            log.info("Cache miss for specific portfolio summary - User: {}, Portfolio: {}, fetching from source", userId, portfolioId);
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(portfolioId);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid portfolioId for overview: {}", portfolioId);
            return null;
        }
        PortfolioModelV1 portfolio = portfolioService.getPortfolioById(id);
        if (portfolio == null || portfolio.getOwner() == null || !portfolio.getOwner().equals(userId)) {
            log.warn("No portfolio found with ID: {} for user: {}", portfolioId, userId);
            return null;
        }
        var filteredPortfolios = java.util.List.of(portfolio);

        log.info("Found portfolio {} for user: {}", portfolioId, userId);

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
        Map<String, BrokerPortfolioSummary> brokerSummaryMap = new HashMap<>();
        log.debug("Grouping portfolios by broker for user: {} and {}", userId, context);

        for (var portfolio : portfolios) {
            if (!com.am.common.amcommondata.model.enums.PortfolioKind.isBroker(portfolio.getPortfolioKind())) {
                continue;
            }
            log.debug("Processing portfolio: ID={}, Broker={}, Value={}",
                    portfolio.getId(), portfolio.getBrokerType(), portfolio.getTotalValue());

            var portfolioSummary = portfolioMapper.toPortfolioModelV1(portfolio);
            String brokerKey = portfolio.getBrokerType() != null ? portfolio.getBrokerType().name() : "UNKNOWN";
            brokerSummaryMap.merge(brokerKey, portfolioSummary,
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
        PortfolioSummaryV1 finalSummary = getPortfolioSummary(portfolios, userId, portfolioId, interval);
        finalSummary.setBrokerPortfolios(brokerSummaryMap);

        log.info("Total portfolio value for user {} and {}: {}",
                userId, context, finalSummary.getInvestmentValue());

        // Apply timeframe overrides before caching
        applyTimeframeGainLoss(finalSummary, userId, portfolioId, interval);
        // When per-symbol previousClose repair misses after hours, freeze day P&L from prior-session snapshot.
        applySessionDayGainLossIfMissing(finalSummary, userId, portfolioId);

        // Store in cache
        log.debug("Caching portfolio summary for user: {}", userId);
        if (portfolioSummaryRedisService != null) {
            portfolioSummaryRedisService.cachePortfolioSummary(finalSummary, userId, interval, portfolioId);
        }
        if (portfolioSummaryMongoService != null) {
            portfolioSummaryMongoService.cachePortfolioSummary(finalSummary, userId, interval, portfolioId);
        }

        log.info("Completed overviewPortfolio for user: {}", userId);
        return finalSummary;
    }

    private PortfolioSummaryV1 getPortfolioSummary(List<PortfolioModelV1> portfolios, String userId,
            String portfolioId, TimeInterval interval) {
        log.debug("Calculating total portfolio value from {} portfolios", portfolios.size());

        List<com.portfolio.model.portfolio.EquityHoldings> equityHoldings = null;
        com.portfolio.model.portfolio.PortfolioHoldings pricedHoldings = null;
        if (userId != null) {
            try {
                pricedHoldings = (portfolioId == null || portfolioId.isBlank())
                        ? portfolioHoldingsService.getPortfolioHoldings(userId, interval, true)
                        : portfolioHoldingsService.getPortfolioHoldings(userId, portfolioId, interval, true);
                if (pricedHoldings != null && pricedHoldings.getEquityHoldings() != null) {
                    equityHoldings = pricedHoldings.getEquityHoldings();
                }
            } catch (Exception e) {
                log.warn("Failed to get cached portfolio holdings in getPortfolioSummary: {}", e.getMessage());
            }
        }
        if (equityHoldings == null) {
            equityHoldings = portfolioHoldingsService.getHoldings(portfolios);
        }
        
        var investmentValue = equityHoldings.stream()
                .mapToDouble(h -> h.getInvestmentCost() != null ? h.getInvestmentCost() : 0.0)
                .sum();

        // Use calculator to generate the summary
        PortfolioSummaryV1 summary = portfolioCalculator.calculateSummary(equityHoldings, investmentValue);
        summary.setInvestmentValue(investmentValue);
        if (pricedHoldings != null) {
            summary.setAsOf(pricedHoldings.getAsOf());
            summary.setPriceFreshness(pricedHoldings.getPriceFreshness());
            summary.setPriceSource(pricedHoldings.getPriceSource());
            summary.setSessionDate(pricedHoldings.getSessionDate());
        }
        return summary;
    }

    private PortfolioSummaryV1 repriceCachedSummary(
            PortfolioSummaryV1 cached, String userId, String portfolioId, TimeInterval interval) {
        try {
            com.portfolio.model.portfolio.PortfolioHoldings ph = portfolioId == null
                    ? portfolioHoldingsService.getPortfolioHoldings(userId, interval, true)
                    : portfolioHoldingsService.getPortfolioHoldings(userId, portfolioId, interval, true);
            if (ph == null || ph.getEquityHoldings() == null || ph.getEquityHoldings().isEmpty()) {
                // Still freeze day P&L from snapshots when holdings reprice is unavailable.
                applySessionDayGainLossIfMissing(cached, userId, portfolioId);
                return cached;
            }
            double investmentValue = cached.getInvestmentValue() != null
                    ? cached.getInvestmentValue()
                    : ph.getEquityHoldings().stream()
                            .mapToDouble(h -> h.getInvestmentCost() != null ? h.getInvestmentCost() : 0.0)
                            .sum();
            PortfolioSummaryV1 live = portfolioCalculator.calculateSummary(ph.getEquityHoldings(), investmentValue);
            cached.setInvestmentValue(investmentValue);
            cached.setCurrentValue(live.getCurrentValue());
            // Cost-basis overlay as fallback; period return re-applied below for 1W/1M/…
            cached.setTotalGainLoss(live.getTotalGainLoss());
            cached.setTotalGainLossPercentage(live.getTotalGainLossPercentage());
            cached.setTodayGainLoss(live.getTodayGainLoss());
            cached.setTodayGainLossPercentage(live.getTodayGainLossPercentage());
            cached.setTotalAssets(live.getTotalAssets());
            cached.setGainersCount(live.getGainersCount());
            cached.setLosersCount(live.getLosersCount());
            cached.setTodayGainersCount(live.getTodayGainersCount());
            cached.setTodayLosersCount(live.getTodayLosersCount());
            cached.setAsOf(ph.getAsOf());
            cached.setPriceFreshness(ph.getPriceFreshness());
            cached.setPriceSource(ph.getPriceSource());
            cached.setSessionDate(ph.getSessionDate());
            cached.setLastUpdated(java.time.LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
            // Keep interval-scoped Total Return (snapshot baseline), not all-time cost-basis.
            applyTimeframeGainLoss(cached, userId, portfolioId, interval);
            applySessionDayGainLossIfMissing(cached, userId, portfolioId);
            return cached;
        } catch (Exception e) {
            log.warn("Summary price overlay failed; serving cached KPIs: {}", e.getMessage());
            applySessionDayGainLossIfMissing(cached, userId, portfolioId);
            return cached;
        }
    }

    private Optional<PortfolioSummaryV1> getCachedSummary(String userId, TimeInterval interval) {
        return getCachedSummary(userId, interval, null);
    }

    private Optional<PortfolioSummaryV1> getCachedSummary(String userId, TimeInterval interval, String portfolioId) {
        log.debug("Checking cache for portfolio summary - User: {}, Interval: {}, Portfolio: {}",
                userId, interval != null ? interval.getCode() : "null", portfolioId);

        Optional<PortfolioSummaryV1> cachedSummary = Optional.empty();
        
        // Tier 1: Redis
        if (portfolioSummaryRedisService != null) {
            if (portfolioId == null) {
                cachedSummary = portfolioSummaryRedisService.getLatestSummary(userId, interval);
            } else {
                cachedSummary = portfolioSummaryRedisService.getLatestSummary(userId, interval, portfolioId);
            }
            if (cachedSummary.isPresent()) {
                log.info("Serving portfolio summary from Redis cache - User: {}, Interval: {}",
                        userId, interval != null ? interval.getCode() : "null");
                return cachedSummary;
            }
        }
        
        // Tier 2: Mongo
        if (portfolioSummaryMongoService != null) {
            cachedSummary = portfolioSummaryMongoService.getLatestFreshSummary(userId, interval, portfolioId);
            if (cachedSummary.isPresent()) {
                log.info("Serving portfolio summary from Mongo cache - User: {}, Interval: {}",
                        userId, interval != null ? interval.getCode() : "null");
                return cachedSummary;
            }
        }

        log.debug("No cached summary found for user: {}", userId);
        return cachedSummary;
    }

    private void applyTimeframeGainLoss(PortfolioSummaryV1 summary, String userId, 
                                          String portfolioId, TimeInterval interval) {
        if (interval == null || interval == TimeInterval.OVERALL) return;

        // Fetch snapshot history for the timeframe window (1D lookback already spans weekends).
        List<PortfolioSnapshotModel> history = portfolioSnapshotService.getHistory(
            userId, portfolioId, interval.getCode()
        );

        if (history == null || history.isEmpty()) {
            // Broader windows when the interval window is empty (new portfolios / catch-up lag).
            history = loadSnapshotHistory(userId, portfolioId);
            if (history == null || history.isEmpty()) return;
        }

        // For 1D: find the most recent snapshot NOT from today
        // For other timeframes: find the oldest snapshot (period start)
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        PortfolioSnapshotModel baselineSnap;

        if (TimeInterval.ONE_DAY.equals(interval)) {
            baselineSnap = history.stream()
                .filter(s -> s.getSnapshotDate() != null && !today.equals(s.getSnapshotDate()))
                .max(Comparator.comparing(PortfolioSnapshotModel::getSnapshotDate))
                .orElse(null);
        } else {
            baselineSnap = history.stream()
                .filter(s -> s.getSnapshotDate() != null)
                .min(Comparator.comparing(PortfolioSnapshotModel::getSnapshotDate))
                .orElse(null);
        }

        if (baselineSnap == null) return;

        double baselineWealth = (portfolioId != null && !portfolioId.isEmpty()
                && baselineSnap.getPortfolios() != null)
            ? (baselineSnap.getPortfolios().stream()
                  .filter(p -> portfolioId.equals(p.getPortfolioId()))
                  .mapToDouble(p -> p.getClose() != null ? p.getClose() : 0.0).sum())
            : (baselineSnap.getTotalUserWealth() != null ? baselineSnap.getTotalUserWealth() : 0.0);

        if (baselineWealth <= 0
                && baselineSnap.getTotalUserWealth() != null
                && baselineSnap.getTotalUserWealth() > 0) {
            // Portfolio id may be missing from snapshot entries — use user wealth.
            baselineWealth = baselineSnap.getTotalUserWealth();
        }
        if (baselineWealth <= 0) return;

        double currentValue = summary.getCurrentValue() != null ? summary.getCurrentValue() : 0.0;
        double gainLoss = currentValue - baselineWealth;
        double gainLossPct = (gainLoss / baselineWealth) * 100.0;

        summary.setTotalGainLoss(gainLoss);
        summary.setTotalGainLossPercentage(gainLossPct);
        // 1D period return is the session change — also fill Today's P&L when quotes lack previousClose.
        if (TimeInterval.ONE_DAY.equals(interval) && summary.getTodayGainLoss() == null) {
            summary.setTodayGainLoss(gainLoss);
            summary.setTodayGainLossPercentage(gainLossPct);
            log.info("[Overview] Mirrored 1D timeframe gain into todayGainLoss={}", gainLoss);
        }
    }

    /**
     * Industry freeze when quote previousClose is collapsed/missing after hours:
     * day P&L = last cash-session close − prior cash-session close.
     * On weekends, {@code current ≈ last session}, so using current−lastSnap yields ~0 —
     * prefer two non-today snapshots when available.
     * Only fills when per-symbol todayGainLoss is still null — never overwrites a live baseline.
     */
    private void applySessionDayGainLossIfMissing(
            PortfolioSummaryV1 summary, String userId, String portfolioId) {
        if (summary == null || summary.getTodayGainLoss() != null) {
            return;
        }
        if (portfolioSnapshotService == null || userId == null || userId.isBlank()) {
            return;
        }
        try {
            List<PortfolioSnapshotModel> history = loadSnapshotHistory(userId, portfolioId);
            if (history == null || history.isEmpty()) {
                log.info("[Overview] No snapshot history for day P&L fallback user={}", userId);
                return;
            }
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            List<PortfolioSnapshotModel> priorSessions = history.stream()
                    .filter(s -> s.getSnapshotDate() != null && !today.equals(s.getSnapshotDate()))
                    .sorted(Comparator.comparing(PortfolioSnapshotModel::getSnapshotDate).reversed())
                    .toList();
            if (priorSessions.isEmpty()) {
                log.info("[Overview] No non-today snapshot for day P&L fallback user={} today={}", userId, today);
                return;
            }

            PortfolioSnapshotModel lastSession = priorSessions.get(0);
            double lastWealth = snapshotWealth(lastSession, portfolioId);
            if (lastWealth <= 0) {
                log.info("[Overview] Snapshot baseline wealth <= 0 date={} portfolioId={}",
                        lastSession.getSnapshotDate(), portfolioId);
                return;
            }

            double currentValue = summary.getCurrentValue() != null ? summary.getCurrentValue() : 0.0;
            double dayGain;
            double dayPct;
            String mode;

            // Prefer close−open on the last session row (correct weekend freeze once EOD stores true open).
            double lastOpen = snapshotOpen(lastSession, portfolioId);
            if (lastOpen > 0 && isDistinctWealth(lastWealth, lastOpen)) {
                dayGain = lastWealth - lastOpen;
                dayPct = (dayGain / lastOpen) * 100.0;
                summary.setTodayGainLoss(dayGain);
                summary.setTodayGainLossPercentage(dayPct);
                log.info("[Overview] Filled todayGainLoss via session OHLC {} open={} close={} gain={}",
                        lastSession.getSnapshotDate(), lastOpen, lastWealth, dayGain);
                return;
            }

            if (priorSessions.size() >= 2) {
                PortfolioSnapshotModel priorSession = priorSessions.get(1);
                double priorWealth = snapshotWealth(priorSession, portfolioId);
                if (priorWealth > 0) {
                    // Frozen last-session day move (works on weekends when current≈lastWealth).
                    dayGain = lastWealth - priorWealth;
                    dayPct = (dayGain / priorWealth) * 100.0;
                    mode = "two-session " + priorSession.getSnapshotDate() + "→" + lastSession.getSnapshotDate();
                } else if (isDistinctWealth(currentValue, lastWealth)) {
                    dayGain = currentValue - lastWealth;
                    dayPct = (dayGain / lastWealth) * 100.0;
                    mode = "current-vs-last (prior wealth missing)";
                } else {
                    log.info("[Overview] Cannot freeze day P&L — prior session wealth missing date={}",
                            priorSession.getSnapshotDate());
                    return;
                }
            } else if (isDistinctWealth(currentValue, lastWealth)) {
                dayGain = currentValue - lastWealth;
                dayPct = (dayGain / lastWealth) * 100.0;
                mode = "current-vs-last " + lastSession.getSnapshotDate();
            } else {
                // Single snap equals mark — weekend/holiday with no prior session row.
                log.info("[Overview] Single snapshot equals mark; cannot freeze day P&L date={} wealth={}",
                        lastSession.getSnapshotDate(), lastWealth);
                return;
            }

            summary.setTodayGainLoss(dayGain);
            summary.setTodayGainLossPercentage(dayPct);
            log.info("[Overview] Filled todayGainLoss via {} gain={}", mode, dayGain);
        } catch (Exception e) {
            log.warn("[Overview] Snapshot day P&L fallback failed: {}", e.getMessage());
        }
    }

    private List<PortfolioSnapshotModel> loadSnapshotHistory(String userId, String portfolioId) {
        List<PortfolioSnapshotModel> history = portfolioSnapshotService.getHistory(userId, portfolioId, "1M");
        if (history == null || history.isEmpty()) {
            history = portfolioSnapshotService.getHistory(userId, null, "1M");
        }
        if (history == null || history.isEmpty()) {
            history = portfolioSnapshotService.getHistory(userId, portfolioId, "1W");
        }
        if (history == null || history.isEmpty()) {
            history = portfolioSnapshotService.getHistory(userId, null, "1W");
        }
        return history;
    }

    private static double snapshotWealth(PortfolioSnapshotModel snap, String portfolioId) {
        double wealth = 0.0;
        if (portfolioId != null && !portfolioId.isEmpty() && snap.getPortfolios() != null) {
            wealth = snap.getPortfolios().stream()
                    .filter(p -> portfolioId.equals(p.getPortfolioId()))
                    .mapToDouble(p -> p.getClose() != null ? p.getClose() : 0.0)
                    .sum();
        }
        if (wealth <= 0 && snap.getTotalUserWealth() != null) {
            wealth = snap.getTotalUserWealth();
        }
        return wealth;
    }

    private static double snapshotOpen(PortfolioSnapshotModel snap, String portfolioId) {
        double open = 0.0;
        if (portfolioId != null && !portfolioId.isEmpty() && snap.getPortfolios() != null) {
            open = snap.getPortfolios().stream()
                    .filter(p -> portfolioId.equals(p.getPortfolioId()))
                    .mapToDouble(p -> p.getOpen() != null ? p.getOpen() : 0.0)
                    .sum();
        }
        if (open <= 0 && snap.getTotalUserWealthOpen() != null) {
            open = snap.getTotalUserWealthOpen();
        }
        return open;
    }

    private static boolean isDistinctWealth(double a, double b) {
        if (a <= 0 || b <= 0) {
            return false;
        }
        double rel = Math.abs(a - b) / Math.max(Math.abs(a), Math.abs(b));
        return rel > 0.0005; // >5 bps
    }
}
