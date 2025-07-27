package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.Heatmap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Provider for sector heatmap analytics
 */
@Service
@Slf4j
public class SectorHeatmapProvider extends AbstractIndexAnalyticsProvider<Heatmap> {

    public SectorHeatmapProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(nseIndicesService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_HEATMAP;
    }

    @Override
    public Heatmap generateAnalytics(String indexSymbol) {
        log.info("Generating sector heatmap for index: {}", indexSymbol);

        var indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return Heatmap.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .sectors(Collections.emptyList())
                .build();
        }
        
        var marketData = getMarketData(indexStockSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for index: {}", indexSymbol);
            return Heatmap.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .sectors(Collections.emptyList())
                .build();
        }
        
        // Group stocks by sector and calculate performance
        Map<String, List<MarketDataResponse>> sectorMap = new HashMap<>();
        
        // Get stock metadata from Redis or another source to map symbols to sectors
        // For now, we'll use a simplified approach with mock sector data
        for (String symbol : marketData.keySet()) {
            // In a real implementation, you would get the sector from a stock metadata service
            // For now, we'll assign mock sectors based on symbol prefix
            String sector = getMockSectorForSymbol(symbol);
            
            sectorMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(marketData.get(symbol));
        }
        
        // Calculate performance for each sector
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        for (Map.Entry<String, List<MarketDataResponse>> entry : sectorMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketDataResponse> sectorStocks = entry.getValue();
            
            // Calculate average performance for the sector
            double totalPerformance = 0.0;
            double totalChangePercent = 0.0;
            
            for (MarketDataResponse stock : sectorStocks) {
                double closePrice = stock.getOhlc().getClose();
                double openPrice = stock.getOhlc().getOpen();
                
                if (openPrice > 0) {
                    double changePercent = ((stock.getLastPrice() - openPrice) / openPrice) * 100;
                    totalChangePercent += changePercent;
                    
                    // Performance score based on price movement relative to previous close
                    double performanceScore = ((stock.getLastPrice() - stock.getOhlc().getClose()) / stock.getOhlc().getClose()) * 100;
                    totalPerformance += performanceScore;
                }
            }
            
            double avgPerformance = sectorStocks.isEmpty() ? 0 : totalPerformance / sectorStocks.size();
            double avgChangePercent = sectorStocks.isEmpty() ? 0 : totalChangePercent / sectorStocks.size();
            
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
            .indexSymbol(indexSymbol)
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
