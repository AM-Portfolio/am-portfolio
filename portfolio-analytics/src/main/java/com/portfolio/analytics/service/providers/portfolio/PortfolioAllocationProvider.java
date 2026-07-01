package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.utils.AllocationUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provider for portfolio sector allocation analytics
 */
@Service
@Slf4j
public class PortfolioAllocationProvider extends AbstractPortfolioAnalyticsProvider<SectorAllocation> {

    public PortfolioAllocationProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(portfolioService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_ALLOCATION;
    }

    @Override
    public SectorAllocation generateAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Generating sector allocation for portfolio: {}", request.getCoreIdentifiers().getPortfolioId());
        return generateSectorAllocation(request.getCoreIdentifiers().getPortfolioId(), request);
    }
    
    /**
     * Generate sector allocation using Hybrid Circuit Breaker.
     * Attempts Market Data fetch for Global Timeframe. Falls back to MongoDB if timeout.
     */
    private SectorAllocation generateSectorAllocation(String portfolioId, TimeFrameRequest timeFrameRequest) {
        log.info("Calculating sector allocations for portfolio: {} using Hybrid Architecture", portfolioId);
        
        return processPortfolioDataHybrid(
            portfolioId,
            timeFrameRequest,
            this::createEmptyResult,
            
            // Primary Engine: Market Data API (Supports Global Timeframes)
            (portfolio, portfolioSymbols, marketData) -> {
                // Create a map of symbol to holding quantity
                Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);
                
                // Group stocks by sector and industry
                Map<String, List<String>> sectorToStocks = securityDetailsService.groupSymbolsBySector(portfolioSymbols);
                Map<String, List<String>> industryToStocks = securityDetailsService.groupSymbolsByIndustry(portfolioSymbols);
                
                // Map industry to parent sector
                Map<String, String> industryToSector = mapIndustriesToSectors(industryToStocks, sectorToStocks);
                
                // Calculate market values for each stock using Market Data
                Map<String, Double> stockToMarketValue = new HashMap<>();
                double totalPortfolioValue = calculateMarketValues(marketData, symbolToQuantity, stockToMarketValue);
                
                // Calculate sector and industry weights
                List<SectorAllocation.SectorWeight> sectorWeights = calculateSectorWeights(
                        sectorToStocks, stockToMarketValue, totalPortfolioValue);
                
                List<SectorAllocation.IndustryWeight> industryWeights = calculateIndustryWeights(
                        industryToStocks, industryToSector, stockToMarketValue, totalPortfolioValue);
                
                return SectorAllocation.builder()
                    .timestamp(Instant.now())
                    .sectorWeights(sectorWeights)
                    .industryWeights(industryWeights)
                    .build();
            },
            
            // Fallback Engine: MongoDB Extraction (1-Day Snapshot)
            (portfolio) -> {
                double totalPortfolioValue = portfolio.getEquityModels().stream()
                    .mapToDouble(e -> e.getCurrentValue() != null ? e.getCurrentValue() : 0.0)
                    .sum();
                    
                if (totalPortfolioValue <= 0) {
                    return createEmptyResult();
                }

                // Group by Sector and sum currentValue
                Map<String, Double> sectorValues = portfolio.getEquityModels().stream()
                    .collect(Collectors.groupingBy(
                        e -> e.getSector() != null ? e.getSector() : "Other",
                        Collectors.summingDouble(e -> e.getCurrentValue() != null ? e.getCurrentValue() : 0.0)
                    ));
                    
                List<SectorAllocation.SectorWeight> sectorWeights = sectorValues.entrySet().stream()
                    .map(entry -> SectorAllocation.SectorWeight.builder()
                        .sectorName(entry.getKey())
                        .weightPercentage((entry.getValue() / totalPortfolioValue) * 100.0)
                        .marketCap(entry.getValue())
                        .build())
                    .sorted(Comparator.comparing(SectorAllocation.SectorWeight::getWeightPercentage).reversed())
                    .collect(Collectors.toList());

                // Group by Industry and sum currentValue
                Map<String, Double> industryValues = portfolio.getEquityModels().stream()
                    .collect(Collectors.groupingBy(
                        e -> e.getIndustry() != null ? e.getIndustry() : "Other",
                        Collectors.summingDouble(e -> e.getCurrentValue() != null ? e.getCurrentValue() : 0.0)
                    ));
                    
                List<SectorAllocation.IndustryWeight> industryWeights = industryValues.entrySet().stream()
                    .map(entry -> {
                        // Find the sector for this industry by picking the first matching equity
                        String parentSector = portfolio.getEquityModels().stream()
                            .filter(e -> Objects.equals(e.getIndustry(), entry.getKey()))
                            .map(e -> e.getSector() != null ? e.getSector() : "Other")
                            .findFirst()
                            .orElse("Other");
                            
                        return SectorAllocation.IndustryWeight.builder()
                            .industryName(entry.getKey())
                            .parentSector(parentSector)
                            .weightPercentage((entry.getValue() / totalPortfolioValue) * 100.0)
                            .marketCap(entry.getValue())
                            .build();
                    })
                    .sorted(Comparator.comparing(SectorAllocation.IndustryWeight::getWeightPercentage).reversed())
                    .collect(Collectors.toList());
                    
                return SectorAllocation.builder()
                    .timestamp(Instant.now())
                    .sectorWeights(sectorWeights)
                    .industryWeights(industryWeights)
                    .build();
            }
        );
    }
    
    /**
     * Create empty result when no data is available
     */
    private SectorAllocation createEmptyResult() {
        return SectorAllocation.builder()
            
            .timestamp(Instant.now())
            .sectorWeights(Collections.emptyList())
            .industryWeights(Collections.emptyList())
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
     * Map industries to their parent sectors
     */
    private Map<String, String> mapIndustriesToSectors(
            Map<String, List<String>> industryToStocks, 
            Map<String, List<String>> sectorToStocks) {
        log.debug("Mapping {} industries to {} sectors", industryToStocks.size(), sectorToStocks.size());
        
        Map<String, String> industryToSector = new HashMap<>();
        
        // Determine the parent sector for each industry
        for (String industry : industryToStocks.keySet()) {
            List<String> industrySymbols = industryToStocks.get(industry);
            
            // Count occurrences of each sector for stocks in this industry
            Map<String, Long> sectorCounts = new HashMap<>();
            for (String symbol : industrySymbols) {
                for (Map.Entry<String, List<String>> entry : sectorToStocks.entrySet()) {
                    if (entry.getValue().contains(symbol)) {
                        String sector = entry.getKey();
                        sectorCounts.put(sector, sectorCounts.getOrDefault(sector, 0L) + 1);
                        break;
                    }
                }
            }
            
            // Find the most common sector for this industry
            String mostCommonSector = sectorCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");
                
            industryToSector.put(industry, mostCommonSector);
        }
        
        return industryToSector;
    }
    
    /**
     * Calculate market values for each stock and total portfolio value
     */
    private double calculateMarketValues(
            Map<String, MarketData> marketData, 
            Map<String, Double> symbolToQuantity, 
            Map<String, Double> stockToMarketValue) {
        
        // Use AllocationUtils to calculate market values
        Map<String, Double> calculatedValues = AllocationUtils.calculateMarketValues(marketData, symbolToQuantity);
        
        // Copy values to the provided map
        stockToMarketValue.putAll(calculatedValues);
        
        // Calculate and return total portfolio value
        double totalPortfolioValue = AllocationUtils.calculateTotalValue(calculatedValues);
        log.debug("Total portfolio value: {}", totalPortfolioValue);
        return totalPortfolioValue;
    }
    
    /**
     * Calculate sector weights
     */
    private List<SectorAllocation.SectorWeight> calculateSectorWeights(
            Map<String, List<String>> sectorToStocks, 
            Map<String, Double> stockToMarketValue, 
            double totalPortfolioValue) {
        
        // Use AllocationUtils to calculate sector weights
        List<SectorAllocation.SectorWeight> sectorWeights = 
            AllocationUtils.calculateSectorWeights(sectorToStocks, stockToMarketValue, totalPortfolioValue);
        
        log.info("Generated sector weights with {} sectors", sectorWeights.size());
        
        return sectorWeights;
    }
    
    /**
     * Calculate industry weights
     */
    private List<SectorAllocation.IndustryWeight> calculateIndustryWeights(
            Map<String, List<String>> industryToStocks, 
            Map<String, String> industryToSector, 
            Map<String, Double> stockToMarketValue, 
            double totalPortfolioValue) {
        
        // Use AllocationUtils to calculate industry weights
        return AllocationUtils.calculateIndustryWeights(
            industryToStocks, industryToSector, stockToMarketValue, totalPortfolioValue);
    }
    
    // Removed unused methods as they've been replaced by direct calls to AllocationUtils
}
