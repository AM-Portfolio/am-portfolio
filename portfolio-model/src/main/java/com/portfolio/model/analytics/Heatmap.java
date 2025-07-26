package com.portfolio.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents a sector/industry heatmap showing relative performance
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Heatmap {
    private String indexSymbol;
    private Instant timestamp;
    private List<SectorPerformance> sectors;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorPerformance {
        private String sectorName;
        private double performance;
        private double changePercent;
        private String color; // For UI representation (e.g., "green", "red", or hex code)
    }
}
