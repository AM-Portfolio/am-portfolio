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
public class StressRequest {
    /** Preset id, e.g. NIFTY_DOWN_10. Null when using custom or presets list. */
    private String preset;
    /** Optional batch of presets (WS7). When non-empty, runs each and returns all scenarios. */
    private List<String> presets;
    private CustomShock custom;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CustomShock {
        private String sector;
        /** Nullable so missing JSON does not silently become 0.0. */
        private Double shockPct;
    }
}
