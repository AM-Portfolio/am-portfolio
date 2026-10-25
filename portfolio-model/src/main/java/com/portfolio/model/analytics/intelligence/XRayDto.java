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
public class XRayDto {
    private List<WeightSliceDto> sectorWeights;
    private List<WeightSliceDto> industryWeights;
    private List<WeightSliceDto> marketCapWeights;
    /** EQUITY / FIXED_INCOME / COMMODITY / CASH / MUTUAL_FUND slices (sum ≈ 100%). */
    private List<WeightSliceDto> assetClassWeights;
    /** Portfolio book NAV used as X-Ray denominator (INR). */
    private Double totalValue;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WeightSliceDto {
        private String name;
        private double weightPct;
        /** Absolute INR exposure for this slice. */
        private Double value;
    }
}
