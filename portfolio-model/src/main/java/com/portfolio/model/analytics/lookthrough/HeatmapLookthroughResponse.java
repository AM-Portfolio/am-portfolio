package com.portfolio.model.analytics.lookthrough;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sector drill-down for one ETF or mutual-fund scheme on the portfolio heatmap.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeatmapLookthroughResponse {

    private String schemeIsin;
    private String schemeName;
    private String schemeSymbol;
    private Source source;
    private String asOf;
    private boolean available;
    private Double schemeValue;
    private Coverage coverage;
    private List<SectorSlice> sectors;

    public enum Source {
        ETF_HOLDINGS,
        MF_HOLDINGS,
        UNAVAILABLE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Coverage {
        private int holdingsCount;
        private double isinCoverage;
        private double unknownSectorWeightPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SectorSlice {
        private String sector;
        private Double weightPct;
        private Double value;
        private List<HoldingSlice> holdings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HoldingSlice {
        private String isin;
        private String symbol;
        private String name;
        private Double weightPct;
        private Double value;
        private String marketCapCategory;
    }
}
