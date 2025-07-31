package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.AllocationUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.SectorAllocation;
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
    public SectorAllocation generateAnalytics(String portfolioId) {
        log.info("Generating sector allocation for portfolio: {}", portfolioId);
        return generateSectorAllocation(portfolioId, null);
    }
    
    @Override
    public SectorAllocation generateAnalytics(String portfolioId, TimeFrameRequest timeFrameRequest) {
        log.info("Generating sector allocation for portfolio: {} with time frame parameters", portfolioId);
        return generateSectorAllocation(portfolioId, timeFrameRequest);
    }
    
    /**
     * Common method to generate sector allocation with or without time frame
     */
    private SectorAllocation generateSectorAllocation(String portfolioId, TimeFrameRequest timeFrameRequest) {
        log.info("Calculating sector allocations for portfolio: {} with timeFrame: {}", portfolioId, timeFrameRequest);
        
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
        
        // Fetch market data for all stocks in the portfolio using AnalyticsUtils
        Map<String, MarketData> marketData = AnalyticsUtils.fetchMarketData(this, portfolioSymbols, timeFrameRequest);
        if (marketData.isEmpty()) {
            log.warn("No market data available for portfolio: {}", portfolioId);
            return createEmptyResult(portfolioId);
        }
        
        // Create a map of symbol to holding quantity
        Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);
        
        // Group stocks by sector and industry
        Map<String, List<String>> sectorToStocks = securityDetailsService.groupSymbolsBySector(portfolioSymbols);
        Map<String, List<String>> industryToStocks = securityDetailsService.groupSymbolsByIndustry(portfolioSymbols);
        
        log.debug("Sector groups for portfolio {}: {}", portfolioId, sectorToStocks.keySet());
        log.debug("Industry groups for portfolio {}: {}", portfolioId, industryToStocks.keySet());
        
        // Map industry to parent sector
        Map<String, String> industryToSector = mapIndustriesToSectors(industryToStocks, sectorToStocks);
        
        // Calculate market values for each stock
        Map<String, Double> stockToMarketValue = new HashMap<>();
        double totalPortfolioValue = calculateMarketValues(marketData, symbolToQuantity, stockToMarketValue);
        
        // Calculate sector and industry weights
        List<SectorAllocation.SectorWeight> sectorWeights = calculateSectorWeights(
                sectorToStocks, stockToMarketValue, totalPortfolioValue);
        
        List<SectorAllocation.IndustryWeight> industryWeights = calculateIndustryWeights(
                industryToStocks, industryToSector, stockToMarketValue, totalPortfolioValue);
        
        return SectorAllocation.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .sectorWeights(sectorWeights)
            .industryWeights(industryWeights)
            .build();
    }
    
    /**
     * Create empty result when no data is available
     */
    private SectorAllocation createEmptyResult(String portfolioId) {
        return SectorAllocation.builder()
            .portfolioId(portfolioId)
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
