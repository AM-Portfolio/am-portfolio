package com.portfolio.model.analytics.intelligence;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportPreviewResponse {
    private String period;
    private Instant asOf;
    private Map<String, Object> summary;
    private HealthDto health;
    private RiskDto risk;
    private XRayDto xray;
    private Object movers;
    private Object stressSnapshot;
}
