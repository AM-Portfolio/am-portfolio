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

    /** {@code PORTFOLIO_BETA} when historyPoints ≥ 20 and β is finite; otherwise {@code ASSUMED_ONE}. */
    private String method;
    /** β applied to index presets. Always present: measured value, or 1.0 when assumed. */
    private Double betaUsed;
    /** Benchmark for β (e.g. NIFTY50). Sensex presets may share NIFTY β until separate series. */
    private String benchmark;
    /** Aligned daily-return count used for β (not calendar days). */
    private Integer historyDays;
    /** True when β defaulted to 1.0 (history &lt; 20 or β missing/non-finite). */
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
