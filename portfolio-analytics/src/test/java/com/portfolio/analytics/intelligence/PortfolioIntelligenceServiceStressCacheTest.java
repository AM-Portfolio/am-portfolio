package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.StressRequest;
import com.portfolio.model.analytics.intelligence.StressResponse;
import com.portfolio.redis.service.PortfolioIntelligenceRedisService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioIntelligenceServiceStressCacheTest {

    @Mock
    private PortfolioIntelligenceSnapshotFactory snapshotFactory;
    @Mock
    private HealthScoreEngine healthScoreEngine;
    @Mock
    private RiskRadarEngine riskRadarEngine;
    @Mock
    private XRaySummaryBuilder xRaySummaryBuilder;
    @Mock
    private WhatIfEngine whatIfEngine;
    @Mock
    private IntelligenceSuggestService intelligenceSuggestService;
    @Mock
    private PortfolioIntelligenceRedisService intelligenceRedisService;

    private PortfolioIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioIntelligenceService(
                snapshotFactory,
                healthScoreEngine,
                riskRadarEngine,
                xRaySummaryBuilder,
                new StressEngine(),
                whatIfEngine,
                intelligenceSuggestService,
                intelligenceRedisService,
                new SimpleMeterRegistry());
    }

    @Test
    void stress_doesNotCacheAssumed_thenServesMeasuredOnSecondCall() {
        when(snapshotFactory.getHistoryTimeoutStressMs()).thenReturn(20_000L);
        when(snapshotFactory.build(eq("p1"), eq(true)))
                .thenReturn(snap(null, 0), snap(0.5, 30));

        StressRequest req = StressRequest.builder().preset("NIFTY_DOWN_10").build();
        StressResponse first = service.stress("p1", req);
        assertThat(first.getBetaAssumed()).isTrue();
        assertThat(first.getMethod()).isEqualTo("ASSUMED_ONE");
        assertThat(first.getBetaUsed()).isEqualTo(1.0);

        StressResponse second = service.stress("p1", req);
        assertThat(second.getBetaAssumed()).isFalse();
        assertThat(second.getMethod()).isEqualTo("PORTFOLIO_BETA");
        assertThat(second.getBetaUsed()).isEqualTo(0.5);
        assertThat(second.getScenarios().get(0).getPctImpact()).isEqualTo(-5.0);

        StressResponse third = service.stress("p1", req);
        assertThat(third.getBetaAssumed()).isFalse();
        assertThat(third.getBetaUsed()).isEqualTo(0.5);

        verify(snapshotFactory, times(2)).build(eq("p1"), eq(true));
    }

    @Test
    void finalizeSnapshot_omitsBetaWhenHistoryBelowTwenty() {
        PortfolioIntelligenceSnapshot snap = PortfolioIntelligenceSnapshotFactory.finalizeSnapshot(
                "p1",
                List.of(PortfolioIntelligenceSnapshot.Holding.builder()
                        .symbol("A")
                        .value(100)
                        .weightPct(100)
                        .sector("Energy")
                        .build()),
                19,
                1.0,
                2.0,
                3.0,
                0.5);
        assertThat(snap.getBeta()).isNull();
        assertThat(snap.getHistoryPoints()).isEqualTo(19);
        assertThat(snap.getDailyVolPct()).isNull();
    }

    private static PortfolioIntelligenceSnapshot snap(Double beta, int historyPoints) {
        return PortfolioIntelligenceSnapshot.builder()
                .portfolioId("p1")
                .holdings(List.of(PortfolioIntelligenceSnapshot.Holding.builder()
                        .symbol("A")
                        .value(100)
                        .weightPct(100)
                        .sector("Energy")
                        .build()))
                .totalValue(100)
                .holdingsCount(1)
                .beta(beta)
                .historyPoints(historyPoints)
                .build();
    }
}
