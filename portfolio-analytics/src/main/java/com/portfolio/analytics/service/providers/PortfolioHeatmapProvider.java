package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.market.MarketData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provider for portfolio sector heatmap analytics
 */
@Service
@Slf4j
public class PortfolioHeatmapProvider extends AbstractPortfolioAnalyticsProvider<Heatmap> {

    public PortfolioHeatmapProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(portfolioService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_HEATMAP;
    }

    @Override
    public Heatmap generateAnalytics(String portfolioId) {
        log.info("Generating sector heatmap for portfolio: {}", portfolioId);
        
        // Get portfolio data
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (portfolio == null || portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) {
            log.warn("No portfolio or holdings found for ID: {}", portfolioId);
            return createEmptyResult(portfolioId);
        }
        
        // Get symbols from portfolio holdings
        List<String> portfolioSymbols = getPortfolioSymbols(portfolio);
        if (portfolioSymbols.isEmpty()) {
            log.warn("No stock symbols found in portfolio: {}", portfolioId);
            return createEmptyResult(portfolioId);
        }
        
        // Fetch market data for all stocks in the portfolio
        Map<String, MarketData> marketData = getMarketData(portfolioSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for portfolio: {}", portfolioId);
            return createEmptyResult(portfolioId);
        }
        
        // Create a map of symbol to holding quantity
        Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);
        
        // Group stocks by sector
        Map<String, List<String>> sectorToStocks = securityDetailsService.groupSymbolsBySector(portfolioSymbols);
        
        // Process market data by sector
        Map<String, List<MarketData>> sectorMarketDataMap = new HashMap<>();
        Map<String, List<Double>> sectorQuantitiesMap = new HashMap<>();
        groupMarketDataBySector(marketData, sectorToStocks, symbolToQuantity, sectorMarketDataMap, sectorQuantitiesMap);
        
        // Calculate performance for each sector
        List<Heatmap.SectorPerformance> sectorPerformances = calculateSectorPerformances(sectorMarketDataMap, sectorQuantitiesMap);
        
        // Sort sectors by performance (highest to lowest)
        sectorPerformances.sort(Comparator.comparing(Heatmap.SectorPerformance::getPerformance).reversed());
        
        return Heatmap.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .sectors(sectorPerformances)
            .build();
    }
    
    /**
     * Create empty result when no data is available
     */
    private Heatmap createEmptyResult(String portfolioId) {
        return Heatmap.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .sectors(Collections.emptyList())
            .build();
    }
    
    /**
     * Create a map of symbol to holding quantity
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
     * Group market data by sector
     */
    private void groupMarketDataBySector(
            Map<String, MarketData> marketData,
            Map<String, List<String>> sectorToStocks,
            Map<String, Double> symbolToQuantity,
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap) {
        
        for (String symbol : marketData.keySet()) {
            MarketData data = marketData.get(symbol);
            String sector = findSectorForSymbol(symbol, sectorToStocks);
            
            sectorMarketDataMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(data);
            
            sectorQuantitiesMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(symbolToQuantity.getOrDefault(symbol, 0.0));
        }
    }
    
    /**
     * Find the sector for a given symbol
     */
    private String findSectorForSymbol(String symbol, Map<String, List<String>> sectorToStocks) {
        return sectorToStocks.entrySet().stream()
            .filter(entry -> entry.getValue().contains(symbol))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("Unknown");
    }
    
    /**
     * Calculate performance for each sector
     */
    private List<Heatmap.SectorPerformance> calculateSectorPerformances(
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap) {
        
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        
        for (Map.Entry<String, List<MarketData>> entry : sectorMarketDataMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketData> sectorStocks = entry.getValue();
            List<Double> quantities = sectorQuantitiesMap.get(sectorName);
            
            // Calculate weighted averages for the sector
            SectorMetrics metrics = calculateSectorMetrics(sectorStocks, quantities);
            
            // Assign color based on performance
            String color = getColorForPerformance(metrics.avgPerformance);
            
            sectorPerformances.add(Heatmap.SectorPerformance.builder()
                .sectorName(sectorName)
                .performance(metrics.avgPerformance)
                .changePercent(metrics.avgChangePercent)
                .color(color)
                .build());
        }
        
        return sectorPerformances;
    }
    
    /**
     * Calculate weighted average metrics for a sector
     */
    private SectorMetrics calculateSectorMetrics(List<MarketData> sectorStocks, List<Double> quantities) {
        double totalPerformance = 0.0;
        double totalChangePercent = 0.0;
        double totalValue = 0.0;
        
        for (int i = 0; i < sectorStocks.size(); i++) {
            MarketData stock = sectorStocks.get(i);
            double quantity = quantities.get(i);
            double value = stock.getLastPrice() * quantity;
            totalValue += value;
            
            double closePrice = stock.getOhlc().getClose();
            double openPrice = stock.getOhlc().getOpen();
            
            if (openPrice > 0) {
                // Calculate intraday change percentage
                double changePercent = ((stock.getLastPrice() - openPrice) / openPrice) * 100;
                totalChangePercent += changePercent * value; // Weight by value
                
                // Performance score based on price movement relative to previous close
                double performanceScore = ((stock.getLastPrice() - closePrice) / closePrice) * 100;
                totalPerformance += performanceScore * value; // Weight by value
            }
        }
        
        double avgPerformance = totalValue > 0 ? totalPerformance / totalValue : 0;
        double avgChangePercent = totalValue > 0 ? totalChangePercent / totalValue : 0;
        
        return new SectorMetrics(avgPerformance, avgChangePercent);
    }
    
    /**
     * Helper class to store sector metrics
     */
    private static class SectorMetrics {
        final double avgPerformance;
        final double avgChangePercent;
        
        SectorMetrics(double avgPerformance, double avgChangePercent) {
            this.avgPerformance = avgPerformance;
            this.avgChangePercent = avgChangePercent;
        }
    }
    
    /**
     * Get color code based on performance value
     */
    private String getColorForPerformance(double performance) {
        if (performance > 3) return "#006400"; // Dark Green
        if (performance > 1) return "#32CD32"; // Lime Green
        if (performance > 0) return "#90EE90"; // Light Green
        if (performance > -1) return "#FFA07A"; // Light Salmon
        if (performance > -3) return "#FF4500"; // Orange Red
        return "#8B0000"; // Dark Red
    }
}
