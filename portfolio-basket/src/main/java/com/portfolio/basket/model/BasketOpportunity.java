package com.portfolio.basket.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BasketOpportunity {
    private String etfIsin;
    private String etfName;
    private double matchScore;
    private double replicaScore; // Score based on weight alignment
    private boolean readyToReplicate; // true if replicaScore >= 70
    private int totalItems;
    private int heldCount;
    private int missingCount;
    private Double totalPortfolioValue;

    private List<BasketItem> composition;
    private List<BasketItem> buyList; // Stocks to buy to reach 100% or bridge gap

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BasketItem {
        private String stockSymbol;
        private String isin;
        private String sector;
        private ItemStatus status; // HELD, MISSING, SUBSTITUTE
        private String userHoldingSymbol; // If SUBSTITUTE or HELD
        private String reason; // e.g. "Sector Match"

        private Double etfWeight; // Target Weight in ETF
        private Double userWeight; // Actual Weight in User Portfolio
        private Double replicaWeight; // Weight contributed to the replica
        private Double buyQuantity; // Suggested if MISSING
        private Double lastPrice; // Current market price
        private String marketCapCategory;
        private Double marketCapValue;

        private List<Alternative> alternatives; // Possible substitutes
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Alternative {
        private String symbol;
        private String isin;
        private Double userWeight;
    }

    public enum ItemStatus {
        HELD, MISSING, SUBSTITUTE
    }
}
