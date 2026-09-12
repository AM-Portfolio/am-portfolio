package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.PortfolioModelV1;
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
import com.portfolio.redis.service.PortfolioIntelligenceRedisService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates snapshot → health + risk + xray; stress; what-if; report preview.
 * Intelligence responses use fail-open Redis cache + in-process single-flight (WS7).
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
    private final PortfolioIntelligenceRedisService intelligenceRedisService;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, CompletableFuture<PortfolioIntelligenceResponse>> inFlight =
            new ConcurrentHashMap<>();

    public PortfolioIntelligenceResponse intelligence(String portfolioId) {
        return intelligence(portfolioId, null);
    }

    public PortfolioIntelligenceResponse intelligence(String portfolioId, PortfolioModelV1 ownedPortfolio) {
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean cacheHit = false;
        try {
            var cached = intelligenceRedisService.get(portfolioId);
            if (cached.isPresent()) {
                cacheHit = true;
                return cached.get();
            }

            CompletableFuture<PortfolioIntelligenceResponse> future = inFlight.computeIfAbsent(portfolioId, id ->
                    CompletableFuture.supplyAsync(() -> {
                        PortfolioIntelligenceSnapshot snapshot = ownedPortfolio != null
                                ? snapshotFactory.buildFromPortfolio(ownedPortfolio)
                                : snapshotFactory.build(id);
                        PortfolioIntelligenceResponse response = toIntelligenceResponse(snapshot);
                        intelligenceRedisService.put(id, response);
                        return response;
                    }));

            try {
                return future.join();
            } finally {
                inFlight.remove(portfolioId, future);
            }
        } finally {
            sample.stop(Timer.builder("portfolio.intel.intelligence")
                    .tag("cache", cacheHit ? "hit" : "miss")
                    .register(meterRegistry));
        }
    }

    public StressResponse stress(String portfolioId, StressRequest request) {
        return stress(portfolioId, request, null);
    }

    public StressResponse stress(String portfolioId, StressRequest request, PortfolioModelV1 ownedPortfolio) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            PortfolioIntelligenceSnapshot snapshot = ownedPortfolio != null
                    ? snapshotFactory.buildFromPortfolio(ownedPortfolio)
                    : snapshotFactory.build(portfolioId);
            StressResponse response = stressEngine.run(snapshot, request != null ? request : new StressRequest());
            return stressEngine.finalizeAbs(response, snapshot.getTotalValue());
        } finally {
            sample.stop(Timer.builder("portfolio.intel.stress").register(meterRegistry));
        }
    }

    public WhatIfResponse whatIf(String portfolioId, WhatIfRequest request) {
        return whatIf(portfolioId, request, null);
    }

    public WhatIfResponse whatIf(String portfolioId, WhatIfRequest request, PortfolioModelV1 ownedPortfolio) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            PortfolioIntelligenceSnapshot snapshot = ownedPortfolio != null
                    ? snapshotFactory.buildFromPortfolio(ownedPortfolio)
                    : snapshotFactory.build(portfolioId);
            return whatIfEngine.simulate(snapshot, request);
        } finally {
            sample.stop(Timer.builder("portfolio.intel.whatif").register(meterRegistry));
        }
    }

    public ReportPreviewResponse reportPreview(String portfolioId, ReportPreviewRequest request) {
        return reportPreview(portfolioId, request, null);
    }

    public ReportPreviewResponse reportPreview(
            String portfolioId, ReportPreviewRequest request, PortfolioModelV1 ownedPortfolio) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String period = request != null && request.getPeriod() != null
                    ? request.getPeriod().trim().toUpperCase(Locale.ROOT)
                    : "WEEKLY";
            if (!"WEEKLY".equals(period) && !"MONTHLY".equals(period)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must be WEEKLY or MONTHLY");
            }

            PortfolioIntelligenceResponse intel = intelligence(portfolioId, ownedPortfolio);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("portfolioId", portfolioId);
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
        } finally {
            sample.stop(Timer.builder("portfolio.intel.report").register(meterRegistry));
        }
    }

    private PortfolioIntelligenceResponse toIntelligenceResponse(PortfolioIntelligenceSnapshot snapshot) {
        long start = System.currentTimeMillis();
        HealthDto health = healthScoreEngine.compute(snapshot);
        RiskDto risk = riskRadarEngine.compute(snapshot);
        XRayDto xray = xRaySummaryBuilder.build(snapshot);
        double confidence = confidence(snapshot);

        PortfolioIntelligenceResponse response = PortfolioIntelligenceResponse.builder()
                .portfolioId(snapshot.getPortfolioId())
                .asOf(Instant.now())
                .confidence(confidence)
                .health(health)
                .risk(risk)
                .xray(xray)
                .build();

        log.info(
                "Intel computed portfolioId={} holdings={} historyPoints={} confidence={} health={} durationMs={}",
                snapshot.getPortfolioId(),
                snapshot.getHoldingsCount(),
                snapshot.getHistoryPoints(),
                confidence,
                health != null ? health.getScore() : null,
                System.currentTimeMillis() - start);
        return response;
    }

    private static double confidence(PortfolioIntelligenceSnapshot snapshot) {
        if (snapshot.getHoldingsCount() <= 0 || snapshot.getTotalValue() <= 0) {
            return 0.0;
        }
        if (snapshot.getHistoryPoints() >= HealthScoreConstants.MIN_HISTORY_POINTS
                && snapshot.getPortRetPct() != null
                && snapshot.getDailyVolPct() != null
                && snapshot.getBeta() != null) {
            return 0.9;
        }
        return 0.55;
    }
}
