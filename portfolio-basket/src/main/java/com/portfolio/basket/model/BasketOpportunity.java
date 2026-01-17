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
    private int totalItems;
    private int heldCount;
    private int missingCount;

    private List<BasketItem> composition;

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
    }

    public enum ItemStatus {
        HELD, MISSING, SUBSTITUTE
    }
}
