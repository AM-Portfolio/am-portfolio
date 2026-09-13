package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.HealthDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden fixture from E2E-BACKEND-PLAN §5 — expects health ~64 Watch.
 */
class HealthScoreEngineTest {

    private final HealthScoreEngine engine = new HealthScoreEngine();

    @Test
    void workedExample_health64_watch() {
        PortfolioIntelligenceSnapshot snapshot = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("fixture")
                .holdings(List.of(
                        holding("HDFCBANK", 250_000, 25.00, "Banking", "LARGE_CAP"),
                        holding("ICICIBANK", 100_000, 10.00, "Banking", "LARGE_CAP"),
                        holding("TCS", 150_000, 15.00, "IT", "LARGE_CAP"),
                        holding("INFY", 50_000, 5.00, "IT", "LARGE_CAP"),
                        holding("RELIANCE", 200_000, 20.00, "Energy", "LARGE_CAP"),
                        holding("MIDA", 100_000, 10.00, "Pharma", "MID_CAP"),
                        holding("SMALLB", 100_000, 10.00, "Pharma", "SMALL_CAP")
                ))
                .totalValue(1_000_000)
                .holdingsCount(7)
                .distinctSectors(4)
                .top1Pct(25.00)
                .maxSectorPct(35.00)
                .maxSectorName("Banking")
                .liquidSharePct(85.00)
                .historyPoints(60)
                .portRetPct(8.7)
                .niftyRetPct(5.0)
                .dailyVolPct(0.70)
                .beta(1.2)
                .build();

        HealthDto health = engine.compute(snapshot);

        Map<String, Integer> byId = health.getComponents().stream()
                .collect(Collectors.toMap(HealthDto.HealthComponentDto::getId, HealthDto.HealthComponentDto::getScore));

        assertEquals(52, byId.get(HealthScoreConstants.ID_DIVERSIFICATION));
        assertEquals(28, byId.get(HealthScoreConstants.ID_CONCENTRATION));
        assertEquals(86, byId.get(HealthScoreConstants.ID_PERFORMANCE));
        assertEquals(72, byId.get(HealthScoreConstants.ID_VOLATILITY));
        assertEquals(85, byId.get(HealthScoreConstants.ID_LIQUIDITY));
        assertEquals(90, byId.get(HealthScoreConstants.ID_BETA));
        assertEquals(88, byId.get(HealthScoreConstants.ID_ALLOCATION));
        assertEquals(55, byId.get(HealthScoreConstants.ID_RISK_RESILIENCE));

        assertEquals(64, health.getScore());
        assertEquals(HealthScoreConstants.BAND_WATCH, health.getBand());

        assertEquals(HealthScoreConstants.SEVERITY_FOCUS, severity(health, HealthScoreConstants.ID_DIVERSIFICATION));
        assertEquals(HealthScoreConstants.SEVERITY_FOCUS, severity(health, HealthScoreConstants.ID_CONCENTRATION));
        assertEquals(HealthScoreConstants.SEVERITY_FOCUS, severity(health, HealthScoreConstants.ID_RISK_RESILIENCE));
        assertEquals(HealthScoreConstants.SEVERITY_OK, severity(health, HealthScoreConstants.ID_PERFORMANCE));
    }

    @Test
    void omitVolBetaAndPerf_whenHistoryBelow20_renormalizes() {
        PortfolioIntelligenceSnapshot snapshot = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("fixture")
                .holdings(List.of(holding("A", 100, 100, "X", "LARGE_CAP")))
                .totalValue(100)
                .holdingsCount(7)
                .distinctSectors(4)
                .top1Pct(25)
                .maxSectorPct(35)
                .maxSectorName("Banking")
                .liquidSharePct(85)
                .historyPoints(0)
                .portRetPct(8.7)
                .niftyRetPct(5.0)
                .build();

        HealthDto health = engine.compute(snapshot);
        List<String> ids = health.getComponents().stream().map(HealthDto.HealthComponentDto::getId).toList();

        assertTrue(!ids.contains(HealthScoreConstants.ID_VOLATILITY));
        assertTrue(!ids.contains(HealthScoreConstants.ID_BETA));
        assertTrue(!ids.contains(HealthScoreConstants.ID_PERFORMANCE));
        assertTrue(ids.contains(HealthScoreConstants.ID_DIVERSIFICATION));
        assertTrue(health.getScore() >= 0 && health.getScore() <= 100);
    }

    private static String severity(HealthDto health, String id) {
        return health.getComponents().stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst()
                .map(HealthDto.HealthComponentDto::getSeverity)
                .orElse(null);
    }

    private static PortfolioIntelligenceSnapshot.Holding holding(
            String symbol, double value, double weightPct, String sector, String cap) {
        return PortfolioIntelligenceSnapshot.Holding.builder()
                .symbol(symbol)
                .value(value)
                .weightPct(weightPct)
                .sector(sector)
                .industry(sector)
                .marketCap(cap)
                .build();
    }
}
