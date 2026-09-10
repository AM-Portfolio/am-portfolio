package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.HealthDto;
import com.portfolio.model.analytics.intelligence.PortfolioIntelligenceResponse;
import com.portfolio.model.analytics.intelligence.ReportPreviewRequest;
import com.portfolio.model.analytics.intelligence.ReportPreviewResponse;
import com.portfolio.model.analytics.intelligence.RiskDto;
import com.portfolio.model.analytics.intelligence.StressRequest;
import com.portfolio.model.analytics.intelligence.StressResponse;
import com.portfolio.model.analytics.intelligence.WhatIfRequest;
import com.portfolio.model.analytics.intelligence.WhatIfResponse;
import com.portfolio.model.analytics.intelligence.XRayDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates snapshot → health + risk + xray; stress; what-if; report preview.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioIntelligenceService {

    private final PortfolioIntelligenceSnapshotFactory snapshotFactory;
    private final HealthScoreEngine healthScoreEngine;
    private final RiskRadarEngine riskRadarEngine;
    private final XRaySummaryBuilder xRaySummaryBuilder;
    private final StressEngine stressEngine;
    private final WhatIfEngine whatIfEngine;

    public PortfolioIntelligenceResponse intelligence(String portfolioId) {
        PortfolioIntelligenceSnapshot snapshot = snapshotFactory.build(portfolioId);
        return toIntelligenceResponse(snapshot);
    }

    public StressResponse stress(String portfolioId, StressRequest request) {
        PortfolioIntelligenceSnapshot snapshot = snapshotFactory.build(portfolioId);
        StressResponse response = stressEngine.run(snapshot, request != null ? request : new StressRequest());
        return stressEngine.finalizeAbs(response, snapshot.getTotalValue());
    }

    public WhatIfResponse whatIf(String portfolioId, WhatIfRequest request) {
        PortfolioIntelligenceSnapshot snapshot = snapshotFactory.build(portfolioId);
        return whatIfEngine.simulate(snapshot, request);
    }

    public ReportPreviewResponse reportPreview(String portfolioId, ReportPreviewRequest request) {
        String period = request != null && request.getPeriod() != null
                ? request.getPeriod().trim().toUpperCase(Locale.ROOT)
                : "WEEKLY";
        if (!"WEEKLY".equals(period) && !"MONTHLY".equals(period)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must be WEEKLY or MONTHLY");
        }

        PortfolioIntelligenceSnapshot snapshot = snapshotFactory.build(portfolioId);
        PortfolioIntelligenceResponse intel = toIntelligenceResponse(snapshot);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("portfolioId", portfolioId);
        summary.put("holdingsCount", snapshot.getHoldingsCount());
        summary.put("totalValue", snapshot.getTotalValue());
        summary.put("confidence", intel.getConfidence());

        return ReportPreviewResponse.builder()
                .period(period)
                .asOf(intel.getAsOf())
                .summary(summary)
                .health(intel.getHealth())
                .risk(intel.getRisk())
                .xray(intel.getXray())
                .movers(null)
                .stressSnapshot(null)
                .build();
    }

    private PortfolioIntelligenceResponse toIntelligenceResponse(PortfolioIntelligenceSnapshot snapshot) {
        HealthDto health = healthScoreEngine.compute(snapshot);
        RiskDto risk = riskRadarEngine.compute(snapshot);
        XRayDto xray = xRaySummaryBuilder.build(snapshot);
        double confidence = confidence(snapshot);

        return PortfolioIntelligenceResponse.builder()
                .portfolioId(snapshot.getPortfolioId())
                .asOf(Instant.now())
                .confidence(confidence)
                .health(health)
                .risk(risk)
                .xray(xray)
                .build();
    }

    private static double confidence(PortfolioIntelligenceSnapshot snapshot) {
        if (snapshot.getHoldingsCount() <= 0 || snapshot.getTotalValue() <= 0) {
            return 0.0;
        }
        if (snapshot.getHistoryPoints() >= HealthScoreConstants.MIN_HISTORY_POINTS) {
            return 0.9;
        }
        return 0.55;
    }
}
