package com.portfolio.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents sector/industry allocation percentages within an index
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorAllocation {
    private String indexSymbol;
    private Instant timestamp;
    private List<SectorWeight> sectorWeights;
    private List<IndustryWeight> industryWeights;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorWeight {
        private String sectorName;
        private double weightPercentage;
        private double marketCap;
        private List<String> topStocks; // Top stocks in this sector
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndustryWeight {
        private String industryName;
        private String parentSector;
        private double weightPercentage;
        private double marketCap;
        private List<String> topStocks; // Top stocks in this industry
    }
}
