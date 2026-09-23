package com.portfolio.model.analytics.intelligence;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cached regression history metrics for intelligence / stress β.
 * Omits daily return series to keep Redis payloads small.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CachedIntelligenceHistory {
    private int historyPoints;
    private Double portRetPct;
    private Double niftyRetPct;
    private Double dailyVolPct;
    private Double beta;
    private long computedAtEpochMs;
}
