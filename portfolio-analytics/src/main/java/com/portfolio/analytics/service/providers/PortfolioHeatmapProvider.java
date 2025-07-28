package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.Heatmap;
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
    public Heatmap generateAnalytics(String portfolioId) {
        log.info("Generating sector heatmap for portfolio: {}", portfolioId);
        
        // Get portfolio data
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (portfolio == null || portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) {
            log.warn("No portfolio or holdings found for ID: {}", portfolioId);
            return Heatmap.builder()
                .portfolioId(portfolioId)
                .timestamp(Instant.now())
                .sectors(Collections.emptyList())
                .build();
        }
        
        // Get symbols from portfolio holdings
        List<String> portfolioSymbols = getPortfolioSymbols(portfolio);
        if (portfolioSymbols.isEmpty()) {
            log.warn("No stock symbols found in portfolio: {}", portfolioId);
            return Heatmap.builder()
                .portfolioId(portfolioId)
                .timestamp(Instant.now())
                .sectors(Collections.emptyList())
                .build();
        }
        
        // Fetch market data for all stocks in the portfolio
        Map<String, MarketDataResponse> marketData = getMarketData(portfolioSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for portfolio: {}", portfolioId);
            return Heatmap.builder()
                .portfolioId(portfolioId)
                .timestamp(Instant.now())
                .sectors(Collections.emptyList())
                .build();
        }
        
        // Create a map of symbol to holding quantity
        Map<String, Double> symbolToQuantity = portfolio.getEquityModels().stream()
            .collect(Collectors.toMap(
                EquityModel::getSymbol,
                EquityModel::getQuantity,
                (a, b) -> a + b // In case of duplicate symbols, sum the quantities
            ));
        
        // Group stocks by sector and calculate performance
        Map<String, List<String>> sectorToStocks = securityDetailsService.groupSymbolsBySector(portfolioSymbols);
        
        // Group market data by sector
        Map<String, List<MarketDataResponse>> sectorMap = new HashMap<>();
        Map<String, List<Double>> sectorQuantities = new HashMap<>();
        
        for (String symbol : marketData.keySet()) {
            // Get sector for this symbol
            String sector = "Unknown";
            for (Map.Entry<String, List<String>> entry : sectorToStocks.entrySet()) {
                if (entry.getValue().contains(symbol)) {
                    sector = entry.getKey();
                    break;
                }
            }
            
            sectorMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(marketData.get(symbol));
            
            sectorQuantities.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(symbolToQuantity.getOrDefault(symbol, 0.0));
        }
        
        // Calculate performance for each sector
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        for (Map.Entry<String, List<MarketDataResponse>> entry : sectorMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketDataResponse> sectorStocks = entry.getValue();
            List<Double> quantities = sectorQuantities.get(sectorName);
            
            // Calculate weighted average performance for the sector
            double totalPerformance = 0.0;
            double totalChangePercent = 0.0;
            double totalValue = 0.0;
            
            for (int i = 0; i < sectorStocks.size(); i++) {
                MarketDataResponse stock = sectorStocks.get(i);
                double quantity = quantities.get(i);
                double value = stock.getLastPrice() * quantity;
                totalValue += value;
                
                double closePrice = stock.getOhlc().getClose();
                double openPrice = stock.getOhlc().getOpen();
                
                if (openPrice > 0) {
                    double changePercent = ((stock.getLastPrice() - openPrice) / openPrice) * 100;
                    totalChangePercent += changePercent * value; // Weight by value
                    
                    // Performance score based on price movement relative to previous close
                    double performanceScore = ((stock.getLastPrice() - closePrice) / closePrice) * 100;
                    totalPerformance += performanceScore * value; // Weight by value
                }
            }
            
            double avgPerformance = totalValue > 0 ? totalPerformance / totalValue : 0;
            double avgChangePercent = totalValue > 0 ? totalChangePercent / totalValue : 0;
            
            // Assign color based on performance
            String color = getColorForPerformance(avgPerformance);
            
            sectorPerformances.add(Heatmap.SectorPerformance.builder()
                .sectorName(sectorName)
                .performance(avgPerformance)
                .changePercent(avgChangePercent)
                .color(color)
                .build());
        }
        
        // Sort sectors by performance (highest to lowest)
        sectorPerformances.sort(Comparator.comparing(Heatmap.SectorPerformance::getPerformance).reversed());
        
        return Heatmap.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .sectors(sectorPerformances)
            .build();
    }
    
    /**
     * Get color code based on performance value
     */
    private String getColorForPerformance(double performance) {
        if (performance > 3) return "#006400"; // Dark Green
        if (performance > 1) return "#32CD32"; // Lime Green
        if (performance > 0) return "#90EE90"; // Light Green
        if (performance > -1) return "#FFA07A"; // Light Salmon
        if (performance > -3) return "#FF4500"; // Orange Red
        return "#8B0000"; // Dark Red
    }
}
