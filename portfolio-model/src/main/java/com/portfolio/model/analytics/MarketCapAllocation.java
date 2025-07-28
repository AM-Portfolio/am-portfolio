package com.portfolio.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents market capitalization allocation breakdown for an index
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketCapAllocation {
    private String portfolioId;
    private String indexSymbol;
    private Instant timestamp;
    private List<CapSegment> segments;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CapSegment {
        private String segmentName; // e.g., "Large Cap", "Mid Cap", "Small Cap"
        private double weightPercentage;
        private double totalMarketCap;
        private int numberOfStocks;
        private List<String> topStocks; // Representative stocks in this segment
    }
}
