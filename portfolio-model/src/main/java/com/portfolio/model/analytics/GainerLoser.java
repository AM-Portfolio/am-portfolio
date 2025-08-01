package com.portfolio.model.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.model.market.OhlcData;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents top gainers and losers in an index or portfolio
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GainerLoser {
    private Instant timestamp;
    private List<StockMovement> topGainers;
    private List<StockMovement> topLosers;
    private List<SectorMovement> sectorMovements; // Sector-wise movement data
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StockMovement {
        private String symbol;
        private String companyName;
        private double lastPrice;

        @JsonIgnore
        private OhlcData ohlcData;

        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Double changeAmount = 0.0;
        
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Double changePercent = 0.0;

        private String sector;
        private Double quantity; // Used for portfolio analytics (holding quantity)
        private Double marketValue; // Used for portfolio analytics (price * quantity)
        private Double weightPercentage; // Used for portfolio analytics (% of portfolio)
    }
    
    /**
     * Represents sector-wise movement data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorMovement {
        private String sectorName;
        @Builder.Default
        private double averageChangePercent = 0.0;
        private int stockCount;
        private double marketCapWeight; // Market capitalization weight of the sector
        private List<String> topGainerSymbols; // Top gainers in this sector
        private List<String> topLoserSymbols; // Top losers in this sector
        private Map<String, Double> stockPerformance; // Symbol to performance mapping
    }
}
