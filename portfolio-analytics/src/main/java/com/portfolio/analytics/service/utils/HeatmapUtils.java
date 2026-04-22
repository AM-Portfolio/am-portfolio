package com.portfolio.analytics.service.utils;

import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.market.MarketData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for heatmap-related operations shared between
 * IndexHeatmapProvider and PortfolioHeatmapProvider
 */
@Slf4j
public class HeatmapUtils {

    // Removed getColorForPerformance as it's now in the Heatmap class

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
     * Create a sector performance object with domain-driven approach
     * @param sectorName The name of the sector
     * @param metrics The metrics for the sector
     * @return A SectorPerformance object
     */
    public static Heatmap.SectorPerformance createSectorPerformance(String sectorName, SectorMetrics metrics) {
        // Generate a sector code from the sector name
        String sectorCode = generateSectorCode(sectorName);
        
        Heatmap.SectorPerformance sectorPerformance = Heatmap.SectorPerformance.builder()
            .sectorName(sectorName)
            .sectorCode(sectorCode)
            .performance(metrics.getPerformance())
            .changePercent(metrics.getChangePercent())
            .weightage(metrics.getWeightage())
            .build();
            
        // Use domain method to update color
        sectorPerformance.updateColor();
        
        return sectorPerformance;
    }
    
    /**
     * Create a sector performance object with domain-driven approach and explicit sector code
     * @param sectorName The name of the sector
     * @param sectorCode The sector code (stable identifier)
     * @param metrics The metrics for the sector
     * @return A SectorPerformance object
     */
    public static Heatmap.SectorPerformance createSectorPerformance(String sectorName, String sectorCode, SectorMetrics metrics) {
        Heatmap.SectorPerformance sectorPerformance = Heatmap.SectorPerformance.builder()
            .sectorName(sectorName)
            .sectorCode(sectorCode)
            .performance(metrics.getPerformance())
            .changePercent(metrics.getChangePercent())
            .weightage(metrics.getWeightage())
            .build();
            
        // Use domain method to update color
        sectorPerformance.updateColor();
        
        return sectorPerformance;
    }
    
    /**
     * Generate a sector code from a sector name
     * @param sectorName The sector name
     * @return A sector code
     */
    private static String generateSectorCode(String sectorName) {
        if (sectorName == null || sectorName.isEmpty()) {
            return "UNKN";
        }
        
        // Take first 4 characters and convert to uppercase
        if (sectorName.length() <= 4) {
            return sectorName.toUpperCase();
        } else {
            return sectorName.substring(0, 4).toUpperCase();
        }
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
    
    /**
     * Convert MarketData to StockDetail using domain-driven approach
     * @param symbol The stock symbol
     * @param data The market data
     * @param quantity The quantity (for portfolio stocks)
     * @return A StockDetail object
     */
    public static Heatmap.StockDetail convertToStockDetail(String symbol, MarketData data, double quantity) {
        if (data == null || data.getOhlc() == null) {
            return null;
        }
        
        // Create StockDetail with basic information
        Heatmap.StockDetail stockDetail = Heatmap.StockDetail.builder()
            .symbol(symbol)
            .name(symbol) // Use symbol as name if company name not available
            .price(BigDecimal.valueOf(data.getLastPrice()))
            .quantity(BigDecimal.valueOf(quantity))
            .build();
        
        // Use domain methods to calculate values
        BigDecimal previousPrice = BigDecimal.valueOf(data.getOhlc().getClose());
        stockDetail.calculateChange(previousPrice)
                  .calculateChangePercent(previousPrice)
                  .calculateValue()
                  .updateColor();
        
        return stockDetail;
    }
    
    /**
     * Create stock details for a sector using domain-driven approach
     * @param sectorStocks List of market data for stocks in a sector
     * @param quantities List of quantities for each stock
     * @param symbols List of symbols for each stock
     * @return List of StockDetail objects
     */
    public static List<Heatmap.StockDetail> createStockDetails(
            List<MarketData> sectorStocks, 
            List<Double> quantities,
            List<String> symbols) {
        
        List<Heatmap.StockDetail> stockDetails = new ArrayList<>();
        
        for (int i = 0; i < sectorStocks.size(); i++) {
            MarketData data = sectorStocks.get(i);
            double quantity = quantities.get(i);
            String symbol = symbols.get(i);
            
            Heatmap.StockDetail stockDetail = convertToStockDetail(symbol, data, quantity);
            if (stockDetail != null) {
                stockDetails.add(stockDetail);
            }
        }
        
        return stockDetails;
    }
    
    /**
     * Create a complete sector performance with stock details using domain-driven approach
     * @param sectorName The name of the sector
     * @param sectorCode The sector code (stable identifier)
     * @param sectorStocks List of market data for stocks in a sector
     * @param quantities List of quantities for each stock
     * @param symbols List of symbols for each stock
     * @param totalPortfolioValue Total value of the portfolio (optional)
     * @return A complete SectorPerformance object with stock details
     */
    public static Heatmap.SectorPerformance createCompleteSectorPerformance(
            String sectorName,
            String sectorCode,
            List<MarketData> sectorStocks,
            List<Double> quantities,
            List<String> symbols,
            Double totalPortfolioValue) {
        
        // Calculate metrics
        SectorMetrics metrics = calculateWeightedSectorMetrics(sectorStocks, quantities, totalPortfolioValue);
        
        // Create sector performance
        Heatmap.SectorPerformance sectorPerformance = createSectorPerformance(sectorName, sectorCode, metrics);
        
        // Create stock details
        List<Heatmap.StockDetail> stockDetails = createStockDetails(sectorStocks, quantities, symbols);
        
        // Set stock details and use domain methods to calculate values
        sectorPerformance.setStocks(stockDetails);
        sectorPerformance.setStockCount(stockDetails.size());
        
        // Use domain methods to calculate values and sort stocks
        sectorPerformance.calculateTotalValue()
                        .calculateTotalReturnAmount()
                        .calculateStockWeights()
                        .sortStocksByValue();
        
        return sectorPerformance;
    }
}
