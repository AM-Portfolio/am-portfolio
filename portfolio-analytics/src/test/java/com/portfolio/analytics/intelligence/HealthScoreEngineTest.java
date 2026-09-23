package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.HealthDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden fixtures — legacy health ~64 Watch; health_v2 D1–D4 + Vol-U*.
 */
class HealthScoreEngineTest {

    private final HealthScoreEngine engine = new HealthScoreEngine();

    @BeforeEach
    void resetFlag() {
        engine.setHealthV2(false);
    }

    @Test
    void workedExample_health64_watch() {
        PortfolioIntelligenceSnapshot snapshot = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("fixture")
                .holdings(List.of(
                        holding("HDFCBANK", 250_000, 25.00, "Banking", "LARGE_CAP", null),
                        holding("ICICIBANK", 100_000, 10.00, "Banking", "LARGE_CAP", null),
                        holding("TCS", 150_000, 15.00, "IT", "LARGE_CAP", null),
                        holding("INFY", 50_000, 5.00, "IT", "LARGE_CAP", null),
                        holding("RELIANCE", 200_000, 20.00, "Energy", "LARGE_CAP", null),
                        holding("MIDA", 100_000, 10.00, "Pharma", "MID_CAP", null),
                        holding("SMALLB", 100_000, 10.00, "Pharma", "SMALL_CAP", null)
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
                .holdings(List.of(holding("A", 100, 100, "X", "LARGE_CAP", null)))
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

    @Test
    void d1_equityOnly_divBelow95() {
        engine.setHealthV2(true);
        PortfolioIntelligenceSnapshot snapshot = equalEquitySnapshot(15, 10);
        int div = roundDiv(engine.compute(snapshot));
        assertTrue(div < 95, "D1 equity-only div should be <95, got " + div);
        String reason = divReason(engine.compute(snapshot));
        assertTrue(reason.contains("Classes") || reason.contains("effN"), reason);
    }

    @Test
    void d2_multiClass_divAboveD1() {
        engine.setHealthV2(true);
        PortfolioIntelligenceSnapshot equity = equalEquitySnapshot(15, 10);
        int d1 = roundDiv(engine.compute(equity));

        List<PortfolioIntelligenceSnapshot.Holding> holdings = new ArrayList<>();
        for (PortfolioIntelligenceSnapshot.Holding h : equity.getHoldings()) {
            holdings.add(holding(h.getSymbol(), h.getValue(), h.getWeightPct() * 0.75,
                    h.getSector(), h.getMarketCap(), HealthScoreEngine.ASSET_EQUITY));
        }
        holdings.add(holding("DEBT1", 200_000, 20.0, "Debt", "NA", HealthScoreEngine.ASSET_FIXED_INCOME));
        holdings.add(holding("GOLD1", 50_000, 5.0, "Metal", "NA", HealthScoreEngine.ASSET_COMMODITY));

        PortfolioIntelligenceSnapshot multi = PortfolioIntelligenceSnapshot.builder()
                .portfolioId(equity.getPortfolioId())
                .holdings(holdings)
                .totalValue(1_000_000)
                .holdingsCount(holdings.size())
                .distinctSectors(equity.getDistinctSectors() + 2)
                .top1Pct(equity.getTop1Pct() * 0.75)
                .maxSectorPct(equity.getMaxSectorPct() * 0.75)
                .maxSectorName(equity.getMaxSectorName())
                .liquidSharePct(85)
                .historyPoints(0)
                .build();

        int d2 = roundDiv(engine.compute(multi));
        assertTrue(d2 > d1, "D2 multi-class " + d2 + " should be > D1 " + d1);
    }

    @Test
    void d3_singleName_divBelow40() {
        engine.setHealthV2(true);
        PortfolioIntelligenceSnapshot snapshot = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("d3")
                .holdings(List.of(holding("ONLY", 1_000_000, 100, "IT", "LARGE_CAP", HealthScoreEngine.ASSET_EQUITY)))
                .totalValue(1_000_000)
                .holdingsCount(1)
                .distinctSectors(1)
                .top1Pct(100)
                .maxSectorPct(100)
                .maxSectorName("IT")
                .liquidSharePct(100)
                .historyPoints(0)
                .build();
        int div = roundDiv(engine.compute(snapshot));
        assertTrue(div < 40, "D3 single-name div should be <40, got " + div);
    }

    @Test
    void d4_flagOff_legacyCountFormula() {
        engine.setHealthV2(false);
        PortfolioIntelligenceSnapshot snapshot = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("d4")
                .holdings(List.of(holding("A", 100, 100, "X", "LARGE_CAP", null)))
                .holdingsCount(7)
                .distinctSectors(4)
                .top1Pct(25)
                .maxSectorPct(35)
                .liquidSharePct(85)
                .historyPoints(0)
                .build();
        // legacy: 0.5*min(100,7*8)+0.5*min(100,4*12) = 0.5*56+0.5*48 = 52
        assertEquals(52, roundDiv(engine.compute(snapshot)));
        assertEquals(52.0, HealthScoreEngine.diversificationLegacy(snapshot), 0.001);
    }

    @Test
    void volU1_annualizeDailyOnePct() {
        double ann = HealthScoreEngine.annualizedVolPct(1.0);
        assertEquals(15.87, ann, 0.02);
        assertEquals(88, Math.round(HealthScoreEngine.volatilityFromAnnPct(ann)));
    }

    @Test
    void volU2_insufficientHistory_emits55() {
        engine.setHealthV2(true);
        PortfolioIntelligenceSnapshot snapshot = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("vol-u2")
                .holdings(List.of(holding("A", 100, 100, "X", "LARGE_CAP", null)))
                .holdingsCount(1)
                .distinctSectors(1)
                .top1Pct(100)
                .maxSectorPct(100)
                .liquidSharePct(100)
                .historyPoints(0)
                .build();
        HealthDto health = engine.compute(snapshot);
        Map<String, HealthDto.HealthComponentDto> byId = health.getComponents().stream()
                .collect(Collectors.toMap(HealthDto.HealthComponentDto::getId, c -> c));
        assertTrue(byId.containsKey(HealthScoreConstants.ID_VOLATILITY));
        assertEquals(55, byId.get(HealthScoreConstants.ID_VOLATILITY).getScore());
        assertTrue(byId.get(HealthScoreConstants.ID_VOLATILITY).getReason().toLowerCase().contains("insufficient"));
    }

    @Test
    void volS1_measuredReasonIncludesAnnPct() {
        engine.setHealthV2(true);
        PortfolioIntelligenceSnapshot snapshot = PortfolioIntelligenceSnapshot.builder()
                .portfolioId("vol-s1")
                .holdings(List.of(holding("A", 100, 50, "X", "LARGE_CAP", null),
                        holding("B", 100, 50, "Y", "LARGE_CAP", null)))
                .holdingsCount(2)
                .distinctSectors(2)
                .top1Pct(50)
                .maxSectorPct(50)
                .liquidSharePct(100)
                .historyPoints(60)
                .dailyVolPct(1.0)
                .beta(1.0)
                .build();
        HealthDto health = engine.compute(snapshot);
        String reason = health.getComponents().stream()
                .filter(c -> HealthScoreConstants.ID_VOLATILITY.equals(c.getId()))
                .map(HealthDto.HealthComponentDto::getReason)
                .findFirst()
                .orElse("");
        assertTrue(reason.contains("Ann. vol") && reason.contains("%"), reason);
        assertTrue(reason.contains("15.9") || reason.contains("15.8"), reason);
    }

    private static int roundDiv(HealthDto health) {
        return health.getComponents().stream()
                .filter(c -> HealthScoreConstants.ID_DIVERSIFICATION.equals(c.getId()))
                .map(HealthDto.HealthComponentDto::getScore)
                .findFirst()
                .orElseThrow();
    }

    private static String divReason(HealthDto health) {
        return health.getComponents().stream()
                .filter(c -> HealthScoreConstants.ID_DIVERSIFICATION.equals(c.getId()))
                .map(HealthDto.HealthComponentDto::getReason)
                .findFirst()
                .orElse("");
    }

    private static PortfolioIntelligenceSnapshot equalEquitySnapshot(int names, int sectors) {
        List<PortfolioIntelligenceSnapshot.Holding> holdings = new ArrayList<>();
        double w = 100.0 / names;
        for (int i = 0; i < names; i++) {
            String sector = "S" + (i % sectors);
            holdings.add(holding("EQ" + i, 10_000, w, sector, "LARGE_CAP", HealthScoreEngine.ASSET_EQUITY));
        }
        return PortfolioIntelligenceSnapshot.builder()
                .portfolioId("eq")
                .holdings(holdings)
                .totalValue(names * 10_000.0)
                .holdingsCount(names)
                .distinctSectors(sectors)
                .top1Pct(w)
                .maxSectorPct(100.0 * ((names + sectors - 1) / sectors) / names)
                .maxSectorName("S0")
                .liquidSharePct(90)
                .historyPoints(0)
                .build();
    }

    private static String severity(HealthDto health, String id) {
        return health.getComponents().stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst()
                .map(HealthDto.HealthComponentDto::getSeverity)
                .orElse(null);
    }

    private static PortfolioIntelligenceSnapshot.Holding holding(
            String symbol, double value, double weightPct, String sector, String cap, String assetClass) {
        return PortfolioIntelligenceSnapshot.Holding.builder()
                .symbol(symbol)
                .value(value)
                .weightPct(weightPct)
                .sector(sector)
                .industry(sector)
                .marketCap(cap)
                .assetClass(assetClass)
                .build();
    }
}
