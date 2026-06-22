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

import com.portfolio.redis.service.PortfolioHeatmapRedisService;

/**
 * Provider for portfolio sector heatmap analytics
 */
@Service
@Slf4j
public class PortfolioHeatmapProvider extends AbstractPortfolioAnalyticsProvider<Heatmap> {

    private final PortfolioHeatmapRedisService heatmapRedisService;

    public PortfolioHeatmapProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService, PortfolioHeatmapRedisService heatmapRedisService) {
        super(portfolioService, marketDataService, securityDetailsService);
        this.heatmapRedisService = heatmapRedisService;
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_HEATMAP;
    }

    @Override
    public Heatmap generateAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Generating sector heatmap for portfolio: {}", request.getCoreIdentifiers().getPortfolioId());
        
        String portfolioId = request.getCoreIdentifiers().getPortfolioId();
        
        // Check cache first
        Optional<Heatmap> cached = heatmapRedisService.getCachedHeatmap(portfolioId, request.getTimeFrameRequest());
        if (cached.isPresent()) {
            return cached.get();
        }

        return processPortfolioData(
            portfolioId,
            request.getTimeFrameRequest(),
            this::createEmptyResult,
            (portfolio, portfolioSymbols, marketData) -> {
        
                // Create a map of symbol to holding quantity
                Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);
                
                // Group stocks by sector
                Map<String, List<String>> sectorToStocks = securityDetailsService.groupSymbolsBySector(portfolioSymbols);
                
                // Process market data by sector and compute total portfolio value in one pass
                Map<String, List<MarketData>> sectorMarketDataMap = new HashMap<>();
                Map<String, List<Double>> sectorQuantitiesMap = new HashMap<>();
                double[] totalPortfolioValue = {0.0};
                groupMarketDataBySector(marketData, sectorToStocks, symbolToQuantity, sectorMarketDataMap, sectorQuantitiesMap, totalPortfolioValue);
                
                // Calculate performance for each sector
                List<Heatmap.SectorPerformance> sectorPerformances = calculateSectorPerformances(sectorMarketDataMap, sectorQuantitiesMap, symbolToQuantity, totalPortfolioValue[0]);
                
                // Create heatmap with domain-driven approach
                Heatmap heatmap = Heatmap.builder()
                    .portfolioId(portfolioId)
                    .timestamp(Instant.now())
                    .sectors(sectorPerformances)
                    .build();
                    
                // Use domain method to sort sectors
                heatmap.sortSectorsByPerformance();
                
                log.info("Generated heatmap with {} sectors for portfolio: {}", sectorPerformances.size(), portfolioId);
                
                heatmapRedisService.cacheHeatmap(heatmap, portfolioId, request.getTimeFrameRequest());
                
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
            Map<String, List<Double>> sectorQuantitiesMap,
            double[] totalPortfolioValue) {
        log.debug("Grouping {} stocks by sector", marketData.size());
        
        // Build inverted index for O(1) sector lookup
        Map<String, String> symbolToSector = new HashMap<>();
        sectorToStocks.forEach((sector, symbols) -> 
            symbols.forEach(sym -> symbolToSector.put(sym, sector))
        );
        
        for (String symbol : marketData.keySet()) {
            MarketData data = marketData.get(symbol);
            if (data == null) {
                log.warn("Null market data encountered for symbol: {}", symbol);
                continue;
            }

            String sector = symbolToSector.getOrDefault(symbol, "Unknown");
            
            sectorMarketDataMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(data);
            
            double quantity = symbolToQuantity.getOrDefault(symbol, 0.0);
            sectorQuantitiesMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(quantity);
                
            double resolvedPrice = 0.0;
            if (data.getLastPrice() != null && data.getLastPrice() > 0) {
                resolvedPrice = data.getLastPrice();
            } else if (data.getOhlc() != null && data.getOhlc().getClose() > 0) {
                resolvedPrice = data.getOhlc().getClose();
            }
            totalPortfolioValue[0] += resolvedPrice * quantity;
        }
    }
    
    
    
    /**
     * Calculate performance for each sector with domain-driven approach
     */
    private List<Heatmap.SectorPerformance> calculateSectorPerformances(
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap,
            Map<String, Double> symbolToQuantity,
            double totalPortfolioValue) {
        
        log.debug("Calculating performance metrics for {} sectors", sectorMarketDataMap.size());
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        
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
    
    // calculateTotalSectorValues has been merged into groupMarketDataBySector for performance
    
    // SectorMetrics class and related methods have been moved to HeatmapUtils
}
