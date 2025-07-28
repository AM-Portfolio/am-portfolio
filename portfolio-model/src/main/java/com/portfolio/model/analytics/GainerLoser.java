package com.portfolio.model.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents top gainers and losers in an index or portfolio
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GainerLoser {
    private String indexSymbol; // Used for index analytics
    private String portfolioId; // Used for portfolio analytics
    private Instant timestamp;
    private List<StockMovement> topGainers;
    private List<StockMovement> topLosers;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StockMovement {
        private String symbol;
        private String companyName;
        private double lastPrice;
        private double previousPrice;
        private double changeAmount;
        private double changePercent;
        private String sector;
        private Double quantity; // Used for portfolio analytics (holding quantity)
        private Double marketValue; // Used for portfolio analytics (price * quantity)
        private Double weightPercentage; // Used for portfolio analytics (% of portfolio)
    }
}
