package com.portfolio.model.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents sector/industry allocation percentages within an index or portfolio
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectorAllocation {
    private String indexSymbol; // Used for index analytics
    private String portfolioId; // Used for portfolio analytics
    private Instant timestamp;
    private List<SectorWeight> sectorWeights;
    private List<IndustryWeight> industryWeights;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorWeight {
        private String sectorName;
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private double weightPercentage = 0.0;
        private double marketCap;
        private List<String> topStocks; // Top stocks in this sector
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IndustryWeight {
        private String industryName;
        private String parentSector;
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private double weightPercentage = 0.0;
        private double marketCap;
        private List<String> topStocks; // Top stocks in this industry
    }
}
