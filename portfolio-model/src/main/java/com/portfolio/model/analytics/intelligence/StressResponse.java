package com.portfolio.model.analytics.intelligence;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StressResponse {
    private String portfolioId;
    private String estimateLabel;
    private List<ScenarioImpactDto> scenarios;

    /** HOLDING_BETA | PORTFOLIO_BETA | SECTOR_WEIGHT | ASSUMED_ONE */
    private String method;
    /** Portfolio β used for index scenarios; null when sector-only / assumed. */
    private Double betaUsed;
    /** Benchmark for β (e.g. NIFTY50). Sensex presets may share NIFTY β until separate series. */
    private String benchmark;
    private Integer historyDays;
    /** True when β defaulted to 1.0 because history/β missing. */
    private Boolean betaAssumed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ScenarioImpactDto {
        private String id;
        private double pctImpact;
        private double absImpact;
        /** Custom sector: book weight matched by SectorMatcher (%). */
        private Double matchedWeightPct;
        /** Custom sector: holdings matched. */
        private Integer matchedHoldings;
        /** Echo of request custom.shockPct (signed percent points). */
        private Double appliedShockPct;
        /** Short note e.g. no holdings matched. */
        private String note;
    }
}
