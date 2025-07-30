package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.SectorAllocation;
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
        
        // Use SecurityDetailsService to group stocks by sector and industry
        Map<String, List<String>> sectorToStocks = securityDetailsService.groupSymbolsBySector(indexStockSymbols);
        Map<String, List<String>> industryToStocks = securityDetailsService.groupSymbolsByIndustry(indexStockSymbols);
        
        log.info("Sector groups for index {}: {}", indexSymbol, sectorToStocks.keySet());
        log.info("Industry groups for index {}: {}", indexSymbol, industryToStocks.keySet());
        
        // Create mappings for stock to sector and industry to sector
        Map<String, String> stockToSector = new HashMap<>();
        Map<String, String> industryToSector = new HashMap<>();
        Map<String, Double> stockToMarketCap = new HashMap<>();
        
        // Map stocks to sectors
        for (Map.Entry<String, List<String>> entry : sectorToStocks.entrySet()) {
            String sector = entry.getKey();
            for (String symbol : entry.getValue()) {
                stockToSector.put(symbol, sector);
            }
        }
        
        // Map industries to sectors and determine which stocks belong to which industry
        Map<String, Set<String>> industryStockSets = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : industryToStocks.entrySet()) {
            String industry = entry.getKey();
            List<String> stocks = entry.getValue();
            industryStockSets.put(industry, new HashSet<>(stocks));
            
            // Find the most common sector for stocks in this industry
            Map<String, Integer> sectorCounts = new HashMap<>();
            for (String symbol : stocks) {
                String sector = stockToSector.getOrDefault(symbol, "Unknown");
                sectorCounts.put(sector, sectorCounts.getOrDefault(sector, 0) + 1);
            }
            
            // Assign the industry to the most common sector
            String mostCommonSector = sectorCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");
                
            industryToSector.put(industry, mostCommonSector);
        }
        
        // Calculate market cap for each stock
        double totalMarketCap = 0.0;
        for (String symbol : marketData.keySet()) {
            MarketData data = marketData.get(symbol);
            
            // Calculate market cap (price * outstanding shares)
            double marketCap = data.getLastPrice() * 1000000;
            
            stockToMarketCap.put(symbol, marketCap);
            totalMarketCap += marketCap;
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
}
