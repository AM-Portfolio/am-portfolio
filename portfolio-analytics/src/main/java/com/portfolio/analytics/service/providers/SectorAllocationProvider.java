package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.SectorAllocation;
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

    public SectorAllocationProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService) {
        super(nseIndicesService, marketDataService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_ALLOCATION;
    }

    @Override
    public SectorAllocation generateAnalytics(String indexSymbol) {
        log.info("Calculating sector allocations for index: {}", indexSymbol);
        
        var indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return SectorAllocation.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .sectorWeights(Collections.emptyList())
                .industryWeights(Collections.emptyList())
                .build();
        }
        
        var marketData = getMarketData(indexStockSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for index: {}", indexSymbol);
            return SectorAllocation.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .sectorWeights(Collections.emptyList())
                .industryWeights(Collections.emptyList())
                .build();
        }
        
        // Group stocks by sector and industry
        Map<String, List<String>> sectorToStocks = new HashMap<>();
        Map<String, String> stockToSector = new HashMap<>();
        Map<String, List<String>> industryToStocks = new HashMap<>();
        Map<String, String> industryToSector = new HashMap<>();
        Map<String, Double> stockToMarketCap = new HashMap<>();
        
        double totalMarketCap = 0.0;
        
        // Assign mock sectors and industries
        for (String symbol : marketData.keySet()) {
            MarketDataResponse data = marketData.get(symbol);
            
            // Mock sector and industry assignment
            String sector = getMockSectorForSymbol(symbol);
            String industry = getMockIndustryForSymbol(symbol);
            
            // Mock market cap calculation
            double mockShares = getMockOutstandingShares(symbol);
            double marketCap = data.getLastPrice() * mockShares;
            
            // Store mappings
            stockToSector.put(symbol, sector);
            stockToMarketCap.put(symbol, marketCap);
            totalMarketCap += marketCap;
            
            // Group by sector
            sectorToStocks.computeIfAbsent(sector, k -> new ArrayList<>()).add(symbol);
            
            // Group by industry
            industryToStocks.computeIfAbsent(industry, k -> new ArrayList<>()).add(symbol);
            industryToSector.put(industry, sector);
        }
        
        // Calculate sector weights
        List<SectorAllocation.SectorWeight> sectorWeights = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sectorToStocks.entrySet()) {
            String sectorName = entry.getKey();
            List<String> stocks = entry.getValue();
            
            // Calculate total market cap for this sector
            double sectorMarketCap = stocks.stream()
                .mapToDouble(symbol -> stockToMarketCap.getOrDefault(symbol, 0.0))
                .sum();
            
            // Calculate weight percentage
            double weightPercentage = totalMarketCap > 0 ? (sectorMarketCap / totalMarketCap) * 100 : 0;
            
            // Get top stocks by market cap
            List<String> topStocks = stocks.stream()
                .sorted(Comparator.comparing(symbol -> stockToMarketCap.getOrDefault(symbol, 0.0)).reversed())
                .limit(5)
                .collect(Collectors.toList());
            
            sectorWeights.add(SectorAllocation.SectorWeight.builder()
                .sectorName(sectorName)
                .weightPercentage(weightPercentage)
                .marketCap(sectorMarketCap)
                .topStocks(topStocks)
                .build());
        }
        
        // Calculate industry weights
        List<SectorAllocation.IndustryWeight> industryWeights = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : industryToStocks.entrySet()) {
            String industryName = entry.getKey();
            List<String> stocks = entry.getValue();
            String parentSector = industryToSector.get(industryName);
            
            // Calculate total market cap for this industry
            double industryMarketCap = stocks.stream()
                .mapToDouble(symbol -> stockToMarketCap.getOrDefault(symbol, 0.0))
                .sum();
            
            // Calculate weight percentage
            double weightPercentage = totalMarketCap > 0 ? (industryMarketCap / totalMarketCap) * 100 : 0;
            
            // Get top stocks by market cap
            List<String> topStocks = stocks.stream()
                .sorted(Comparator.comparing(symbol -> stockToMarketCap.getOrDefault(symbol, 0.0)).reversed())
                .limit(3)
                .collect(Collectors.toList());
            
            industryWeights.add(SectorAllocation.IndustryWeight.builder()
                .industryName(industryName)
                .parentSector(parentSector)
                .weightPercentage(weightPercentage)
                .marketCap(industryMarketCap)
                .topStocks(topStocks)
                .build());
        }
        
        // Sort by weight percentage (highest to lowest)
        sectorWeights.sort(Comparator.comparing(SectorAllocation.SectorWeight::getWeightPercentage).reversed());
        industryWeights.sort(Comparator.comparing(SectorAllocation.IndustryWeight::getWeightPercentage).reversed());
        
        return SectorAllocation.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .sectorWeights(sectorWeights)
            .industryWeights(industryWeights)
            .build();
    }
    
    /**
     * Get mock sector for a symbol
     */
    private String getMockSectorForSymbol(String symbol) {
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
    
    /**
     * Get mock industry for a symbol
     */
    private String getMockIndustryForSymbol(String symbol) {
        if (symbol.startsWith("INFO") || symbol.startsWith("TCS")) {
            return "IT Consulting";
        } else if (symbol.startsWith("WIPRO") || symbol.startsWith("INFY")) {
            return "IT Services";
        } else if (symbol.startsWith("HDFC")) {
            return "Banking";
        } else if (symbol.startsWith("ICICI")) {
            return "Financial Services";
        } else if (symbol.startsWith("SBI") || symbol.startsWith("PNB")) {
            return "Public Sector Banks";
        } else if (symbol.startsWith("RELIANCE")) {
            return "Conglomerate";
        } else if (symbol.startsWith("ONGC")) {
            return "Oil & Gas";
        } else if (symbol.startsWith("BHARTI") || symbol.startsWith("IDEA")) {
            return "Telecom Services";
        } else if (symbol.startsWith("ITC")) {
            return "FMCG";
        } else if (symbol.startsWith("HUL")) {
            return "Consumer Products";
        } else {
            return "Miscellaneous";
        }
    }
    
    /**
     * Get mock outstanding shares for a stock
     */
    private double getMockOutstandingShares(String symbol) {
        if (symbol.startsWith("RELIANCE") || symbol.startsWith("TCS")) {
            return 8000000000.0; // 8 billion shares
        } else if (symbol.startsWith("HDFC") || symbol.startsWith("INFY")) {
            return 5000000000.0; // 5 billion shares
        } else if (symbol.startsWith("ITC") || symbol.startsWith("SBI")) {
            return 3000000000.0; // 3 billion shares
        } else {
            return 1000000000.0; // 1 billion shares
        }
    }
}
