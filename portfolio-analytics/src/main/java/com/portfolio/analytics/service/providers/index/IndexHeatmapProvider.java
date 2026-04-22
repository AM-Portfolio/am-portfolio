package com.portfolio.analytics.service.providers.index;

import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.HeatmapUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provider for sector heatmap analytics
 */
@Service
@Slf4j
public class IndexHeatmapProvider extends AbstractIndexAnalyticsProvider<Heatmap> {

    public IndexHeatmapProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(nseIndicesService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_HEATMAP;
    }

    @Override
    public Heatmap generateAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Generating sector heatmap for index: {}", request.getCoreIdentifiers().getIndexSymbol());
        return generateHeatmap(request.getCoreIdentifiers().getIndexSymbol(), request.getTimeFrameRequest());
    }
    

    /**
     * Common method to generate heatmap with or without time frame
     */
    private Heatmap generateHeatmap(String indexSymbol, TimeFrameRequest timeFrameRequest) {
        log.info("Generating sector heatmap for index: {} with timeFrame: {}", indexSymbol, timeFrameRequest);

        // Get index symbols
        List<String> indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return createEmptyHeatmap();
        }
        
        log.info("Found {} stock symbols for index: {}", indexStockSymbols.size(), indexSymbol);
        
        // Fetch market data using AnalyticsUtils
        Map<String, MarketData> marketData = AnalyticsUtils.fetchMarketData(this, indexStockSymbols, timeFrameRequest);
        if (marketData.isEmpty()) {
            log.warn("No market data available for index: {}", indexSymbol);
            return createEmptyHeatmap();
        }
        
        log.info("Successfully fetched market data for {} out of {} symbols for index: {}", 
                marketData.size(), indexStockSymbols.size(), indexSymbol);
        
        // Group stocks by sector
        Map<String, List<MarketData>> sectorMap = groupStocksBySector(marketData);
        
        // Calculate performance for each sector
        List<Heatmap.SectorPerformance> sectorPerformances = calculateSectorPerformances(sectorMap);
        
        // Create heatmap with domain-driven approach
        Heatmap heatmap = Heatmap.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .sectors(sectorPerformances)
            .build();
            
        // Use domain method to sort sectors and assign performance ranks
        heatmap.sortSectorsByPerformance();
        
        log.info("Generated heatmap with {} sectors for index: {}", sectorPerformances.size(), indexSymbol);
        
        return heatmap;
    }
    
    /**
     * Create an empty heatmap result when no data is available
     */
    private Heatmap createEmptyHeatmap() {
        return Heatmap.builder()
            .timestamp(Instant.now())
            .sectors(Collections.emptyList())
            .build();
    }
    
    /**
     * Group stocks by sector
     */
    private Map<String, List<MarketData>> groupStocksBySector(Map<String, MarketData> marketData) {
        log.info("Grouping {} stocks by sector", marketData.size());
        Map<String, List<MarketData>> sectorMap = new HashMap<>();
        
        if (marketData.isEmpty()) {
            return sectorMap;
        }
        
        // Extract symbols from market data
        List<String> symbols = new ArrayList<>(marketData.keySet());
        
        // Use SecurityDetailsService to get sector information
        Map<String, List<String>> sectorToSymbols = securityDetailsService.groupSymbolsBySector(symbols);
        
        // Map market data to sectors
        for (Map.Entry<String, List<String>> entry : sectorToSymbols.entrySet()) {
            String sector = entry.getKey();
            List<String> sectorSymbols = entry.getValue();
            
            // Create a list of MarketData objects for this sector
            List<MarketData> sectorStocks = sectorSymbols.stream()
                .filter(marketData::containsKey)
                .map(marketData::get)
                .collect(Collectors.toList());
            
            // Only add sectors with data
            if (!sectorStocks.isEmpty()) {
                sectorMap.put(sector, sectorStocks);
            }
        }
        
        // Handle any symbols that might not have sector information
        // by using the mock implementation as fallback
        if (sectorMap.isEmpty()) {
            log.warn("No sector information found from SecurityDetailsService, using fallback mock implementation");
            return fallbackGroupStocksBySector(marketData);
        }
        
        return sectorMap;
    }
    
    /**
     * Fallback method to group stocks by sector using mock data
     * Used when SecurityDetailsService doesn't return sector information
     */
    private Map<String, List<MarketData>> fallbackGroupStocksBySector(Map<String, MarketData> marketData) {
        log.info("Using fallback method to group {} stocks by sector", marketData.size());
        Map<String, List<MarketData>> sectorMap = new HashMap<>();
        
        if (marketData.isEmpty()) {
            return sectorMap;
        }
        
        for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketData data = entry.getValue();
            
            // Get sector for this symbol
            String sector = getMockSectorForSymbol(symbol);
            
            // Add to sector group
            sectorMap.computeIfAbsent(sector, k -> new ArrayList<>()).add(data);
}
            
            log.info("Found {} sectors from security details service", sectorMap.size());      
        return sectorMap;
    }
    
    /**
     * Calculate performance metrics for each sector using domain-driven approach
     */
    private List<Heatmap.SectorPerformance> calculateSectorPerformances(Map<String, List<MarketData>> sectorMap) {
        log.info("Calculating performance metrics for {} sectors", sectorMap.size());
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        
        // Calculate total market value for weightage calculation
        double totalMarketValue = calculateTotalMarketValue(sectorMap);
        log.debug("Total market value for weightage calculation: {}", totalMarketValue);
        
        for (Map.Entry<String, List<MarketData>> entry : sectorMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketData> sectorStocks = entry.getValue();
            
            // Extract symbols for this sector
            List<String> symbols = new ArrayList<>();
            for (MarketData data : sectorStocks) {
                String symbol = data.getSymbol();
                if (symbol == null || symbol.isEmpty()) {
                    symbol = "UNKNOWN-" + symbols.size();
                }
                symbols.add(symbol);
                log.debug("Added symbol: {} to sector: {}", symbol, sectorName);
            }
            
            // Create quantities list (for index stocks, we use 1.0 as default quantity)
            List<Double> quantities = new ArrayList<>();
            for (int i = 0; i < sectorStocks.size(); i++) {
                quantities.add(1.0); // Default quantity for index stocks
            }
            
            // Generate sector code from sector name
            String sectorCode = generateSectorCode(sectorName);
            
            // Create complete sector performance with stock details using domain-driven approach
            Heatmap.SectorPerformance sectorPerformance = HeatmapUtils.createCompleteSectorPerformance(
                sectorName,
                sectorCode,
                sectorStocks,
                quantities,
                symbols,
                totalMarketValue);
            
            sectorPerformances.add(sectorPerformance);
            
            // Use domain method to get color for logging
            String color = Heatmap.deriveColorFromPerformance(sectorPerformance.getPerformance());
            log.debug("Sector '{}' performance: {}, change: {}%, weightage: {}%, color: {}", 
                    sectorName, sectorPerformance.getPerformance(), sectorPerformance.getChangePercent(), 
                    sectorPerformance.getWeightage(), color);
        }
        
        log.info("Calculated performance metrics for {} sectors", sectorPerformances.size());
        return sectorPerformances;
    }
    
    /**
     * Generate a sector code from a sector name
     * @param sectorName The sector name
     * @return A sector code
     */
    private String generateSectorCode(String sectorName) {
        if (sectorName == null || sectorName.isEmpty()) {
            return "UNKN";
        }
        
        // Take first 4 characters and convert to uppercase
        if (sectorName.length() <= 4) {
            return sectorName.toUpperCase();
        } else {
            return sectorName.substring(0, 4).toUpperCase();
        }
    }
    
    /**
     * Calculate the total market value of all stocks in the index
     */
    private double calculateTotalMarketValue(Map<String, List<MarketData>> sectorMap) {
        double totalValue = 0.0;
        
        for (List<MarketData> stocks : sectorMap.values()) {
            totalValue += calculateSectorMarketValue(stocks);
        }
        
        return totalValue;
    }
    
    /**
     * Calculate the market value of a sector based on last prices
     */
    private double calculateSectorMarketValue(List<MarketData> stocks) {
        double sectorValue = 0.0;
        
        for (MarketData stock : stocks) {
            // For index stocks, we don't have quantity information
            // Using last price as a proxy for market value contribution
            sectorValue += stock.getLastPrice();
        }
        
        return sectorValue;
    }
    
    // SectorMetrics class and related methods have been moved to HeatmapUtils
    
    /**
     * Get a mock sector name based on symbol prefix (for demonstration)
     * In a real implementation, this would come from a stock metadata service
     */
    private String getMockSectorForSymbol(String symbol) {
        // Simple mock implementation - in real code, get this from a proper source
        if (symbol.startsWith("INFO") || symbol.startsWith("TCS") || symbol.startsWith("WIPRO") || symbol.startsWith("INFY")) {
            return "Information Technology";
        } else if (symbol.startsWith("HDFC") || symbol.startsWith("ICICI") || symbol.startsWith("SBI") || symbol.startsWith("PNB")) {
            return "Financial Services";
        } else if (symbol.startsWith("RELIANCE") || symbol.startsWith("ONGC")) {
            return "Energy";
        } else if (symbol.startsWith("BHARTI") || symbol.startsWith("IDEA")) {
            return "Telecommunication";
        } else if (symbol.startsWith("ITC") || symbol.startsWith("HUL")) {
            return "Consumer Goods";
        } else {
            return "Others";
        }
    }
}
