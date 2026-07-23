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

    public PortfolioAnalyticsFacade(AnalyticsFactory analyticsFactory, 
                                    @Qualifier("taskExecutor") Executor taskExecutor,
                                    PortfolioService portfolioService,
                                    MarketDataService marketDataService) {
        this.analyticsFactory = analyticsFactory;
        this.taskExecutor = taskExecutor;
        this.portfolioService = portfolioService;
        this.marketDataService = marketDataService;
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
                        request.setPrefetchAttempted(true);
                        Map<String, MarketData> prefetched = marketDataService.getMarketData(symbols);
                        if (prefetched != null) {
                            // Ensure normalized keys so analytics providers can look them up successfully
                            Map<String, MarketData> normalizedPrefetch = new java.util.HashMap<>();
                            for (Map.Entry<String, MarketData> entry : prefetched.entrySet()) {
                                if (entry.getValue() != null) {
                                    String cleaned = com.portfolio.model.util.SymbolResolver.normalize(
                                            entry.getKey().contains(":") ? entry.getKey().substring(entry.getKey().indexOf(':') + 1) : entry.getKey()
                                    );
                                    normalizedPrefetch.put(cleaned, entry.getValue());
                                }
                            }
                            request.setPrefetchedMarketData(normalizedPrefetch);
                        }
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

        // Start futures
        if (request.getFeatureToggles().isIncludeHeatmap()) {
            heatmapFuture = CompletableFuture.supplyAsync(() -> generateSectorHeatmap(request), taskExecutor);
        }
        
        if (request.getFeatureToggles().isIncludeMovers()) {
            moversFuture = CompletableFuture.supplyAsync(() -> getTopGainersLosers(request), taskExecutor);
        }
        
        if (request.getFeatureToggles().isIncludeSectorAllocation()) {
            sectorAllocationFuture = CompletableFuture.supplyAsync(() -> calculateSectorAllocations(request), taskExecutor);
        }
        
        if (request.getFeatureToggles().isIncludeMarketCapAllocation()) {
            marketCapAllocationFuture = CompletableFuture.supplyAsync(() -> calculateMarketCapAllocations(request), taskExecutor);
        }
        
        // Join futures and populate builder
        if (heatmapFuture != null) {
            analyticsBuilder.heatmap(heatmapFuture.join());
        }
        
        if (moversFuture != null) {
            analyticsBuilder.movers(moversFuture.join());
        }
        
        if (sectorAllocationFuture != null) {
            analyticsBuilder.sectorAllocation(sectorAllocationFuture.join());
        }
        
        if (marketCapAllocationFuture != null) {
            analyticsBuilder.marketCapAllocation(marketCapAllocationFuture.join());
        }
        
        // Add the analytics component to the response
        responseBuilder.analytics(analyticsBuilder.build());
        
        return responseBuilder.build();
    }
}
