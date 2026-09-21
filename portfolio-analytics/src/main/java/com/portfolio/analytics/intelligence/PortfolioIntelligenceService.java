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
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Iterator;
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

    private final ConcurrentHashMap<String, CachedStress> stressL1 = new ConcurrentHashMap<>();
    private static final long STRESS_L1_TTL_MS = 30_000L;

    private record CachedStress(StressResponse response, long expiresAtMs) {}

    @PostConstruct
    void wireHistoryWarmedListener() {
        snapshotFactory.setHistoryWarmedListener(this::evictStressL1);
    }

    /** Drop sticky ASSUMED stress entries after hist Redis warm. */
    void evictStressL1(String portfolioId) {
        if (portfolioId == null || portfolioId.isBlank()) {
            return;
        }
        String prefix = portfolioId + "|";
        Iterator<Map.Entry<String, CachedStress>> it = stressL1.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CachedStress> e = it.next();
            if (e.getKey().startsWith(prefix)) {
                it.remove();
            }
        }
    }

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

            CompletableFuture<PortfolioIntelligenceResponse> created = new CompletableFuture<>();
            CompletableFuture<PortfolioIntelligenceResponse> existing =
                    inFlight.putIfAbsent(portfolioId, created);
            if (existing == null) {
                try {
                    PortfolioIntelligenceSnapshot snapshot = buildSnapshot(portfolioId, ownedPortfolio, true);
                    PortfolioIntelligenceResponse response = toIntelligenceResponse(snapshot);
                    intelligenceRedisService.put(portfolioId, response);
                    created.complete(response);
                    return response;
                } catch (Throwable t) {
                    created.completeExceptionally(t);
                    throw unwrapStatus(t);
                } finally {
                    inFlight.remove(portfolioId, created);
                }
            }

            try {
                return existing.join();
            } catch (java.util.concurrent.CompletionException e) {
                throw unwrapStatus(e);
            }
        } finally {
            sample.stop(Timer.builder("portfolio.intel.intelligence")
                    .tag("cache", cacheHit ? "hit" : "miss")
                    .register(meterRegistry));
        }
    }

    private PortfolioIntelligenceSnapshot buildSnapshot(
            String portfolioId, PortfolioModelV1 ownedPortfolio, boolean includeHistory) {
        return buildSnapshot(portfolioId, ownedPortfolio, includeHistory, true, 0L);
    }

    private PortfolioIntelligenceSnapshot buildSnapshot(
            String portfolioId,
            PortfolioModelV1 ownedPortfolio,
            boolean includeHistory,
            boolean fetchHistoryIfMiss) {
        return buildSnapshot(portfolioId, ownedPortfolio, includeHistory, fetchHistoryIfMiss, 0L);
    }

    private PortfolioIntelligenceSnapshot buildSnapshot(
            String portfolioId,
            PortfolioModelV1 ownedPortfolio,
            boolean includeHistory,
            boolean fetchHistoryIfMiss,
            long historyTimeoutOverrideMs) {
        if (ownedPortfolio == null) {
            return snapshotFactory.build(portfolioId, includeHistory);
        }
        if (isAggregateCacheKey(portfolioId)) {
            return snapshotFactory.buildFromPortfolio(
                    ownedPortfolio,
                    includeHistory,
                    AggregatePortfolioKeys.RESPONSE_PORTFOLIO_ID,
                    portfolioId,
                    fetchHistoryIfMiss,
                    historyTimeoutOverrideMs);
        }
        return snapshotFactory.buildFromPortfolio(
                ownedPortfolio, includeHistory, null, null, fetchHistoryIfMiss, historyTimeoutOverrideMs);
    }

    private static boolean isAggregateCacheKey(String portfolioId) {
        return portfolioId != null && portfolioId.startsWith("user:") && portfolioId.endsWith(":all");
    }

    private static RuntimeException unwrapStatus(Throwable t) {
        Throwable cur = t;
        while (cur instanceof java.util.concurrent.CompletionException && cur.getCause() != null) {
            cur = cur.getCause();
        }
        if (cur instanceof ResponseStatusException rse) {
            return rse;
        }
        if (cur instanceof RuntimeException re) {
            return re;
        }
        return new RuntimeException(cur);
    }

    public StressResponse stress(String portfolioId, StressRequest request) {
        return stress(portfolioId, request, null);
    }

    public StressResponse stress(String portfolioId, StressRequest request, PortfolioModelV1 ownedPortfolio) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            StressRequest effective = request != null ? request : new StressRequest();
            String l1Key = stressCacheKey(portfolioId, effective);
            CachedStress hit = stressL1.get(l1Key);
            if (hit != null && hit.expiresAtMs() > System.currentTimeMillis()) {
                return hit.response();
            }

            // Fetch hist with stress timeout (20s); joins in-flight intel warm when present.
            PortfolioIntelligenceSnapshot snapshot = buildSnapshot(
                    portfolioId,
                    ownedPortfolio,
                    true,
                    true,
                    snapshotFactory.getHistoryTimeoutStressMs());
            StressResponse response = stressEngine.run(snapshot, effective);
            StressResponse finalized = stressEngine.finalizeAbs(response, snapshot.getTotalValue());
            if (isAggregateCacheKey(portfolioId) && finalized != null) {
                finalized.setPortfolioId(AggregatePortfolioKeys.RESPONSE_PORTFOLIO_ID);
            }
            // Only cache measured-β responses — never sticky ASSUMED_ONE.
            if (finalized != null && !Boolean.TRUE.equals(finalized.getBetaAssumed())) {
                stressL1.put(l1Key, new CachedStress(finalized, System.currentTimeMillis() + STRESS_L1_TTL_MS));
            }
            return finalized;
        } finally {
            sample.stop(Timer.builder("portfolio.intel.stress").register(meterRegistry));
        }
    }

    private static String stressCacheKey(String portfolioId, StressRequest request) {
        StringBuilder sb = new StringBuilder(portfolioId != null ? portfolioId : "");
        sb.append('|');
        if (request.getCustom() != null) {
            sb.append("C:").append(request.getCustom().getSector())
                    .append(':').append(request.getCustom().getShockPct());
        } else if (request.getPresets() != null && !request.getPresets().isEmpty()) {
            sb.append("P:").append(String.join(",", request.getPresets()));
        } else {
            sb.append("p:").append(request.getPreset() != null ? request.getPreset() : "NIFTY_DOWN_10");
        }
        return sb.toString();
    }

    public WhatIfResponse whatIf(String portfolioId, WhatIfRequest request) {
        return whatIf(portfolioId, request, null);
    }

    public WhatIfResponse whatIf(String portfolioId, WhatIfRequest request, PortfolioModelV1 ownedPortfolio) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            PortfolioIntelligenceSnapshot snapshot = buildSnapshot(portfolioId, ownedPortfolio, false);
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
