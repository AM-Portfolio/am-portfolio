package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.analytics.service.utils.TopMoverUtils;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provider for portfolio top movers (gainers and losers) analytics
 */
@Service
@Slf4j
public class PortfolioTopMoversProvider extends AbstractPortfolioAnalyticsProvider<GainerLoser> {

    public static final int DEFAULT_LIMIT = 5;

    public PortfolioTopMoversProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(portfolioService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.TOP_MOVERS;
    }

    @Override
    public GainerLoser generateAnalytics(AdvancedAnalyticsRequest request) {
        // Default to the default limit from TopMoverUtils
        return generateAnalytics(request.getCoreIdentifiers().getPortfolioId(), request);
    }
    
    @Override
    public GainerLoser generateAnalytics(String portfolioId, AdvancedAnalyticsRequest request) {
        // Extract movers limit from feature configuration
        Integer moversLimit = request.getFeatureConfiguration().getMoversLimit();
        int limit = moversLimit != null ? moversLimit : 
                    (request.getPagination().isReturnAllData() ? DEFAULT_LIMIT : request.getPagination().getSize());
        
        log.info("Generating top {} movers for portfolio {} using Hybrid Architecture", 
                limit, portfolioId);
        
        return processPortfolioDataHybrid(
            portfolioId,
            request.getTimeFrameRequest(),
            this::createEmptyResponse,
            
            // Primary Engine: Market Data API
            (portfolio, portfolioSymbols, marketData) -> {
                // Get sector information for symbols
                Map<String, String> symbolSectors = securityDetailsService.getSymbolMapSectors(portfolioSymbols);
                
                // Calculate top movers using the determined limit and include sector information
                return com.portfolio.analytics.service.utils.TopMoverUtils.buildTopMoversResponse(marketData, limit, portfolioId, true, symbolSectors);
            },
            
            // Fallback Engine: MongoDB local extraction
            (portfolio) -> {
                double totalValue = portfolio.getEquityModels().stream()
                    .mapToDouble(e -> e.getCurrentValue() != null ? e.getCurrentValue() : 0.0)
                    .sum();
                    
                List<GainerLoser.StockMovement> allMovements = portfolio.getEquityModels().stream()
                    .map(e -> {
                        double currentValue = e.getCurrentValue() != null ? e.getCurrentValue() : 0.0;
                        double weight = totalValue > 0 ? (currentValue / totalValue) * 100.0 : 0.0;
                        
                        return GainerLoser.StockMovement.builder()
                            .symbol(e.getSymbol())
                            .companyName(e.getCompanyName() != null ? e.getCompanyName() : e.getName())
                            .lastPrice(e.getCurrentPrice() != null ? e.getCurrentPrice() : 0.0)
                            .changeAmount(e.getTodayProfitLoss() != null ? e.getTodayProfitLoss() : 0.0)
                            .changePercent(e.getTodayProfitLossPercentage() != null ? e.getTodayProfitLossPercentage() : 0.0)
                            .sector(e.getSector() != null ? e.getSector() : "Other")
                            .quantity(e.getQuantity() != null ? e.getQuantity() : 0.0)
                            .marketValue(currentValue)
                            .weightPercentage(weight)
                            .build();
                    })
                    .collect(Collectors.toList());
                    
                // Sort by changePercent descending
                allMovements.sort(Comparator.comparing(GainerLoser.StockMovement::getChangePercent).reversed());
                
                List<GainerLoser.StockMovement> gainers = allMovements.stream()
                    .filter(m -> m.getChangePercent() > 0)
                    .limit(limit)
                    .collect(Collectors.toList());
                    
                List<GainerLoser.StockMovement> losers = allMovements.stream()
                    .filter(m -> m.getChangePercent() < 0)
                    // Take from the end of the list (most negative)
                    .sorted(Comparator.comparing(GainerLoser.StockMovement::getChangePercent))
                    .limit(limit)
                    .collect(Collectors.toList());
                    
                return GainerLoser.builder()
                    .timestamp(Instant.now())
                    .topGainers(gainers)
                    .topLosers(losers)
                    .sectorMovements(Collections.emptyList()) // Can be populated if needed
                    .build();
            }
        );
    }

    
    
    /**
     * Creates an empty response when no data is available
     */
    private GainerLoser createEmptyResponse() {
        return GainerLoser.builder()
            .timestamp(Instant.now())
            .topGainers(Collections.emptyList())
            .topLosers(Collections.emptyList())
            .build();
    }
}
