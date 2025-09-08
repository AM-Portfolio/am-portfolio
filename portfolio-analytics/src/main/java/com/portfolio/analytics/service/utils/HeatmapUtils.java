package com.portfolio.analytics.service.utils;

import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.market.MarketData;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Utility class for heatmap-related operations shared between
 * IndexHeatmapProvider and PortfolioHeatmapProvider
 */
@Slf4j
public class HeatmapUtils {

    /**
     * Get color code based on performance value
     * @param performance The performance value
     * @return A hex color code representing the performance level
     */
    public static String getColorForPerformance(double performance) {
        if (performance > 3) return "#006400"; // Dark Green
        if (performance > 1) return "#32CD32"; // Lime Green
        if (performance > 0) return "#90EE90"; // Light Green
        if (performance > -1) return "#FFA07A"; // Light Salmon
        if (performance > -3) return "#FF4500"; // Orange Red
        return "#8B0000"; // Dark Red
    }

    /**
     * Helper class to hold sector metrics
     */
    public static class SectorMetrics {
        private final double performance;
        private final double changePercent;
        private final double weightage;
        
        public SectorMetrics(double performance, double changePercent) {
            this(performance, changePercent, 0.0);
        }
        
        public SectorMetrics(double performance, double changePercent, double weightage) {
            this.performance = roundToTwoDecimals(performance);
            this.changePercent = roundToTwoDecimals(changePercent);
            this.weightage = roundToTwoDecimals(weightage);
        }
        
        public double getPerformance() {
            return performance;
        }
        
        public double getChangePercent() {
            return changePercent;
        }
        
        public double getWeightage() {
            return weightage;
        }
    }

    /**
     * Calculate simple average metrics for a list of stocks
     * Used by IndexHeatmapProvider
     * @param stocks List of market data for stocks in a sector
     * @return SectorMetrics containing average performance and change percent
     */
    public static SectorMetrics calculateSectorMetrics(List<MarketData> stocks) {
        return calculateSectorMetrics(stocks, null, 0.0);
    }
    
    /**
     * Calculate simple average metrics for a list of stocks with weightage
     * Used by IndexHeatmapProvider
     * @param stocks List of market data for stocks in a sector
     * @param totalMarketValue Total market value of all stocks in the index
     * @param sectorMarketValue Market value of this sector
     * @return SectorMetrics containing average performance, change percent, and weightage
     */
    public static SectorMetrics calculateSectorMetrics(List<MarketData> stocks, Double totalMarketValue, Double sectorMarketValue) {
        log.debug("Calculating sector metrics for {} stocks", stocks.size());
        double totalPerformance = 0.0;
        double totalChangePercent = 0.0;
        int validStockCount = 0;
        
        for (MarketData stock : stocks) {
            if (stock.getOhlc() != null) {
                double closePrice = stock.getOhlc().getClose();
                double openPrice = stock.getOhlc().getOpen();
                
                if (openPrice > 0 && closePrice > 0) {
                    // Calculate change from open to current price
                    double changePercent = ((stock.getLastPrice() - openPrice) / openPrice) * 100;
                    totalChangePercent += changePercent;
                    
                    // Performance score based on price movement relative to previous close
                    double performanceScore = ((stock.getLastPrice() - closePrice) / closePrice) * 100;
                    totalPerformance += performanceScore;
                    
                    validStockCount++;
                }
            }
        }
        
        // Calculate averages
        double avgPerformance = validStockCount > 0 ? totalPerformance / validStockCount : 0;
        double avgChangePercent = validStockCount > 0 ? totalChangePercent / validStockCount : 0;
        
        // Calculate weightage if total market value is provided
        double weightage = 0.0;
        if (totalMarketValue != null && totalMarketValue > 0 && sectorMarketValue != null) {
            weightage = (sectorMarketValue / totalMarketValue) * 100;
        }
        
        log.debug("Calculated metrics from {} valid stocks out of {} total stocks", 
                validStockCount, stocks.size());
        
        return new SectorMetrics(avgPerformance, avgChangePercent, weightage);
    }

    /**
     * Calculate weighted average metrics for a sector
     * Used by PortfolioHeatmapProvider
     * @param sectorStocks List of market data for stocks in a sector
     * @param quantities List of quantities for each stock
     * @return SectorMetrics containing weighted average performance and change percent
     */
    public static SectorMetrics calculateWeightedSectorMetrics(List<MarketData> sectorStocks, List<Double> quantities) {
        return calculateWeightedSectorMetrics(sectorStocks, quantities, null);
    }
    
    /**
     * Calculate weighted average metrics for a sector with weightage
     * Used by PortfolioHeatmapProvider
     * @param sectorStocks List of market data for stocks in a sector
     * @param quantities List of quantities for each stock
     * @param totalPortfolioValue Total value of the portfolio (optional)
     * @return SectorMetrics containing weighted average performance, change percent, and weightage
     */
    public static SectorMetrics calculateWeightedSectorMetrics(List<MarketData> sectorStocks, List<Double> quantities, Double totalPortfolioValue) {
        log.debug("Calculating weighted metrics for sector with {} stocks", sectorStocks.size());
        double totalPerformance = 0.0;
        double totalChangePercent = 0.0;
        double totalValue = 0.0;
        
        for (int i = 0; i < sectorStocks.size(); i++) {
            MarketData stock = sectorStocks.get(i);
            double quantity = quantities.get(i);
            double value = stock.getLastPrice() * quantity;
            totalValue += value;
            
            double closePrice = stock.getOhlc().getClose();
            double openPrice = stock.getOhlc().getOpen();
            
            if (openPrice > 0) {
                // Calculate intraday change percentage
                double changePercent = ((stock.getLastPrice() - openPrice) / openPrice) * 100;
                totalChangePercent += changePercent * value; // Weight by value
                
                // Performance score based on price movement relative to previous close
                double performanceScore = ((stock.getLastPrice() - closePrice) / closePrice) * 100;
                totalPerformance += performanceScore * value; // Weight by value
            }
        }
        
        double avgPerformance = totalValue > 0 ? totalPerformance / totalValue : 0;
        double avgChangePercent = totalValue > 0 ? totalChangePercent / totalValue : 0;
        
        // Calculate weightage if total portfolio value is provided
        double weightage = 0.0;
        if (totalPortfolioValue != null && totalPortfolioValue > 0) {
            weightage = (totalValue / totalPortfolioValue) * 100;
        }
        
        return new SectorMetrics(avgPerformance, avgChangePercent, weightage);
    }

    /**
     * Create a sector performance object
     * @param sectorName The name of the sector
     * @param metrics The metrics for the sector
     * @return A SectorPerformance object
     */
    public static Heatmap.SectorPerformance createSectorPerformance(String sectorName, SectorMetrics metrics) {
        String color = getColorForPerformance(metrics.getPerformance());
        
        return Heatmap.SectorPerformance.builder()
            .sectorName(sectorName)
            .performance(metrics.getPerformance())
            .changePercent(metrics.getChangePercent())
            .weightage(metrics.getWeightage())
            .color(color)
            .build();
    }
    
    /**
     * Helper method to round a double value to 2 decimal places
     * @param value The value to round
     * @return The rounded value
     */
    public static double roundToTwoDecimals(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
