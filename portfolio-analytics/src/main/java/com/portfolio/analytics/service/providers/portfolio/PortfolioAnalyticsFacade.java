package com.portfolio.analytics.service.providers.portfolio;

import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.AnalyticsFactory;
import com.portfolio.model.analytics.AnalyticsComponent;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.response.AdvancedAnalyticsResponse;
import com.am.common.amcommondata.service.PortfolioService;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.analytics.intelligence.AggregatePortfolioKeys;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.ConcurrentHashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Facade service for portfolio analytics that delegates to the appropriate providers
 * This service follows the same pattern as IndexAnalyticsFacade but for portfolio-specific analytics
 */
@Service
@Slf4j
public class PortfolioAnalyticsFacade {

    public static final String FRESHNESS_LIVE = "LIVE";
    public static final String FRESHNESS_WARMING = "WARMING";
    public static final String FRESHNESS_PERIOD = "PERIOD";

    private static final long ADVANCED_DEADLINE_MS = 900L;
    private static final long L1_PERIOD_TTL_MS = 60_000L;
    private static final long L1_LIVE_TTL_MS = 3_000L;
    
    private final AnalyticsFactory analyticsFactory;
    private final Executor taskExecutor;
    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final com.portfolio.analytics.service.utils.SecurityDetailsService securityDetailsService;
    private final AdvancedPeriodWarmService advancedPeriodWarmService;
    private final com.portfolio.redis.service.PortfolioHeatmapRedisService heatmapRedisService;
    private final Map<String, CachedResponse> fastCache = new ConcurrentHashMap<>();

    private static class CachedResponse {
        final AdvancedAnalyticsResponse response;
        final long createdAt;
        final long ttlMs;

        CachedResponse(AdvancedAnalyticsResponse response, long ttlMs) {
            this.response = response;
            this.createdAt = System.currentTimeMillis();
            this.ttlMs = ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > ttlMs;
        }
    }

    public PortfolioAnalyticsFacade(AnalyticsFactory analyticsFactory, 
                                    @Qualifier("taskExecutor") Executor taskExecutor,
                                    PortfolioService portfolioService,
                                    MarketDataService marketDataService,
                                    com.portfolio.analytics.service.utils.SecurityDetailsService securityDetailsService,
                                    @org.springframework.context.annotation.Lazy AdvancedPeriodWarmService advancedPeriodWarmService,
                                    @org.springframework.lang.Nullable
                                    com.portfolio.redis.service.PortfolioHeatmapRedisService heatmapRedisService) {
        this.analyticsFactory = analyticsFactory;
        this.taskExecutor = taskExecutor;
        this.portfolioService = portfolioService;
        this.marketDataService = marketDataService;
        this.securityDetailsService = securityDetailsService;
        this.advancedPeriodWarmService = advancedPeriodWarmService;
        this.heatmapRedisService = heatmapRedisService;
    }

    /** Drop L1 entries for a portfolio (after period warm or holdings change). */
    public void invalidateL1(String portfolioId) {
        if (portfolioId == null) {
            return;
        }
        fastCache.keySet().removeIf(k -> k.startsWith(portfolioId + "|"));
    }
    
    /**
     * Generate sector heatmap for a portfolio
     * @param request The request containing the portfolio ID
     * @return Heatmap containing sector performances
     */
    public Heatmap generateSectorHeatmap(AdvancedAnalyticsRequest request) {
        log.info("Generating sector heatmap for portfolio: {}", request.getCoreIdentifiers().getPortfolioId());
        return analyticsFactory.generatePortfolioAnalytics(AnalyticsType.SECTOR_HEATMAP, request);
    }
    
    /**
     * Get top gainers and losers for a portfolio
     * @param request The request
     * @return GainerLoser object containing top performers and underperformers
     */
    public GainerLoser getTopGainersLosers(AdvancedAnalyticsRequest request) {
        log.info("Getting top {} gainers and losers for portfolio: {}", request.getFeatureConfiguration().getMoversLimit(), request.getCoreIdentifiers().getPortfolioId());
        return analyticsFactory.generatePortfolioAnalytics(AnalyticsType.TOP_MOVERS, request);
    }
    
    /**
     * Calculate sector and industry allocation percentages for a portfolio`
     * @param request The request
     * @return SectorAllocation containing sector and industry weights
     */
    public SectorAllocation calculateSectorAllocations(AdvancedAnalyticsRequest request) {
        log.info("Calculating sector allocations for portfolio: {}", request.getCoreIdentifiers().getPortfolioId());
        return analyticsFactory.generatePortfolioAnalytics(AnalyticsType.SECTOR_ALLOCATION, request);
    }
    
    /**
     * Calculate market capitalization allocation for a portfolio
     * @param request The request
     * @return MarketCapAllocation containing breakdown by market cap segments
     */
    public MarketCapAllocation calculateMarketCapAllocations(AdvancedAnalyticsRequest request) {
        log.info("Calculating market cap allocations for portfolio: {}", request.getCoreIdentifiers().getPortfolioId());
        return analyticsFactory.generatePortfolioAnalytics(AnalyticsType.MARKET_CAP_ALLOCATION, request);
    }
    
    /**
     * Calculate advanced analytics combining multiple data points based on request parameters
     * @param request The advanced analytics request containing parameters and flags
     * @return AdvancedAnalyticsResponse with requested analytics components
     */
    public AdvancedAnalyticsResponse calculateAdvancedAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Calculating advanced analytics for portfolio: {} from {} to {}", 
                request.getCoreIdentifiers().getPortfolioId(), request.getFromDate(), request.getToDate());

        final long deadlineAt = System.nanoTime() + ADVANCED_DEADLINE_MS * 1_000_000L;
        
        // Generate a cache key based on the request parameters (include TF enum)
        String tfCode = request.getTimeFrame() != null ? request.getTimeFrame().getValue() : "live";
        String cacheKey = String.format("%s|%s|%s|%s|H:%b|M:%b|S:%b|C:%b",
                request.getCoreIdentifiers().getPortfolioId(),
                request.getFromDate(),
                request.getToDate(),
                tfCode,
                request.getFeatureToggles().isIncludeHeatmap(),
                request.getFeatureToggles().isIncludeMovers(),
                request.getFeatureToggles().isIncludeSectorAllocation(),
                request.getFeatureToggles().isIncludeMarketCapAllocation());
        
        CachedResponse cached = fastCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.info("[Optimization] Serving AdvancedAnalytics from fast in-memory cache for key: {}", cacheKey);
            return cached.response;
        }

        boolean periodHeatmapReady = false;
        String portfolioId = request.getCoreIdentifiers().getPortfolioId();
        if (heatmapRedisService != null && request.getFeatureToggles().isIncludeHeatmap()) {
            try {
                periodHeatmapReady = heatmapRedisService
                        .getCachedHeatmap(portfolioId, request)
                        .filter(h -> h.getSectors() != null && !h.getSectors().isEmpty())
                        .isPresent();
            } catch (Exception e) {
                log.debug("[AdvancedAnalytics] heatmap Redis probe failed: {}", e.getMessage());
            }
        }

        // Start building the response
        AdvancedAnalyticsResponse.AdvancedAnalyticsResponseBuilder responseBuilder = AdvancedAnalyticsResponse.builder()
                .portfolioId(portfolioId)
                .startDate(request.getFromDate())
                .endDate(request.getToDate())
                .timestamp(java.time.Instant.now());
        
        // Add comparison index if provided
        if (request.getCoreIdentifiers().getComparisonIndexSymbol() != null && !request.getCoreIdentifiers().getComparisonIndexSymbol().isEmpty()) {
            responseBuilder.comparisonIndexSymbol(request.getCoreIdentifiers().getComparisonIndexSymbol());
        }

        boolean sameDay = com.portfolio.marketdata.util.HistoricalDataRequestFactory
                .isSameDayLiveWindow(request.getTimeFrameRequest());
        String freshness = periodHeatmapReady && !sameDay
                ? FRESHNESS_PERIOD
                : (sameDay ? FRESHNESS_LIVE : FRESHNESS_WARMING);
        responseBuilder.analyticsFreshness(freshness);
        
        // --- OHLC-ONLY PREFETCH (never block HTTP on START_END hist) ---
        request.setAllowHeatmapRedisWrite(false);
        try {
            PortfolioModelV1 portfolio = request.getPrefetchedPortfolio();
            if (portfolio == null) {
                String pid = portfolioId;
                if (pid != null && !pid.isBlank()
                        && !AggregatePortfolioKeys.RESPONSE_PORTFOLIO_ID.equalsIgnoreCase(pid)) {
                    UUID portfolioUuid = UUID.fromString(pid);
                    portfolio = portfolioService.getPortfolioById(portfolioUuid);
                    if (portfolio != null) {
                        request.setPrefetchedPortfolio(portfolio);
                    }
                }
            }
            if (portfolio != null
                    && portfolio.getEquityModels() != null
                    && !portfolio.getEquityModels().isEmpty()) {
                List<String> symbols = portfolio.getEquityModels().stream()
                        .map(EquityModel::getSymbol)
                        .filter(s -> s != null && !s.isEmpty())
                        .collect(Collectors.toList());

                if (!symbols.isEmpty()) {
                    log.info("[Optimization] OHLC prefetch (skipHistRepair) for {} symbols", symbols.size());
                    long remainMs = Math.max(50L, (deadlineAt - System.nanoTime()) / 1_000_000L);
                    CompletableFuture<Map<String, MarketData>> liveFuture = CompletableFuture.supplyAsync(
                            () -> marketDataService.getMarketData(symbols, true), taskExecutor);
                    boolean moversOnly = request.getFeatureToggles().isIncludeMovers()
                            && !request.getFeatureToggles().isIncludeHeatmap()
                            && !request.getFeatureToggles().isIncludeSectorAllocation()
                            && !request.getFeatureToggles().isIncludeMarketCapAllocation();
                    CompletableFuture<Map<String, com.am.common.amcommondata.model.security.SecurityModel>> secFuture =
                            moversOnly
                                    ? CompletableFuture.completedFuture(Collections.emptyMap())
                                    : CompletableFuture.supplyAsync(
                                            () -> securityDetailsService.getSecurityDetails(symbols), taskExecutor);
                    try {
                        liveFuture.get(remainMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        log.warn("[AdvancedAnalytics] OHLC prefetch budget hit: {}", e.getMessage());
                    }
                    Map<String, MarketData> normalizedLive = normalizePrefetch(liveFuture.getNow(Collections.emptyMap()));
                    // Always mark attempted so providers never fan out hist on the HTTP path.
                    request.setPrefetchAttempted(true);
                    if (!normalizedLive.isEmpty()) {
                        request.setPrefetchedLiveMarketData(normalizedLive);
                        // Paint structure from live; PERIOD heatmap comes from Redis when warm.
                        request.setPrefetchedMarketData(normalizedLive);
                    }
                    try {
                        long secRemain = Math.max(20L, (deadlineAt - System.nanoTime()) / 1_000_000L);
                        secFuture.get(secRemain, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (Exception ignored) {
                        // fail-open
                    }
                    Map<String, com.am.common.amcommondata.model.security.SecurityModel> secs =
                            secFuture.getNow(Collections.emptyMap());
                    if (secs != null && !secs.isEmpty()) {
                        request.setPrefetchedSecurityDetails(secs);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to prefetch OHLC in facade; providers may return empty.", e);
            request.setPrefetchAttempted(true);
        }
        // ---------------------------------

        // Build analytics component with requested features
        AnalyticsComponent.AnalyticsComponentBuilder analyticsBuilder = AnalyticsComponent.builder();
        
        CompletableFuture<Heatmap> heatmapFuture = null;
        CompletableFuture<GainerLoser> moversFuture = null;
        CompletableFuture<SectorAllocation> sectorAllocationFuture = null;
        CompletableFuture<MarketCapAllocation> marketCapAllocationFuture = null;

        // Start futures with prefetched data in place
        final AdvancedAnalyticsRequest frozenRequest = request;
        if (request.getFeatureToggles().isIncludeHeatmap()) {
            heatmapFuture = CompletableFuture.supplyAsync(() -> generateSectorHeatmap(frozenRequest), taskExecutor);
        }
        
        if (request.getFeatureToggles().isIncludeMovers()) {
            moversFuture = CompletableFuture.supplyAsync(() -> getTopGainersLosers(frozenRequest), taskExecutor);
        }
        
        if (request.getFeatureToggles().isIncludeSectorAllocation()) {
            sectorAllocationFuture = CompletableFuture.supplyAsync(() -> calculateSectorAllocations(frozenRequest), taskExecutor);
        }
        
        if (request.getFeatureToggles().isIncludeMarketCapAllocation()) {
            marketCapAllocationFuture = CompletableFuture.supplyAsync(() -> calculateMarketCapAllocations(frozenRequest), taskExecutor);
        }
        
        // Join futures and populate builder — remaining deadline only (never 60s)
        java.util.List<CompletableFuture<?>> allFutures = java.util.stream.Stream
            .of(heatmapFuture, moversFuture, sectorAllocationFuture, marketCapAllocationFuture)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());
            
        try {
            if (!allFutures.isEmpty()) {
                long joinMs = Math.max(50L, (deadlineAt - System.nanoTime()) / 1_000_000L);
                CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                    .get(joinMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("[AdvancedAnalytics] Partial timeout after {}ms — returning completed components only",
                    ADVANCED_DEADLINE_MS);
        } catch (Exception e) {
            log.error("[AdvancedAnalytics] Error joining futures", e);
        }
        
        if (heatmapFuture != null && heatmapFuture.isDone() && !heatmapFuture.isCompletedExceptionally()) {
            analyticsBuilder.heatmap(heatmapFuture.getNow(null));
        }
        
        if (moversFuture != null && moversFuture.isDone() && !moversFuture.isCompletedExceptionally()) {
            analyticsBuilder.movers(moversFuture.getNow(null));
        }
        
        if (sectorAllocationFuture != null && sectorAllocationFuture.isDone() && !sectorAllocationFuture.isCompletedExceptionally()) {
            analyticsBuilder.sectorAllocation(sectorAllocationFuture.getNow(null));
        }
        
        if (marketCapAllocationFuture != null && marketCapAllocationFuture.isDone() && !marketCapAllocationFuture.isCompletedExceptionally()) {
            analyticsBuilder.marketCapAllocation(marketCapAllocationFuture.getNow(null));
        }
        
        // Add the analytics component to the response
        responseBuilder.analytics(analyticsBuilder.build());
        
        // Compute lightweight summary if prefetched portfolio is available
        if (request.getPrefetchedPortfolio() != null) {
            try {
                PortfolioModelV1 portfolio = request.getPrefetchedPortfolio();
                Map<String, MarketData> mdMap = request.getPrefetchedLiveMarketData() != null
                        && !request.getPrefetchedLiveMarketData().isEmpty()
                    ? request.getPrefetchedLiveMarketData()
                    : request.getPrefetchedMarketData();
                double totalInvested = 0.0;
                double totalCurrent = 0.0;
                double todayGainLoss = 0.0;
                int gainers = 0;
                int losers = 0;
                int todayGainers = 0;
                int todayLosers = 0;
                int activeCount = 0;

                if (portfolio.getEquityModels() != null) {
                    for (EquityModel eq : portfolio.getEquityModels()) {
                        if (eq == null || eq.getQuantity() == null || eq.getQuantity() <= 0) continue;
                        activeCount++;
                        double qty = eq.getQuantity();
                        double cost = (eq.getAvgBuyingPrice() != null ? eq.getAvgBuyingPrice() : 0.0) * qty;
                        totalInvested += cost;

                        String sym = com.portfolio.model.util.SymbolResolver.normalize(eq.getSymbol());
                        MarketData md = mdMap != null ? mdMap.get(sym) : null;
                        double ltp = (md != null && md.getLastPrice() != null && md.getLastPrice() > 0)
                            ? md.getLastPrice()
                            : (eq.getAvgBuyingPrice() != null ? eq.getAvgBuyingPrice() : 0.0);
                        double val = ltp * qty;
                        totalCurrent += val;

                        double overallGL = val - cost;
                        if (overallGL > 0) gainers++; else if (overallGL < 0) losers++;

                        Double prevClose = (md != null && md.getPreviousClose() != null && md.getPreviousClose() > 0)
                            ? md.getPreviousClose()
                            : null;

                        if (prevClose != null && prevClose > 0) {
                            double dayGL = (ltp - prevClose) * qty;
                            todayGainLoss += dayGL;
                            if (dayGL > 0) todayGainers++; else if (dayGL < 0) todayLosers++;
                        }
                    }
                }

                if (totalCurrent == 0.0 && totalInvested > 0) {
                    totalCurrent = totalInvested;
                }
                double totalGL = totalCurrent - totalInvested;
                double totalGLPct = totalInvested > 0 ? (totalGL / totalInvested) * 100.0 : 0.0;
                double prevVal = totalCurrent - todayGainLoss;
                double todayGLPct = prevVal > 0 ? (todayGainLoss / prevVal) * 100.0 : 0.0;

                com.portfolio.model.portfolio.v1.PortfolioSummaryV1 summary = com.portfolio.model.portfolio.v1.PortfolioSummaryV1.builder()
                    .investmentValue(BigDecimal.valueOf(totalInvested).setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .currentValue(BigDecimal.valueOf(totalCurrent).setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .totalGainLoss(BigDecimal.valueOf(totalGL).setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .totalGainLossPercentage(BigDecimal.valueOf(totalGLPct).setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .todayGainLoss(BigDecimal.valueOf(todayGainLoss).setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .todayGainLossPercentage(BigDecimal.valueOf(todayGLPct).setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .totalAssets(activeCount)
                    .gainersCount(gainers)
                    .losersCount(losers)
                    .todayGainersCount(todayGainers)
                    .todayLosersCount(todayLosers)
                    .lastUpdated(java.time.LocalDateTime.now())
                    .build();

                responseBuilder.summary(summary);
            } catch (Exception e) {
                log.warn("Failed to compute in-memory summary in facade", e);
            }
        }
        
        AdvancedAnalyticsResponse finalResponse = responseBuilder.build();

        // Kick background START_END warm (never await) when period not yet ready.
        if (!FRESHNESS_PERIOD.equals(finalResponse.getAnalyticsFreshness())
                && request.getPrefetchedPortfolio() != null
                && advancedPeriodWarmService != null) {
            try {
                advancedPeriodWarmService.warmAsync(request);
            } catch (Exception e) {
                log.debug("[AdvancedAnalytics] warm enqueue failed: {}", e.getMessage());
            }
        }

        // Never L1-cache empty heatmap/movers — CB/timeouts must not poison cache.
        boolean emptyHeatmap = finalResponse.getAnalytics() == null
                || finalResponse.getAnalytics().getHeatmap() == null
                || finalResponse.getAnalytics().getHeatmap().getSectors() == null
                || finalResponse.getAnalytics().getHeatmap().getSectors().isEmpty();
        boolean moversRequested = request.getFeatureToggles() != null
                && request.getFeatureToggles().isIncludeMovers();
        boolean emptyMovers = true;
        if (finalResponse.getAnalytics() != null && finalResponse.getAnalytics().getMovers() != null) {
            var movers = finalResponse.getAnalytics().getMovers();
            boolean hasGainers = movers.getTopGainers() != null && !movers.getTopGainers().isEmpty();
            boolean hasLosers = movers.getTopLosers() != null && !movers.getTopLosers().isEmpty();
            emptyMovers = !hasGainers && !hasLosers;
        }
        boolean skipL1 = (request.getFeatureToggles() != null
                && request.getFeatureToggles().isIncludeHeatmap()
                && emptyHeatmap)
                || (moversRequested && emptyMovers);
        if (!skipL1) {
            long ttl = FRESHNESS_PERIOD.equals(finalResponse.getAnalyticsFreshness())
                    ? L1_PERIOD_TTL_MS
                    : L1_LIVE_TTL_MS;
            fastCache.put(cacheKey, new CachedResponse(finalResponse, ttl));
        } else {
            log.warn("[Optimization] Skipping L1 cache (emptyHeatmap={} emptyMovers={} key={})",
                    emptyHeatmap, emptyMovers, cacheKey);
        }

        return finalResponse;
    }

    private static Map<String, MarketData> normalizePrefetch(Map<String, MarketData> prefetched) {
        if (prefetched == null || prefetched.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, MarketData> normalized = new java.util.HashMap<>();
        for (Map.Entry<String, MarketData> entry : prefetched.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String cleaned = com.portfolio.model.util.SymbolResolver.normalize(
                    entry.getKey().contains(":")
                            ? entry.getKey().substring(entry.getKey().indexOf(':') + 1)
                            : entry.getKey());
            normalized.put(cleaned, entry.getValue());
        }
        return normalized;
    }
}
