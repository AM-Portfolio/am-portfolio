package com.portfolio.model.analytics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarketCapAllocation {
    @JsonIgnore
    private String indexSymbol; // Used for index analytics
    @JsonIgnore
    private String portfolioId; // Used for portfolio analytics
    @JsonIgnore
    private Instant timestamp;
    private List<CapSegment> segments;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CapSegment {
        private String segmentName; // e.g., "Large Cap", "Mid Cap", "Small Cap"
        private double weightPercentage;
        private double segmentValue;
        private int numberOfStocks;
        private List<String> topStocks; // Representative stocks in this segment
    }
}
