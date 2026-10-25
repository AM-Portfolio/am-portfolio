package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.StressRequest;
import com.portfolio.model.analytics.intelligence.StressResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StressEngineTest {

    private final StressEngine engine = new StressEngine();

    @Test
    void niftyDown10_withBetaHalf_impactIsMinusFive() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                0.5,
                holding("A", 100, 60, "Energy"),
                holding("B", 100, 40, "Pharma"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(-5.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals("PORTFOLIO_BETA", res.getMethod());
        assertEquals(0.5, res.getBetaUsed(), 0.01);
        assertEquals(Boolean.FALSE, res.getBetaAssumed());
    }

    @Test
    void niftyDown10_withBetaOne_impactIsMinusTen() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("A", 100, 100, "Energy"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(-10.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(Boolean.FALSE, res.getBetaAssumed());
    }

    @Test
    void niftyDown10_withNegativeBeta_isMeasuredNotAssumed() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                -0.4,
                holding("A", 100, 100, "Energy"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(4.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals("PORTFOLIO_BETA", res.getMethod());
        assertEquals(-0.4, res.getBetaUsed(), 0.01);
        assertEquals(Boolean.FALSE, res.getBetaAssumed());
    }

    @Test
    void niftyDown10_nineteenDaysWithBeta_assumesOneAndShockUsesOne() {
        PortfolioIntelligenceSnapshot snap = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("p1")
                .holdings(List.of(holding("A", 100, 100, "Energy")))
                .totalValue(100)
                .holdingsCount(1)
                .beta(0.5)
                .historyPoints(19)
                .build();
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(-10.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals("ASSUMED_ONE", res.getMethod());
        assertEquals(1.0, res.getBetaUsed(), 0.01);
        assertEquals(Boolean.TRUE, res.getBetaAssumed());
        assertEquals(19, res.getHistoryDays());
    }

    @Test
    void niftyDown10_thirtyDaysNullBeta_assumesOne() {
        PortfolioIntelligenceSnapshot snap = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("p1")
                .holdings(List.of(holding("A", 100, 100, "Energy")))
                .totalValue(100)
                .holdingsCount(1)
                .beta(null)
                .historyPoints(30)
                .build();
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(-10.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals("ASSUMED_ONE", res.getMethod());
        assertEquals(1.0, res.getBetaUsed(), 0.01);
        assertEquals(Boolean.TRUE, res.getBetaAssumed());
    }

    @Test
    void niftyDown10_missingBeta_assumesOne() {
        PortfolioIntelligenceSnapshot snap = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("p1")
                .holdings(List.of(holding("A", 100, 100, "Energy")))
                .totalValue(100)
                .holdingsCount(1)
                .beta(null)
                .historyPoints(0)
                .build();
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(-10.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals("ASSUMED_ONE", res.getMethod());
        assertEquals(Boolean.TRUE, res.getBetaAssumed());
    }

    @Test
    void niftyDown10_longBook_negativeImpact() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("A", 100, 60, "Energy"),
                holding("B", 100, 40, "Pharma"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(1, res.getScenarios().size());
        assertTrue(res.getScenarios().get(0).getPctImpact() < 0);
    }

    @Test
    void unknownPreset_returns400() {
        PortfolioIntelligenceSnapshot snap = snapshot(1.0, holding("A", 100, 100, "Energy"));
        assertThrows(ResponseStatusException.class,
                () -> engine.run(snap, StressRequest.builder().preset("NOT_A_PRESET").build()));
    }

    @Test
    void customMissingShock_returns400() {
        PortfolioIntelligenceSnapshot snap = snapshot(1.0, holding("A", 100, 100, "IT"));
        StressRequest.CustomShock custom = StressRequest.CustomShock.builder()
                .sector("IT")
                .shockPct(null)
                .build();
        assertThrows(ResponseStatusException.class,
                () -> engine.run(snap, StressRequest.builder().custom(custom).build()));
    }

    @Test
    void batchPresets_returnsAll() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("HDFC", 100, 50, "Financial Services"),
                holding("TCS", 100, 50, "Information Technology"));
        StressResponse res = engine.run(snap, StressRequest.builder()
                .presets(List.of("NIFTY_DOWN_10", "BANKING_DOWN_20", "IT_DOWN_15"))
                .build());
        assertEquals(3, res.getScenarios().size());
    }

    @Test
    void sensexAndSectorPresets_apply() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("MARUTI", 100, 40, "Auto"),
                holding("SUNPHARMA", 100, 30, "Pharma"),
                holding("RELIANCE", 100, 30, "Energy"));
        StressResponse sensex = engine.run(snap, StressRequest.builder().preset("SENSEX_DOWN_10").build());
        assertTrue(sensex.getScenarios().get(0).getPctImpact() < 0);
        StressResponse auto = engine.run(snap, StressRequest.builder().preset("AUTO_DOWN_20").build());
        assertEquals(-8.0, auto.getScenarios().get(0).getPctImpact(), 0.01);
    }

    @Test
    void financialServices_matchesBankingShock() {
        PortfolioIntelligenceSnapshot snap = snapshot(1.0, holding("HDFC", 100, 100, "Financial Services"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("BANKING_DOWN_20").build());
        assertEquals(-20.0, res.getScenarios().get(0).getPctImpact(), 0.01);
    }

    @Test
    void automobile_alias_matchesAutoShock() {
        PortfolioIntelligenceSnapshot snap = snapshot(1.0, holding("MARUTI", 100, 100, "Automobiles"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("AUTO_DOWN_20").build());
        assertEquals(-20.0, res.getScenarios().get(0).getPctImpact(), 0.01);
    }

    @Test
    void customSector_matchesIt_notIndustrials_andSetsNote() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("TCS", 100, 40, "Information Technology"),
                holding("LT", 100, 60, "Industrials"));
        StressRequest.CustomShock custom = StressRequest.CustomShock.builder()
                .sector("IT")
                .shockPct(-19.0)
                .build();
        StressResponse res = engine.run(snap, StressRequest.builder().custom(custom).build());
        assertEquals(1, res.getScenarios().size());
        StressResponse.ScenarioImpactDto row = res.getScenarios().get(0);
        assertEquals(-7.6, row.getPctImpact(), 0.01);
        assertEquals(40.0, row.getMatchedWeightPct(), 0.01);
        assertEquals(1, row.getMatchedHoldings());
        assertEquals(-19.0, row.getAppliedShockPct(), 0.01);
        assertTrue(row.getNote() != null && row.getNote().contains("40.0%"));
        assertTrue(row.getNote().contains("shock"));
        StressResponse.ScenarioImpactDto finalized = engine.withAbs(row, 200);
        assertEquals(-19.0, finalized.getAppliedShockPct(), 0.01);
    }

    @Test
    void customSector_fmcgAlias_matchesFullName() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("HUL", 100, 10, "Fast Moving Consumer Goods"),
                holding("TCS", 100, 90, "Information Technology"));
        StressRequest.CustomShock custom = StressRequest.CustomShock.builder()
                .sector("FMCG")
                .shockPct(18.0)
                .build();
        StressResponse res = engine.run(snap, StressRequest.builder().custom(custom).build());
        StressResponse.ScenarioImpactDto row = res.getScenarios().get(0);
        assertEquals(1.8, row.getPctImpact(), 0.01);
        assertEquals(10.0, row.getMatchedWeightPct(), 0.01);
        assertEquals(18.0, row.getAppliedShockPct(), 0.01);
        assertTrue(row.getNote().contains("shock +18%"));
    }

    @Test
    void customSector_noMatch_zeroImpactWithNote() {
        PortfolioIntelligenceSnapshot snap = snapshot(1.0, holding("LT", 100, 100, "Industrials"));
        StressRequest.CustomShock custom = StressRequest.CustomShock.builder()
                .sector("IT")
                .shockPct(-19.0)
                .build();
        StressResponse res = engine.run(snap, StressRequest.builder().custom(custom).build());
        StressResponse.ScenarioImpactDto row = res.getScenarios().get(0);
        assertEquals(0.0, row.getPctImpact(), 0.01);
        assertEquals(0, row.getMatchedHoldings());
        assertTrue(row.getNote().contains("No holdings match"));
    }

    private static PortfolioIntelligenceSnapshot snapshot(
            double beta, PortfolioIntelligenceSnapshot.Holding... holdings) {
        return PortfolioIntelligenceSnapshot.builder()
                .portfolioId("p1")
                .holdings(List.of(holdings))
                .totalValue(200)
                .holdingsCount(holdings.length)
                .beta(beta)
                .historyPoints(30)
                .build();
    }

    private static PortfolioIntelligenceSnapshot.Holding holding(
            String symbol, double value, double weightPct, String sector) {
        return PortfolioIntelligenceSnapshot.Holding.builder()
                .symbol(symbol)
                .value(value)
                .weightPct(weightPct)
                .sector(sector)
                .industry(sector)
                .marketCap("LARGE_CAP")
                .build();
    }
}
