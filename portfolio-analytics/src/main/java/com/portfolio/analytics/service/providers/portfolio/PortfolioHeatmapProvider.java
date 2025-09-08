package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.utils.HeatmapUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
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
    public Heatmap generateAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Generating sector heatmap for portfolio: {}", request.getCoreIdentifiers().getPortfolioId());
        
        String portfolioId = request.getCoreIdentifiers().getPortfolioId();
        return processPortfolioData(
            portfolioId,
            request.getTimeFrameRequest(),
            this::createEmptyResult,
            (portfolio, portfolioSymbols, marketData) -> {
        
                // Create a map of symbol to holding quantity
                Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);
                
                // Group stocks by sector
                Map<String, List<String>> sectorToStocks = securityDetailsService.groupSymbolsBySector(portfolioSymbols);
                
                // Process market data by sector
                Map<String, List<MarketData>> sectorMarketDataMap = new HashMap<>();
                Map<String, List<Double>> sectorQuantitiesMap = new HashMap<>();
                groupMarketDataBySector(marketData, sectorToStocks, symbolToQuantity, sectorMarketDataMap, sectorQuantitiesMap);
                
                // Calculate performance for each sector
                List<Heatmap.SectorPerformance> sectorPerformances = calculateSectorPerformances(sectorMarketDataMap, sectorQuantitiesMap, symbolToQuantity);
                
                // Create heatmap with domain-driven approach
                Heatmap heatmap = Heatmap.builder()
                    .portfolioId(portfolioId)
                    .timestamp(Instant.now())
                    .sectors(sectorPerformances)
                    .build();
                    
                // Use domain method to sort sectors
                heatmap.sortSectorsByPerformance();
                
                log.info("Generated heatmap with {} sectors for portfolio: {}", sectorPerformances.size(), portfolioId);
                
                return heatmap;
            }
        );
    }
    
    /**
     * Create empty result when no data is available
     */
    private Heatmap createEmptyResult() {
        return Heatmap.builder()
        
            .timestamp(Instant.now())
            .sectors(Collections.emptyList())
            .build();
    }
    
    /**
     * Create a map of symbol to holding quantity
     */
    private Map<String, Double> createSymbolToQuantityMap(PortfolioModelV1 portfolio) {
        log.debug("Creating symbol to quantity map for portfolio: {}", portfolio.getName());
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
        log.debug("Grouping {} stocks by sector", marketData.size());
        
        for (String symbol : marketData.keySet()) {
            MarketData data = marketData.get(symbol);
            String sector = findSectorForSymbol(symbol, sectorToStocks);
            
            sectorMarketDataMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(data);
            
            sectorQuantitiesMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(symbolToQuantity.getOrDefault(symbol, 0.0));
        }
    }
    
    // Removed unused calculateTotalPortfolioValue method as we're using calculateTotalSectorValues
    
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
     * Calculate performance for each sector with domain-driven approach
     */
    private List<Heatmap.SectorPerformance> calculateSectorPerformances(
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap,
            Map<String, Double> symbolToQuantity) {
        
        log.debug("Calculating performance metrics for {} sectors", sectorMarketDataMap.size());
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        
        // Calculate total portfolio value for weightage calculation
        double totalPortfolioValue = calculateTotalSectorValues(sectorMarketDataMap, sectorQuantitiesMap);
        log.debug("Total portfolio value for weightage calculation: {}", totalPortfolioValue);
        
        for (Map.Entry<String, List<MarketData>> entry : sectorMarketDataMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketData> sectorStocks = entry.getValue();
            List<Double> quantities = sectorQuantitiesMap.get(sectorName);
            
            // Extract symbols for this sector
            List<String> symbols = new ArrayList<>();
            for (MarketData data : sectorStocks) {
                // Get symbol from MarketData or use a placeholder if null
                String symbol = data.getSymbol();
                if (symbol == null || symbol.isEmpty()) {
                    // Try to find the symbol by looking up in the symbolToQuantity map
                    for (Map.Entry<String, Double> symbolEntry : symbolToQuantity.entrySet()) {
                        if (symbolEntry.getValue().equals(quantities.get(symbols.size()))) {
                            symbol = symbolEntry.getKey();
                            break;
                        }
                    }
                    
                    // If still null, use a placeholder
                    if (symbol == null || symbol.isEmpty()) {
                        symbol = "UNKNOWN-" + symbols.size();
                    }
                }
                symbols.add(symbol);
                log.debug("Added symbol: {} to sector: {}", symbol, sectorName);
            }
            
            // Create complete sector performance with stock details using domain-driven approach
            Heatmap.SectorPerformance sectorPerformance = HeatmapUtils.createCompleteSectorPerformance(
                sectorName,
                null, // Let the utility generate a sector code
                sectorStocks,
                quantities,
                symbols,
                totalPortfolioValue);
            
            sectorPerformances.add(sectorPerformance);
        }
        
        return sectorPerformances;
    }
    
    /**
     * Calculate the total value across all sectors
     */
    private double calculateTotalSectorValues(
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap) {
        
        double totalValue = 0.0;
        
        for (Map.Entry<String, List<MarketData>> entry : sectorMarketDataMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketData> sectorStocks = entry.getValue();
            List<Double> quantities = sectorQuantitiesMap.get(sectorName);
            
            for (int i = 0; i < sectorStocks.size(); i++) {
                MarketData stock = sectorStocks.get(i);
                double quantity = quantities.get(i);
                totalValue += stock.getLastPrice() * quantity;
            }
        }
        
        return totalValue;
    }
    
    // SectorMetrics class and related methods have been moved to HeatmapUtils
}
