package com.portfolio.basket.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BasketOpportunity {
    private String etfIsin;
    private String etfName;
    private double matchScore;
    private double replicaScore; // Score based on weight alignment
    private boolean readyToReplicate; // true if replicaScore >= 90
    private int totalItems;
    private int heldCount;
    private int missingCount;
    private Double totalPortfolioValue;
    private Double remainingPortfolioValue;
    private Double investmentAmount;
    private Double minimumInvestmentAmount;
    private Double heldMatchScore;
    private Double substituteMatchScore;
    private Double missingMatchScore;

    // --- New fields returned from calculateBasketQuantities ---
    private List<String> excludedSymbols;   // symbols the user excluded — echoed back so UI stays in sync
    private Double actualInvestmentCost;    // sum of floor(qty) * lastPrice across all BUY items
    private Double budgetVariance;          // actualInvestmentCost - investmentAmount (+ve = over budget)
    private Double freshOrderAmount;        // alias clarity for UI — fresh buy total
    private Double heldCoverageValue;       // value of held stock applied to basket targets
    private Double budgetUtilization;       // (held + fresh) / investmentAmount * 100

    // Basket profile for substitute UX
    private Boolean sectorialBasket;        // true when ETF is sector-concentrated (>75% one sector)
    private String dominantSector;          // human-readable dominant sector key
    private List<String> etfConstituentIsins; // ETF index members for broad-basket search filter

    // apply-substitutes feedback
    private Integer appliedSubstituteCount;
    private List<String> substituteWarnings;

    private List<BasketItem> composition;
    private List<BasketItem> buyList; // Stocks to buy to reach 100% or bridge gap

    @Data
    @Builder(toBuilder = true)
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
        private Double rebalancedWeight; // Pro-rata redistributed weight
        private Double buyQuantity; // Suggested if MISSING
        private Double lastPrice; // Current market price
        private String marketCapCategory;
        private Double marketCapValue;
        private Double targetQuantity; // Ideal total quantity user should hold for this stock in this basket
        private Boolean targetQuantityLocked; // Whether this quantity was manually adjusted and should be excluded from surplus redistributions

        private Double heldQuantity; // Actual quantity held in main portfolio
        private Double heldAveragePrice; // Average buying price of held stock
        private String userHoldingIsin; // ISIN of held/substitute share moved

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
        private Double quantity;
        private Double lastPrice;
        private String sector;
        private boolean isSameSector;
        private boolean canFullyCover;
        private String coverageLabel;
    }

    public enum ItemStatus {
        HELD, MISSING, SUBSTITUTE, EXCLUDED
    }
}
