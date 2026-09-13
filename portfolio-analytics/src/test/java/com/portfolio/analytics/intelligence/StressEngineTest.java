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
    void niftyDown10_longBook_negativeImpact() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                holding("A", 100, 60, "Energy"),
                holding("B", 100, 40, "Pharma"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(1, res.getScenarios().size());
        assertTrue(res.getScenarios().get(0).getPctImpact() < 0);
    }

    @Test
    void unknownPreset_returns400() {
        PortfolioIntelligenceSnapshot snap = snapshot(holding("A", 100, 100, "Energy"));
        assertThrows(ResponseStatusException.class,
                () -> engine.run(snap, StressRequest.builder().preset("NOT_A_PRESET").build()));
    }

    @Test
    void customMissingShock_returns400() {
        PortfolioIntelligenceSnapshot snap = snapshot(holding("A", 100, 100, "IT"));
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
        PortfolioIntelligenceSnapshot snap = snapshot(holding("HDFC", 100, 100, "Financial Services"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("BANKING_DOWN_20").build());
        assertEquals(-20.0, res.getScenarios().get(0).getPctImpact(), 0.01);
    }

    private static PortfolioIntelligenceSnapshot snapshot(PortfolioIntelligenceSnapshot.Holding... holdings) {
        return PortfolioIntelligenceSnapshot.builder()
                .portfolioId("p1")
                .holdings(List.of(holdings))
                .totalValue(200)
                .holdingsCount(holdings.length)
                .beta(1.0)
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
