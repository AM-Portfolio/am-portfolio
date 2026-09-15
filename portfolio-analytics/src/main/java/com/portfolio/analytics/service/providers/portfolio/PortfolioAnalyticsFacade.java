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
    
    private final AnalyticsFactory analyticsFactory;
    private final Executor taskExecutor;
    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final com.portfolio.analytics.service.utils.SecurityDetailsService securityDetailsService;
    private final Map<String, CachedResponse> fastCache = new ConcurrentHashMap<>();

    private static class CachedResponse {
        final AdvancedAnalyticsResponse response;
        final long createdAt;

        CachedResponse(AdvancedAnalyticsResponse response) {
            this.response = response;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 60_000; // 60s TTL for L1 Cache
        }
    }

    public PortfolioAnalyticsFacade(AnalyticsFactory analyticsFactory, 
                                    @Qualifier("taskExecutor") Executor taskExecutor,
                                    PortfolioService portfolioService,
                                    MarketDataService marketDataService,
                                    com.portfolio.analytics.service.utils.SecurityDetailsService securityDetailsService) {
        this.analyticsFactory = analyticsFactory;
        this.taskExecutor = taskExecutor;
        this.portfolioService = portfolioService;
        this.marketDataService = marketDataService;
        this.securityDetailsService = securityDetailsService;
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

        // Start building the response
        AdvancedAnalyticsResponse.AdvancedAnalyticsResponseBuilder responseBuilder = AdvancedAnalyticsResponse.builder()
                .portfolioId(request.getCoreIdentifiers().getPortfolioId())
                .startDate(request.getFromDate())
                .endDate(request.getToDate())
                .timestamp(java.time.Instant.now());
        
        // Add comparison index if provided
        if (request.getCoreIdentifiers().getComparisonIndexSymbol() != null && !request.getCoreIdentifiers().getComparisonIndexSymbol().isEmpty()) {
            responseBuilder.comparisonIndexSymbol(request.getCoreIdentifiers().getComparisonIndexSymbol());
        }
        
        // --- PREFETCH MARKET DATA ONCE ---
        try {
            UUID portfolioUuid = UUID.fromString(request.getCoreIdentifiers().getPortfolioId());
            PortfolioModelV1 portfolio = portfolioService.getPortfolioById(portfolioUuid);
            if (portfolio != null) {
                request.setPrefetchedPortfolio(portfolio);
                
                if (portfolio.getEquityModels() != null && !portfolio.getEquityModels().isEmpty()) {
                    List<String> symbols = portfolio.getEquityModels().stream()
                            .map(EquityModel::getSymbol)
                            .filter(s -> s != null && !s.isEmpty())
                            .collect(Collectors.toList());
                    
                    if (!symbols.isEmpty()) {
                        log.info("[Optimization] Prefetching market data once for {} symbols", symbols.size());
                        boolean needsHist = request.getTimeFrameRequest() != null
                                && (request.getFeatureToggles().isIncludeHeatmap()
                                    || request.getFeatureToggles().isIncludeSectorAllocation()
                                    || request.getFeatureToggles().isIncludeMarketCapAllocation());
                        // Live always: movers day%, summary today%, and live/1D primary path.
                        Map<String, MarketData> livePrefetch = marketDataService.getMarketData(symbols);
                        Map<String, MarketData> periodPrefetch = null;

                        if (needsHist) {
                            var tfr = request.getTimeFrameRequest();
                            if (com.portfolio.marketdata.util.HistoricalDataRequestFactory.isSameDayLiveWindow(tfr)) {
                                log.info("[SmartRoute] Same-day hist window — using live for period prefetch");
                                periodPrefetch = livePrefetch;
                            } else {
                                var histReq = com.portfolio.marketdata.util.HistoricalDataRequestFactory
                                        .forPeriodEndpoints(symbols, tfr);
                                periodPrefetch = marketDataService.getHistoricalData(histReq);
                            }
                        }

                        Map<String, MarketData> primary = needsHist ? periodPrefetch : livePrefetch;
                        Map<String, MarketData> normalizedPrimary = normalizePrefetch(primary);
                        Map<String, MarketData> normalizedLive = normalizePrefetch(livePrefetch);

                        // Never put live into period primary — that makes period % = day %.
                        // Live stays on prefetchedLiveMarketData for movers / summary / structure paint.
                        if (needsHist && (normalizedPrimary == null || normalizedPrimary.isEmpty())) {
                            log.warn(
                                    "[Optimization] Hist prefetch empty — not using live as period primary "
                                            + "(providers may fan out; live kept for movers/summary only)");
                            request.setPrefetchAttempted(false);
                        } else if (normalizedPrimary != null && !normalizedPrimary.isEmpty()) {
                            request.setPrefetchAttempted(true);
                            request.setPrefetchedMarketData(normalizedPrimary);
                        } else {
                            request.setPrefetchAttempted(false);
                            log.warn("[Optimization] Primary prefetch empty — providers may fan out with corrected requests");
                        }
                        if (normalizedLive != null && !normalizedLive.isEmpty()) {
                            request.setPrefetchedLiveMarketData(normalizedLive);
                        }

                        // --- PREFETCH SECURITY DETAILS ONCE ---
                        log.info("[Optimization] Prefetching security details once for {} symbols", symbols.size());
                        Map<String, com.am.common.amcommondata.model.security.SecurityModel> prefetchedSecurities =
                            securityDetailsService.getSecurityDetails(symbols);
                        request.setPrefetchedSecurityDetails(prefetchedSecurities);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to prefetch market data in facade. Providers will fallback to fetching individually.", e);
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
        
        // Join futures and populate builder
        java.util.List<CompletableFuture<?>> allFutures = java.util.stream.Stream
            .of(heatmapFuture, moversFuture, sectorAllocationFuture, marketCapAllocationFuture)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());
            
        try {
            if (!allFutures.isEmpty()) {
                CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                    .get(60, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("[AdvancedAnalytics] Partial timeout after 60s — returning completed components only");
        } catch (Exception e) {
            log.error("[AdvancedAnalytics] Error joining futures", e);
        }
        
        if (heatmapFuture != null && heatmapFuture.isDone() && !heatmapFuture.isCompletedExceptionally()) {
            analyticsBuilder.heatmap(heatmapFuture.getNow(null));
        }
        
        // Movers get an isolated extra wait so a slow heatmap/allocation does not null them out
        if (moversFuture != null) {
            try {
                if (!moversFuture.isDone()) {
                    moversFuture.get(12, java.util.concurrent.TimeUnit.SECONDS);
                }
                if (moversFuture.isDone() && !moversFuture.isCompletedExceptionally()) {
                    analyticsBuilder.movers(moversFuture.getNow(null));
                }
            } catch (java.util.concurrent.TimeoutException e) {
                log.warn("[AdvancedAnalytics] Movers still running after extra wait — leaving null for client fallback");
            } catch (Exception e) {
                log.warn("[AdvancedAnalytics] Movers future error: {}", e.getMessage());
            }
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
        // Never L1-cache empty heatmap/allocation — CB/timeouts must not poison 60s.
        boolean emptyHeatmap = finalResponse.getAnalytics() == null
                || finalResponse.getAnalytics().getHeatmap() == null
                || finalResponse.getAnalytics().getHeatmap().getSectors() == null
                || finalResponse.getAnalytics().getHeatmap().getSectors().isEmpty();
        if (!emptyHeatmap) {
            fastCache.put(cacheKey, new CachedResponse(finalResponse));
        } else {
            log.warn("[Optimization] Skipping L1 cache for empty heatmap key={}", cacheKey);
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
