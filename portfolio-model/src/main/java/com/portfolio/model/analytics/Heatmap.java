package com.portfolio.model.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents a sector/industry heatmap showing relative performance for an index or portfolio
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Heatmap {
    private String indexSymbol; // Used for index analytics
    private String portfolioId; // Used for portfolio analytics
    private Instant timestamp;
    private List<SectorPerformance> sectors;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorPerformance {
        private String sectorName;
        private double performance;
        private double changePercent;
        private String color; // For UI representation (e.g., "green", "red", or hex code)
    }
}
