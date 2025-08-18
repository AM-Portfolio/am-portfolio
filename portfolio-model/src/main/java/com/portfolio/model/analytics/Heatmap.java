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
 * Represents a sector/industry heatmap showing relative performance for an index or portfolio
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Heatmap {
    @JsonIgnore
    private String indexSymbol; // Used for index analytics
    @JsonIgnore
    private String portfolioId; // Used for portfolio analytics
    @JsonIgnore
    private Instant timestamp;
    private List<SectorPerformance> sectors;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorPerformance {
        private String sectorName;
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private double performance = 0.0;
        @Builder.Default
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private double changePercent = 0.0;
        private String color; // For UI representation (e.g., "green", "red", or hex code)
    }
}
