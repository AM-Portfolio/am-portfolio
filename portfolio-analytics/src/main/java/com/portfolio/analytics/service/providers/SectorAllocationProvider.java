package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provider for sector allocation analytics
 */
@Service
@Slf4j
public class SectorAllocationProvider extends AbstractIndexAnalyticsProvider<SectorAllocation> {

    public SectorAllocationProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(nseIndicesService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_ALLOCATION;
    }

    @Override
    public SectorAllocation generateAnalytics(String indexSymbol) {
        log.info("Calculating sector allocations for index: {}", indexSymbol);
        return generateSectorAllocation(indexSymbol, null);
    }
    
    @Override
    public SectorAllocation generateAnalytics(String indexSymbol, TimeFrameRequest timeFrameRequest) {
        log.info("Calculating sector allocations for index: {} with time frame", indexSymbol);
        return generateSectorAllocation(indexSymbol, timeFrameRequest);
    }
    
    /**
     * Common method to generate sector allocation with or without time frame
     * 
     * @param indexSymbol The index symbol
     * @param timeFrameRequest Optional time frame parameters (can be null)
     * @return Sector allocation analytics
     */
    private SectorAllocation generateSectorAllocation(String indexSymbol, TimeFrameRequest timeFrameRequest) {
        var indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return createEmptyAllocation(indexSymbol);
        }
        
        // Use the utility method to fetch market data with or without time frame
        var marketData = AnalyticsUtils.fetchMarketData(this, indexStockSymbols, timeFrameRequest);
        if (marketData.isEmpty()) {
            return createEmptyAllocation(indexSymbol);
        }

        // Map stocks to sectors and industries
        Map<String, String> stockToSector = mapStocksToSectors(indexStockSymbols);
        Map<String, String> industryToSector = mapIndustriesToSectors(stockToSector);

        // Calculate market caps for all stocks
        Map<String, Double> stockMarketCaps = calculateMarketCaps(marketData);
        double totalMarketCap = stockMarketCaps.values().stream().mapToDouble(Double::doubleValue).sum();

        // Calculate sector weights
        Map<String, Double> sectorWeights = calculateSectorWeights(stockToSector, stockMarketCaps, totalMarketCap);

        // Calculate industry weights
        List<SectorAllocation.IndustryWeight> industryWeights = calculateIndustryWeights(
                stockToSector, industryToSector, stockMarketCaps, totalMarketCap);

        // Create sector objects with allocation data
        List<SectorAllocation.SectorWeight> sectorWeightsList = createSectorWeights(
                sectorWeights, stockToSector, stockMarketCaps);

        // Sort sectors by weight percentage (highest to lowest)
        sectorWeightsList.sort(Comparator.comparing(SectorAllocation.SectorWeight::getWeightPercentage).reversed());

        return SectorAllocation.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .sectorWeights(sectorWeightsList)
                .industryWeights(industryWeights)
                .build();
    }

    /**
     * Create empty result when no data is available
     */
    private SectorAllocation createEmptyAllocation(String indexSymbol) {
        return SectorAllocation.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .sectorWeights(Collections.emptyList())
                .industryWeights(Collections.emptyList())
                .build();
    }

    /**
     * Map stocks to their sectors
     */
    private Map<String, String> mapStocksToSectors(List<String> indexStockSymbols) {
        Map<String, String> stockToSector = new HashMap<>();
        
        // In a real implementation, this would fetch sector data from a database or service
        // For now, we'll use a simple mapping for demonstration
        for (String symbol : indexStockSymbols) {
            // Assign sectors based on some logic (e.g., first letter of symbol)
            char firstChar = symbol.charAt(0);
            String sector;
            
            if (firstChar >= 'A' && firstChar <= 'E') {
                sector = "Technology";
            } else if (firstChar >= 'F' && firstChar <= 'J') {
                sector = "Financial Services";
            } else if (firstChar >= 'K' && firstChar <= 'O') {
                sector = "Healthcare";
            } else if (firstChar >= 'P' && firstChar <= 'T') {
                sector = "Consumer Goods";
            } else {
                sector = "Industrial";
            }
            
            stockToSector.put(symbol, sector);
        }
        
        return stockToSector;
    }
    
    /**
     * Map industries to their parent sectors
     */
    private Map<String, String> mapIndustriesToSectors(Map<String, String> stockToSector) {
        Map<String, String> industryToSector = new HashMap<>();
        
        // In a real implementation, this would fetch industry-sector mappings from a database
        // For now, we'll use a simple mapping for demonstration
        industryToSector.put("Software", "Technology");
        industryToSector.put("Hardware", "Technology");
        industryToSector.put("Banking", "Financial Services");
        industryToSector.put("Insurance", "Financial Services");
        industryToSector.put("Pharmaceuticals", "Healthcare");
        industryToSector.put("Medical Devices", "Healthcare");
        industryToSector.put("Food & Beverage", "Consumer Goods");
        industryToSector.put("Retail", "Consumer Goods");
        industryToSector.put("Manufacturing", "Industrial");
        industryToSector.put("Construction", "Industrial");
        
        return industryToSector;
    }
    
    /**
     * Calculate market caps for all stocks
     */
    private Map<String, Double> calculateMarketCaps(Map<String, MarketData> marketData) {
        Map<String, Double> marketCaps = new HashMap<>();
        
        for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketData data = entry.getValue();
            
            // Calculate market cap (price * shares outstanding)
            // In a real implementation, would use actual shares outstanding
            // For demonstration, using a multiplier based on price range
            double sharesMultiplier;
            double price = data.getLastPrice();
            
            if (price < 100) {
                sharesMultiplier = 10_000_000; // 10M shares
            } else if (price < 500) {
                sharesMultiplier = 5_000_000; // 5M shares
            } else if (price < 1000) {
                sharesMultiplier = 2_000_000; // 2M shares
            } else {
                sharesMultiplier = 1_000_000; // 1M shares
            }
            
            double marketCap = AnalyticsUtils.calculateMarketCap(data, sharesMultiplier);
            marketCaps.put(symbol, marketCap);
        }
        
        return marketCaps;
    }
    
    /**
     * Calculate sector weights
     */
    private Map<String, Double> calculateSectorWeights(
            Map<String, String> stockToSector, 
            Map<String, Double> stockMarketCaps, 
            double totalMarketCap) {
        
        Map<String, Double> sectorWeights = new HashMap<>();
        Map<String, List<String>> sectorToStocks = stockToSector.entrySet().stream()
            .collect(Collectors.groupingBy(
                Map.Entry::getValue,
                Collectors.mapping(Map.Entry::getKey, Collectors.toList())
            ));
        
        for (Map.Entry<String, List<String>> entry : sectorToStocks.entrySet()) {
            String sector = entry.getKey();
            List<String> stocks = entry.getValue();
            
            // Calculate total market cap for this sector
            double sectorMarketCap = stocks.stream()
                .mapToDouble(symbol -> stockMarketCaps.getOrDefault(symbol, 0.0))
                .sum();
            
            // Calculate weight percentage
            double weightPercentage = totalMarketCap > 0 ? (sectorMarketCap / totalMarketCap) * 100 : 0;
            sectorWeights.put(sector, weightPercentage);
        }
        
        return sectorWeights;
    }
    
    /**
     * Create sector weight objects with allocation data
     */
    private List<SectorAllocation.SectorWeight> createSectorWeights(
            Map<String, Double> sectorWeights, 
            Map<String, String> stockToSector, 
            Map<String, Double> stockMarketCaps) {
        
        List<SectorAllocation.SectorWeight> sectorWeightsList = new ArrayList<>();
        
        // Group stocks by sector
        Map<String, List<String>> sectorToStocks = stockToSector.entrySet().stream()
            .collect(Collectors.groupingBy(
                Map.Entry::getValue,
                Collectors.mapping(Map.Entry::getKey, Collectors.toList())
            ));
        
        for (Map.Entry<String, Double> entry : sectorWeights.entrySet()) {
            String sectorName = entry.getKey();
            double weightPercentage = entry.getValue();
            
            // Get stocks in this sector
            List<String> sectorStocks = sectorToStocks.getOrDefault(sectorName, Collections.emptyList());
            
            // Calculate total market cap for this sector
            double sectorMarketCap = sectorStocks.stream()
                .mapToDouble(symbol -> stockMarketCaps.getOrDefault(symbol, 0.0))
                .sum();
            
            // Get top stocks by market cap
            List<String> topStocks = getTopStocksByMarketCap(sectorStocks, stockMarketCaps, 5);
            
            // Create sector weight object
            sectorWeightsList.add(SectorAllocation.SectorWeight.builder()
                .sectorName(sectorName)
                .weightPercentage(weightPercentage)
                .marketCap(sectorMarketCap)
                .topStocks(topStocks)
                .build());
        }
        
        return sectorWeightsList;
    }
    
    /**
     * Calculate industry weights
     */
    private List<SectorAllocation.IndustryWeight> calculateIndustryWeights(
            Map<String, String> stockToSector, 
            Map<String, String> industryToSector, 
            Map<String, Double> stockMarketCaps, 
            double totalMarketCap) {
        
        List<SectorAllocation.IndustryWeight> industryWeights = new ArrayList<>();
        Map<String, List<String>> industryToStocks = new HashMap<>();
        
        for (Map.Entry<String, String> entry : stockToSector.entrySet()) {
            String symbol = entry.getKey();
            // Get industry based on symbol pattern
            String industry = getIndustryForStock(symbol, industryToSector);
            
            industryToStocks.computeIfAbsent(industry, k -> new ArrayList<>()).add(symbol);
        }
        
        for (Map.Entry<String, List<String>> entry : industryToStocks.entrySet()) {
            String industryName = entry.getKey();
            List<String> stocks = entry.getValue();
            String parentSector = industryToSector.getOrDefault(industryName, "Other");
            
            // Calculate total market cap for this industry
            double industryMarketCap = stocks.stream()
                .mapToDouble(symbol -> stockMarketCaps.getOrDefault(symbol, 0.0))
                .sum();
            
            // Calculate weight percentage
            double weightPercentage = totalMarketCap > 0 ? (industryMarketCap / totalMarketCap) * 100 : 0;
            
            // Get top stocks by market cap
            List<String> topStocks = getTopStocksByMarketCap(stocks, stockMarketCaps, 3);
            
            industryWeights.add(SectorAllocation.IndustryWeight.builder()
                .industryName(industryName)
                .parentSector(parentSector)
                .weightPercentage(weightPercentage)
                .marketCap(industryMarketCap)
                .topStocks(topStocks)
                .build());
        }
        
        // Sort industries by weight percentage (highest to lowest)
        industryWeights.sort(Comparator.comparing(SectorAllocation.IndustryWeight::getWeightPercentage).reversed());
        
        return industryWeights;
    }
    
    private String getIndustryForStock(String symbol, Map<String, String> industryToSector) {
        // In a real implementation, this would fetch industry data from a database or service
        // For now, we'll use a simple mapping for demonstration
        if (symbol.startsWith("INFY") || symbol.startsWith("TCS")) {
            return "Software";
        } else if (symbol.startsWith("HDFC") || symbol.startsWith("ICICI")) {
            return "Banking";
        } else if (symbol.startsWith("CIPLA") || symbol.startsWith("SUNPHARMA")) {
            return "Pharmaceuticals";
        } else if (symbol.startsWith("HUL") || symbol.startsWith("ITC")) {
            return "Food & Beverage";
        } else if (symbol.startsWith("TATASTEEL") || symbol.startsWith("JSWSTEEL")) {
            return "Manufacturing";
        } else {
            return "Other";
        }
    }
    
    // Removed unused methods
    
    /**
     * Get top stocks by market cap
     */
    private List<String> getTopStocksByMarketCap(List<String> stocks, Map<String, Double> stockToMarketCap, int limit) {
        Map<String, Double> filteredMap = new HashMap<>();
        for (String symbol : stocks) {
            filteredMap.put(symbol, stockToMarketCap.getOrDefault(symbol, 0.0));
        }
        
        return AnalyticsUtils.getTopEntriesByValue(filteredMap, limit, true);
    }
}
