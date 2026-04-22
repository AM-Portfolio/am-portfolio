package com.portfolio.model.analytics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a sector/industry heatmap showing relative performance for an index or portfolio
 * with drill-down capability to view individual stock details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Heatmap {
    @JsonIgnore
    private String indexSymbol; // Used for index analytics
    @JsonIgnore
    private String portfolioId; // Used for portfolio analytics
    @JsonIgnore
    private Instant timestamp;
    private List<SectorPerformance> sectors;
    
    /**
     * Utility method to derive color code based on performance value
     * @param performanceValue The performance value (percentage)
     * @return A hex color code representing the performance level
     */
    public static String deriveColorFromPerformance(double performanceValue) {
        if (performanceValue > 3) return "#006400"; // Dark Green
        if (performanceValue > 1) return "#32CD32"; // Lime Green
        if (performanceValue > 0) return "#90EE90"; // Light Green
        if (performanceValue > -1) return "#FFA07A"; // Light Salmon
        if (performanceValue > -3) return "#FF4500"; // Orange Red
        return "#8B0000"; // Dark Red
    }
    
    /**
     * Sort sectors by performance (highest to lowest)
     * @return This heatmap instance for method chaining
     */
    public Heatmap sortSectorsByPerformance() {
        if (sectors != null) {
            sectors.sort(Comparator.comparing(SectorPerformance::getPerformance).reversed());
            
            // Update performance ranks
            for (int i = 0; i < sectors.size(); i++) {
                sectors.get(i).setPerformanceRank(i + 1);
            }
        }
        return this;
    }
    
    /**
     * Get top performing sectors
     * @param limit Maximum number of sectors to return
     * @return List of top performing sectors
     */
    public List<SectorPerformance> getTopSectors(int limit) {
        if (sectors == null || sectors.isEmpty()) {
            return new ArrayList<>();
        }
        
        return sectors.stream()
                .sorted(Comparator.comparing(SectorPerformance::getPerformance).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Get bottom performing sectors
     * @param limit Maximum number of sectors to return
     * @return List of bottom performing sectors
     */
    public List<SectorPerformance> getBottomSectors(int limit) {
        if (sectors == null || sectors.isEmpty()) {
            return new ArrayList<>();
        }
        
        return sectors.stream()
                .sorted(Comparator.comparing(SectorPerformance::getPerformance))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Represents performance data for a sector with additional metadata and stock details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorPerformance {
        private String sectorName;
        private String sectorCode; // e.g., "TECH", "HLTH" — stable identifier
        private Integer performanceRank; // 1 = best performing sector
        
        // Optional industry classification for more detailed categorization
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, List<StockDetail>> industries;
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private double performance = 0.0;
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private double changePercent = 0.0;
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private double weightage = 0.0; // Sector's weight in the portfolio or index
        
        private String color; // For UI representation (e.g., "green", "red", or hex code)
        
        // Enhanced metadata
        @Builder.Default
        private int stockCount = 0;
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private BigDecimal totalValue = BigDecimal.ZERO; // Total value of stocks in this sector
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private BigDecimal totalReturnAmount = BigDecimal.ZERO; // Total return amount for this sector
        
        // Stock details for drill-down view
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<StockDetail> stocks;
        
        /**
         * Calculate and set the total value of all stocks in this sector
         * @return This SectorPerformance instance for method chaining
         */
        public SectorPerformance calculateTotalValue() {
            if (stocks != null && !stocks.isEmpty()) {
                this.totalValue = stocks.stream()
                        .map(StockDetail::getValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                        
                this.stockCount = stocks.size();
            }
            return this;
        }
        
        /**
         * Calculate and set the total return amount for this sector
         * @return This SectorPerformance instance for method chaining
         */
        public SectorPerformance calculateTotalReturnAmount() {
            if (stocks != null && !stocks.isEmpty()) {
                this.totalReturnAmount = stocks.stream()
                        .map(stock -> stock.getChange().multiply(stock.getQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            return this;
        }
        
        /**
         * Calculate and set the weightage of each stock within this sector
         * @return This SectorPerformance instance for method chaining
         */
        public SectorPerformance calculateStockWeights() {
            if (stocks != null && !stocks.isEmpty() && totalValue != null && totalValue.compareTo(BigDecimal.ZERO) > 0) {
                for (StockDetail stock : stocks) {
                    BigDecimal weight = stock.getValue()
                            .divide(totalValue, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    stock.setWeight(weight.doubleValue());
                }
            }
            return this;
        }
        
        /**
         * Sort stocks by a specific criterion
         * @param comparator The comparator to use for sorting
         * @return This SectorPerformance instance for method chaining
         */
        public SectorPerformance sortStocks(Comparator<StockDetail> comparator) {
            if (stocks != null && !stocks.isEmpty()) {
                stocks.sort(comparator);
            }
            return this;
        }
        
        /**
         * Sort stocks by value (highest to lowest)
         * @return This SectorPerformance instance for method chaining
         */
        public SectorPerformance sortStocksByValue() {
            return sortStocks(Comparator.comparing(StockDetail::getValue).reversed());
        }
        
        /**
         * Sort stocks by performance (highest to lowest)
         * @return This SectorPerformance instance for method chaining
         */
        public SectorPerformance sortStocksByPerformance() {
            return sortStocks(Comparator.comparing(StockDetail::getChangePercent).reversed());
        }
        
        /**
         * Update the color based on the performance value
         * @return This SectorPerformance instance for method chaining
         */
        public SectorPerformance updateColor() {
            this.color = Heatmap.deriveColorFromPerformance(this.performance);
            return this;
        }
    }
    
    /**
     * Represents essential information about an individual stock within a sector
     * for heatmap drill-down view
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StockDetail {
        private String symbol;           // Stock symbol/ticker
        private String name;             // Short name (optional)
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private BigDecimal price = BigDecimal.ZERO;      // Current price
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private BigDecimal change = BigDecimal.ZERO;     // Price change
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private double changePercent = 0.0; // Price change percentage
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private BigDecimal quantity = BigDecimal.ZERO;   // Quantity (for portfolio stocks)
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private BigDecimal value = BigDecimal.ZERO;      // Total value (price * quantity)
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private double weight = 0.0;     // Weight within sector (%)
        
        private String color;            // Color for UI representation
        
        /**
         * Calculate the value based on price and quantity
         * @return This StockDetail instance for method chaining
         */
        public StockDetail calculateValue() { 
            if (price != null && quantity != null) {
                this.value = price.multiply(quantity);
            }
            return this;
        }
        
        /**
         * Calculate the change percentage based on price and change
         * @param previousPrice The previous price to calculate change against
         * @return This StockDetail instance for method chaining
         */
        public StockDetail calculateChangePercent(BigDecimal previousPrice) {
            if (previousPrice != null && previousPrice.compareTo(BigDecimal.ZERO) > 0 && change != null) {
                this.changePercent = change
                        .divide(previousPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }
            return this;
        }
        
        /**
         * Calculate the change amount based on current and previous price
         * @param previousPrice The previous price to calculate change against
         * @return This StockDetail instance for method chaining
         */
        public StockDetail calculateChange(BigDecimal previousPrice) {
            if (price != null && previousPrice != null) {
                this.change = price.subtract(previousPrice);
            }
            return this;
        }
        
        /**
         * Update the color based on the change percentage
         * @return This StockDetail instance for method chaining
         */
        public StockDetail updateColor() {
            this.color = Heatmap.deriveColorFromPerformance(this.changePercent);
            return this;
        }
    }
}
