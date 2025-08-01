package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.AllocationUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;


/**
 * Provider for sector allocation analytics
 */
@Service
@Slf4j
public class IndexAllocationProvider extends AbstractIndexAnalyticsProvider<SectorAllocation> {

    public IndexAllocationProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(nseIndicesService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_ALLOCATION;
    }

    @Override
    public SectorAllocation generateAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Calculating sector allocations for index: {}", request.getCoreIdentifiers().getIndexSymbol());
        return generateSectorAllocation(request.getCoreIdentifiers().getIndexSymbol(), request);
    }
    

    /**
     * Common method to generate sector allocation with or without time frame
     * 
     * @param indexSymbol The index symbol
     * @param timeFrameRequest Optional time frame parameters (can be null)
     * @return Sector allocation analytics
     */
    private SectorAllocation generateSectorAllocation(String indexSymbol, AdvancedAnalyticsRequest request) {
        var indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return createEmptyAllocation();
        }
        
        // Use the utility method to fetch market data with or without time frame
        var marketData = AnalyticsUtils.fetchMarketData(this, indexStockSymbols, request.getTimeFrameRequest());
        if (marketData.isEmpty()) {
            return createEmptyAllocation();
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
                .timestamp(Instant.now())
                .sectorWeights(sectorWeightsList)
                .industryWeights(industryWeights)
                .build();
    }

    /**
     * Create empty result when no data is available
     */
    private SectorAllocation createEmptyAllocation() {
        return SectorAllocation.builder()
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
        log.debug("Calculating market caps for {} stocks", marketData.size());
        return AllocationUtils.calculateMarketCaps(marketData);
    }
    
    /**
     * Calculate sector weights
     */
    private Map<String, Double> calculateSectorWeights(
            Map<String, String> stockToSector, 
            Map<String, Double> stockMarketCaps, 
            double totalMarketCap) {
        
        Map<String, List<String>> sectorToStocks = new HashMap<>();
        
        // Group stocks by sector
        for (Map.Entry<String, String> entry : stockToSector.entrySet()) {
            String symbol = entry.getKey();
            String sector = entry.getValue();
            
            sectorToStocks.computeIfAbsent(sector, k -> new ArrayList<>()).add(symbol);
        }
        
        // Use AllocationUtils to calculate sector weights
        List<SectorAllocation.SectorWeight> sectorWeightsList = 
            AllocationUtils.calculateSectorWeights(sectorToStocks, stockMarketCaps, totalMarketCap);
        
        // Convert to Map<String, Double> for backward compatibility
        Map<String, Double> sectorWeights = new HashMap<>();
        sectorWeightsList.forEach(sw -> sectorWeights.put(sw.getSectorName(), sw.getWeightPercentage()));
        
        return sectorWeights;
    }
    
    /**
     * Create sector weight objects with allocation data
     */
    private List<SectorAllocation.SectorWeight> createSectorWeights(
            Map<String, Double> sectorWeights, 
            Map<String, String> stockToSector, 
            Map<String, Double> stockMarketCaps) {
        
        Map<String, List<String>> sectorToStocks = new HashMap<>();
        
        // Group stocks by sector
        for (Map.Entry<String, String> entry : stockToSector.entrySet()) {
            String symbol = entry.getKey();
            String sector = entry.getValue();
            
            sectorToStocks.computeIfAbsent(sector, k -> new ArrayList<>()).add(symbol);
        }
        
        // Use AllocationUtils to calculate sector weights
        double totalMarketCap = AllocationUtils.calculateTotalValue(stockMarketCaps);
        return AllocationUtils.calculateSectorWeights(sectorToStocks, stockMarketCaps, totalMarketCap);
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
