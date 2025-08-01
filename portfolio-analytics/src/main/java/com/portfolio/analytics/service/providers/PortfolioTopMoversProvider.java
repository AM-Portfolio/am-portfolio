package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.security.SecurityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.analytics.service.utils.TopMoverUtils;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.GainerLoser.StockMovement;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest.FeatureConfiguration;
import com.portfolio.model.analytics.request.PaginationRequest;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;

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
        
        log.info("Generating top {} movers for portfolio {} with time frame, pagination, and feature configuration", 
                limit, portfolioId);
        
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
        
        // Fetch historical market data for all stocks in the portfolio using time frame
        Map<String, MarketData> marketData = getHistoricalData(portfolioSymbols, request.getTimeFrameRequest());
        if (marketData.isEmpty()) {
            log.warn("No historical market data available for portfolio: {}", portfolioId);
            return createEmptyResponse(portfolioId);
        }
        
        // Calculate top movers using the determined limit
        return TopMoverUtils.buildTopMoversResponse(marketData, limit, portfolioId, true);
    }

    
    /**
     * Extracts the limit parameter from the variable arguments
     */
    private int extractLimitParameter(Object... params) {
        int limit = DEFAULT_LIMIT; // Default
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
            Map<String, MarketData> marketData, Map<String, Double> symbolToQuantity) {
        log.debug("Calculating performance metrics for {} stocks", marketData.size());
        PerformanceMetrics metrics = new PerformanceMetrics();
        
        for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketData data = entry.getValue();
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
            Map<String, MarketData> marketData, 
            Map<String, Double> symbolToQuantity,
            Predicate<Double> filter) {
        log.debug("Finding top {} movers with filter condition", limit);
        
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
            
            // Format change percent with precision
            Double changePercent = Math.round(metrics.symbolToChangePercent.getOrDefault(symbol, 0.0) * 100.0) / 100.0;
            
            // Calculate and format change amount with precision
            double lastPrice = marketData.get(symbol).getLastPrice();
            double closePrice = marketData.get(symbol).getOhlc().getClose();
            Double changeAmount = Math.round((lastPrice - closePrice) * 100.0) / 100.0;
            
            // Format market value and weight percentage with precision
            Double marketValue = Math.round(metrics.symbolToMarketValue.getOrDefault(symbol, 0.0) * 100.0) / 100.0;
            Double weightPercentage = metrics.totalPortfolioValue > 0 ? 
                    Math.round((marketValue / metrics.totalPortfolioValue) * 10000.0) / 100.0 : 0.0;
        
            // Get security details
            SecurityModel securityModel = securityDetailsService.getSecurityDetails(Collections.singletonList(symbol)).get(symbol);
            String sector = "Unknown";
            String companyName = symbol;
            
            if (securityModel != null && securityModel.getMetadata() != null) {
                sector = securityModel.getMetadata().getSector();
                if (sector == null) {
                    sector = "Unknown";
                }
            }
            
            movers.add(StockMovement.builder()
                .symbol(symbol)
                .companyName(companyName)
                .changePercent(changePercent)
                .changeAmount(changeAmount)
                .lastPrice(lastPrice)
                .ohlcData(marketData.get(symbol).getOhlc())
                .quantity(symbolToQuantity.getOrDefault(symbol, 0.0))
                .marketValue(marketValue)
                .weightPercentage(weightPercentage)
                .sector(sector)
                .build());
            
            // Stop if we have enough movers
            if (movers.size() >= limit) {
                break;
            }
        }
        
        return movers;
    }
    
    /**
     * Calculate sector-wise movement data
     */
    private List<GainerLoser.SectorMovement> calculateSectorMovements(
            List<String> symbols,
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance,
            Map<String, Double> symbolToChangePercent,
            int limit) {
        log.debug("Calculating sector movements for {} symbols", symbols.size());
        
        // Use TopMoverUtils to calculate sector movements with default limit of 3 for top gainers/losers per sector
        return TopMoverUtils.calculateSectorMovements(
            symbols, 
            marketData, 
            symbolToPerformance, 
            symbolToChangePercent, 
            securityDetailsService,
            limit // Default limit for top gainers/losers per sector
        );
    }
    

}
