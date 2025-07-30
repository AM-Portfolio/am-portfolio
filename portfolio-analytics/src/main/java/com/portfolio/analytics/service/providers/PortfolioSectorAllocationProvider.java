package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
// MarketData is already imported below
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.SectorAllocation;
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
public class PortfolioSectorAllocationProvider extends AbstractPortfolioAnalyticsProvider<SectorAllocation> {

    public PortfolioSectorAllocationProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(portfolioService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_ALLOCATION;
    }

    @Override
    public SectorAllocation generateAnalytics(String portfolioId) {
        log.info("Calculating sector allocations for portfolio: {}", portfolioId);
        
        // Get portfolio data
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (portfolio == null || portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) {
            log.warn("No portfolio or holdings found for ID: {}", portfolioId);
            return SectorAllocation.builder()
                .portfolioId(portfolioId)
                .timestamp(Instant.now())
                .sectorWeights(Collections.emptyList())
                .industryWeights(Collections.emptyList())
                .build();
        }
        
        // Get symbols from portfolio holdings
        List<String> portfolioSymbols = getPortfolioSymbols(portfolio);
        if (portfolioSymbols.isEmpty()) {
            log.warn("No stock symbols found in portfolio: {}", portfolioId);
            return SectorAllocation.builder()
                .portfolioId(portfolioId)
                .timestamp(Instant.now())
                .sectorWeights(Collections.emptyList())
                .industryWeights(Collections.emptyList())
                .build();
        }
        
        // Fetch market data for all stocks in the portfolio
        Map<String, MarketData> marketData = getMarketData(portfolioSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for portfolio: {}", portfolioId);
            return SectorAllocation.builder()
                .portfolioId(portfolioId)
                .timestamp(Instant.now())
                .sectorWeights(Collections.emptyList())
                .industryWeights(Collections.emptyList())
                .build();
        }
        
        // Use SecurityDetailsService to group stocks by sector and industry
        Map<String, List<String>> sectorToStocks = securityDetailsService.groupSymbolsBySector(portfolioSymbols);
        Map<String, List<String>> industryToStocks = securityDetailsService.groupSymbolsByIndustry(portfolioSymbols);
        
        log.info("Sector groups for portfolio {}: {}", portfolioId, sectorToStocks.keySet());
        log.info("Industry groups for portfolio {}: {}", portfolioId, industryToStocks.keySet());
        
        // Create a map of symbol to holding quantity
        Map<String, Double> symbolToQuantity = portfolio.getEquityModels().stream()
            .collect(Collectors.toMap(
                EquityModel::getSymbol,
                EquityModel::getQuantity,
                (a, b) -> a + b // In case of duplicate symbols, sum the quantities
            ));
        
        // Create mappings for stock to sector and industry to sector
        Map<String, String> stockToSector = new HashMap<>();
        Map<String, String> industryToSector = new HashMap<>();
        Map<String, Double> stockToMarketValue = new HashMap<>();
        
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
        
        // Calculate market value for each stock in the portfolio
        double totalPortfolioValue = 0.0;
        for (String symbol : marketData.keySet()) {
            MarketData data = marketData.get(symbol);
            double quantity = symbolToQuantity.getOrDefault(symbol, 0.0);
            
            // Calculate market value (price * quantity)
            double marketValue = data.getLastPrice() * quantity;
            
            stockToMarketValue.put(symbol, marketValue);
            totalPortfolioValue += marketValue;
        }
        
        // Calculate sector weights
        List<SectorAllocation.SectorWeight> sectorWeights = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sectorToStocks.entrySet()) {
            String sectorName = entry.getKey();
            List<String> stocks = entry.getValue();
            
            // Calculate total market value for this sector
            double sectorMarketValue = stocks.stream()
                .mapToDouble(symbol -> stockToMarketValue.getOrDefault(symbol, 0.0))
                .sum();
            
            // Calculate weight percentage
            double weightPercentage = totalPortfolioValue > 0 ? (sectorMarketValue / totalPortfolioValue) * 100 : 0;
            
            // Get top stocks by market value
            List<String> topStocks = stocks.stream()
                .sorted(Comparator.comparing(symbol -> stockToMarketValue.getOrDefault(symbol, 0.0)).reversed())
                .limit(5)
                .collect(Collectors.toList());
            
            sectorWeights.add(SectorAllocation.SectorWeight.builder()
                .sectorName(sectorName)
                .weightPercentage(weightPercentage)
                .marketCap(sectorMarketValue)  // This is actually market value, not market cap
                .topStocks(topStocks)
                .build());
        }
        
        // Calculate industry weights
        List<SectorAllocation.IndustryWeight> industryWeights = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : industryToStocks.entrySet()) {
            String industryName = entry.getKey();
            List<String> stocks = entry.getValue();
            String parentSector = industryToSector.get(industryName);
            
            // Calculate total market value for this industry
            double industryMarketValue = stocks.stream()
                .mapToDouble(symbol -> stockToMarketValue.getOrDefault(symbol, 0.0))
                .sum();
            
            // Calculate weight percentage
            double weightPercentage = totalPortfolioValue > 0 ? (industryMarketValue / totalPortfolioValue) * 100 : 0;
            
            // Get top stocks by market value
            List<String> topStocks = stocks.stream()
                .sorted(Comparator.comparing(symbol -> stockToMarketValue.getOrDefault(symbol, 0.0)).reversed())
                .limit(3)
                .collect(Collectors.toList());
            
            industryWeights.add(SectorAllocation.IndustryWeight.builder()
                .industryName(industryName)
                .parentSector(parentSector)
                .weightPercentage(weightPercentage)
                .marketCap(industryMarketValue)  // This is actually market value, not market cap
                .topStocks(topStocks)
                .build());
        }
        
        // Sort by weight percentage (highest to lowest)
        sectorWeights.sort(Comparator.comparing(SectorAllocation.SectorWeight::getWeightPercentage).reversed());
        industryWeights.sort(Comparator.comparing(SectorAllocation.IndustryWeight::getWeightPercentage).reversed());
        
        return SectorAllocation.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .sectorWeights(sectorWeights)
            .industryWeights(industryWeights)
            .build();
    }
}
