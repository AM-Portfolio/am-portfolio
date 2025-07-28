package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.GainerLoser.StockMovement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Provider for portfolio top movers (gainers and losers) analytics
 */
@Service
@Slf4j
public class PortfolioTopMoversProvider extends AbstractPortfolioAnalyticsProvider<GainerLoser> {

    public PortfolioTopMoversProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(portfolioService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.TOP_MOVERS;
    }

    @Override
    public GainerLoser generateAnalytics(String portfolioId) {
        // Default to 5 top movers
        return generateAnalytics(portfolioId, 5);
    }

    @Override
    public GainerLoser generateAnalytics(String portfolioId, Object... params) {
        // Extract limit parameter if provided
        int limit = extractLimitParameter(params);
        log.info("Getting top {} gainers and losers for portfolio: {}", limit, portfolioId);
        
        // Get portfolio data
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (!isValidPortfolio(portfolio)) {
            return createEmptyResponse(portfolioId);
        }
        
        // Get symbols from portfolio holdings
        List<String> portfolioSymbols = getPortfolioSymbols(portfolio);
        if (portfolioSymbols.isEmpty()) {
            log.warn("No stock symbols found in portfolio: {}", portfolioId);
            return createEmptyResponse(portfolioId);
        }
        
        // Fetch market data for all stocks in the portfolio
        Map<String, MarketDataResponse> marketData = getMarketData(portfolioSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for portfolio: {}", portfolioId);
            return createEmptyResponse(portfolioId);
        }
        
        // Create a map of symbol to holding quantity
        Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);
        
        // Calculate performance metrics for each stock
        PerformanceMetrics metrics = calculatePerformanceMetrics(marketData, symbolToQuantity);
        
        // Get top gainers and losers
        List<StockMovement> gainers = getTopMovers(
                metrics, limit, marketData, symbolToQuantity, performance -> performance > 0);
        
        List<StockMovement> losers = getTopMovers(
                metrics, limit, marketData, symbolToQuantity, performance -> performance < 0);
        
        return GainerLoser.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .topGainers(gainers)
            .topLosers(losers)
            .build();
    }
    
    /**
     * Extracts the limit parameter from the variable arguments
     */
    private int extractLimitParameter(Object... params) {
        int limit = 5; // Default
        if (params.length > 0 && params[0] instanceof Integer) {
            limit = (Integer) params[0];
        }
        return limit;
    }
    
    /**
     * Checks if the portfolio is valid and has holdings
     */
    private boolean isValidPortfolio(PortfolioModelV1 portfolio) {
        return portfolio != null && portfolio.getEquityModels() != null && !portfolio.getEquityModels().isEmpty();
    }
    
    /**
     * Creates an empty response when no data is available
     */
    private GainerLoser createEmptyResponse(String portfolioId) {
        return GainerLoser.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .topGainers(Collections.emptyList())
            .topLosers(Collections.emptyList())
            .build();
    }
    
    /**
     * Creates a map of symbol to holding quantity
     */
    private Map<String, Double> createSymbolToQuantityMap(PortfolioModelV1 portfolio) {
        return portfolio.getEquityModels().stream()
            .collect(Collectors.toMap(
                EquityModel::getSymbol,
                EquityModel::getQuantity,
                (a, b) -> a + b // In case of duplicate symbols, sum the quantities
            ));
    }
    
    /**
     * Inner class to hold performance metrics
     */
    private static class PerformanceMetrics {
        final Map<String, Double> symbolToPerformance = new HashMap<>();
        final Map<String, Double> symbolToChangePercent = new HashMap<>();
        final Map<String, Double> symbolToMarketValue = new HashMap<>();
        double totalPortfolioValue = 0.0;
        
        List<Map.Entry<String, Double>> getSortedPerformance(boolean ascending) {
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(symbolToPerformance.entrySet());
            sorted.sort(Map.Entry.<String, Double>comparingByValue());
            if (!ascending) {
                Collections.reverse(sorted);
            }
            return sorted;
        }
    }
    
    /**
     * Calculates performance metrics for each stock
     */
    private PerformanceMetrics calculatePerformanceMetrics(
            Map<String, MarketDataResponse> marketData, Map<String, Double> symbolToQuantity) {
        
        PerformanceMetrics metrics = new PerformanceMetrics();
        
        for (Map.Entry<String, MarketDataResponse> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketDataResponse data = entry.getValue();
            double quantity = symbolToQuantity.getOrDefault(symbol, 0.0);
            
            double closePrice = data.getOhlc().getClose();
            double openPrice = data.getOhlc().getOpen();
            double lastPrice = data.getLastPrice();
            
            // Calculate market value of this holding
            double marketValue = lastPrice * quantity;
            metrics.symbolToMarketValue.put(symbol, marketValue);
            metrics.totalPortfolioValue += marketValue;
            
            if (openPrice > 0) {
                double changePercent = ((lastPrice - openPrice) / openPrice) * 100;
                metrics.symbolToChangePercent.put(symbol, changePercent);
                
                // Performance score based on price movement relative to previous close
                double performanceScore = ((lastPrice - closePrice) / closePrice) * 100;
                metrics.symbolToPerformance.put(symbol, performanceScore);
            }
        }
        
        return metrics;
    }
    
    /**
     * Gets top movers (gainers or losers) based on the filter
     */
    private List<StockMovement> getTopMovers(
            PerformanceMetrics metrics, 
            int limit, 
            Map<String, MarketDataResponse> marketData, 
            Map<String, Double> symbolToQuantity,
            Predicate<Double> filter) {
        
        // For gainers: ascending=false (highest first)
        // For losers: ascending=true (lowest first)
        boolean ascending = filter.test(-1.0); // If filter accepts negative values, we're looking for losers
        
        List<Map.Entry<String, Double>> sortedByPerformance = metrics.getSortedPerformance(!ascending);
        List<StockMovement> movers = new ArrayList<>();
        
        for (int i = 0; i < Math.min(limit, sortedByPerformance.size()); i++) {
            Map.Entry<String, Double> entry = sortedByPerformance.get(i);
            String symbol = entry.getKey();
            double performance = entry.getValue();
            
            // Skip if the performance doesn't match our filter criteria
            if (!filter.test(performance)) {
                continue;
            }
            
            double changePercent = metrics.symbolToChangePercent.getOrDefault(symbol, 0.0);
            double marketValue = metrics.symbolToMarketValue.getOrDefault(symbol, 0.0);
            double weightPercentage = metrics.totalPortfolioValue > 0 ? 
                    (marketValue / metrics.totalPortfolioValue) * 100 : 0;
            
            movers.add(StockMovement.builder()
                .symbol(symbol)
                .changePercent(changePercent)
                .lastPrice(marketData.get(symbol).getLastPrice())
                .quantity(symbolToQuantity.getOrDefault(symbol, 0.0))
                .marketValue(marketValue)
                .weightPercentage(weightPercentage)
                .build());
            
            // Stop if we have enough movers
            if (movers.size() >= limit) {
                break;
            }
        }
        
        return movers;
    }
}
