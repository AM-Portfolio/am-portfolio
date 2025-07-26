package com.portfolio.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents top gainers and losers in an index
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GainerLoser {
    private String indexSymbol;
    private Instant timestamp;
    private List<StockMovement> topGainers;
    private List<StockMovement> topLosers;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockMovement {
        private String symbol;
        private String companyName;
        private double lastPrice;
        private double previousPrice;
        private double changeAmount;
        private double changePercent;
        private String sector;
    }
}
