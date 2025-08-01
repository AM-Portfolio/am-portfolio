package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
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
        
        log.info("Generated heatmap with {} sectors for portfolio: {}", sectorPerformances.size(), portfolioId);
        
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
        
        log.debug("Calculating performance metrics for {} sectors", sectorMarketDataMap.size());
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        
        for (Map.Entry<String, List<MarketData>> entry : sectorMarketDataMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketData> sectorStocks = entry.getValue();
            List<Double> quantities = sectorQuantitiesMap.get(sectorName);
            
            // Calculate weighted averages for the sector using utility
            HeatmapUtils.SectorMetrics metrics = HeatmapUtils.calculateWeightedSectorMetrics(sectorStocks, quantities);
            
            // Create sector performance object using utility
            sectorPerformances.add(HeatmapUtils.createSectorPerformance(sectorName, metrics));
        }
        
        return sectorPerformances;
    }
    
    // SectorMetrics class and related methods have been moved to HeatmapUtils
}
