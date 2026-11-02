package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.StressRequest;
import com.portfolio.model.analytics.intelligence.StressResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StressEngineTest {

    private static StressProperties buildTestProperties() {
        StressProperties props = new StressProperties();
        props.setRawIndices(List.of("^NSEI", "^BSESN", "NIFTY 50", "SENSEX"));
        props.setBroadNiftyEtfs(List.of("NIFTYBEES", "SETFNIF50"));
        Map<String, StressProperties.ScenarioConfig> map = new LinkedHashMap<>();
        map.put("NIFTY_DOWN_10", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.UNIFORM).shockPct(-10.0).description("Nifty Down 10%").build());
        map.put("SENSEX_DOWN_10", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.UNIFORM).shockPct(-10.0).description("Sensex Down 10%").build());
        map.put("GFC_2008", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.UNIFORM).shockPct(-56.0).description("2008 Global Financial Crisis").build());
        map.put("COVID_2020", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.UNIFORM).shockPct(-38.0).description("2020 COVID Crash").build());
        map.put("CHINA_2015", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.UNIFORM).shockPct(-28.0).description("2015 China Devaluation").build());
        map.put("INFLATION_2022", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.UNIFORM).shockPct(-15.0).description("2022 Global Inflation Spike").build());
        map.put("ELECTION_2024", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.UNIFORM).shockPct(-18.0).description("2024 Election Day Volatility").build());
        map.put("BANKING_DOWN_20", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.SECTOR).shockPct(-20.0).targetSector("Banking").description("Banking Shock -20%").build());
        map.put("IT_DOWN_15", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.SECTOR).shockPct(-15.0).targetSector("IT").description("IT Shock -15%").build());
        map.put("AUTO_DOWN_20", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.SECTOR).shockPct(-20.0).targetSector("Auto").description("Auto Shock -20%").build());
        map.put("PHARMA_DOWN_15", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.SECTOR).shockPct(-15.0).targetSector("Pharma").description("Pharma Shock -15%").build());
        map.put("ENERGY_DOWN_20", StressProperties.ScenarioConfig.builder().type(StressProperties.ScenarioType.SECTOR).shockPct(-20.0).targetSector("Energy").description("Energy Shock -20%").build());
        props.setScenarios(map);
        return props;
    }

    private final StressEngine engine = new StressEngine(buildTestProperties());

    @Test
    void niftyDown10_withBetaHalf_impactIsMinusFive() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                0.5,
                holding("A", 100, 60, "Energy"),
                holding("B", 100, 40, "Pharma"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(-5.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(-10.0, res.getScenarios().get(0).getAppliedShockPct(), 0.01);
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
    void gfc2008_withBetaHalf_isMinusTwentyEight() {
        PortfolioIntelligenceSnapshot snap = snapshot(0.5, holding("A", 100, 100, "Energy"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("GFC_2008").build());
        assertEquals(-28.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(-56.0, res.getScenarios().get(0).getAppliedShockPct(), 0.01);
    }

    @Test
    void covid2020_withBetaOne_isMinusThirtyEight() {
        PortfolioIntelligenceSnapshot snap = snapshot(1.0, holding("A", 100, 100, "Energy"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("COVID_2020").build());
        assertEquals(-38.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(-38.0, res.getScenarios().get(0).getAppliedShockPct(), 0.01);
    }

    @Test
    void gfcVsNiftyDown10_ratioIsFivePointSix() {
        PortfolioIntelligenceSnapshot snap = snapshot(0.5, holding("A", 100, 100, "Energy"));
        double nifty = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build())
                .getScenarios().get(0).getPctImpact();
        double gfc = engine.run(snap, StressRequest.builder().preset("GFC_2008").build())
                .getScenarios().get(0).getPctImpact();
        assertEquals(nifty * 5.6, gfc, 0.05);
    }

    @Test
    void eventBatch_returnsAllSevenViewAllIds() {
        PortfolioIntelligenceSnapshot snap = snapshot(1.0, holding("A", 100, 100, "Energy"));
        StressResponse res = engine.run(snap, StressRequest.builder()
                .presets(List.of(
                        "NIFTY_DOWN_10",
                        "SENSEX_DOWN_10",
                        "GFC_2008",
                        "COVID_2020",
                        "CHINA_2015",
                        "INFLATION_2022",
                        "ELECTION_2024"))
                .build());
        assertEquals(7, res.getScenarios().size());
        assertEquals(-28.0, res.getScenarios().get(4).getPctImpact(), 0.01);
        assertEquals(-18.0, res.getScenarios().get(6).getAppliedShockPct(), 0.01);
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
    void customIt_matchesItServicesSectorLabel() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("TCS", 100, 40, "IT Services"),
                holding("RELIANCE", 100, 60, "Energy"));
        StressRequest.CustomShock custom = StressRequest.CustomShock.builder()
                .sector("IT")
                .shockPct(-15.0)
                .build();
        StressResponse res = engine.run(snap, StressRequest.builder().custom(custom).build());
        assertEquals(-6.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(40.0, res.getScenarios().get(0).getMatchedWeightPct(), 0.01);
        assertEquals(1, res.getScenarios().get(0).getMatchedHoldings());
    }

    @Test
    void customIt_usesIndustryWhenSectorUnknown() {
        PortfolioIntelligenceSnapshot.Holding tcs = PortfolioIntelligenceSnapshot.Holding.builder()
                .symbol("INFY")
                .value(100)
                .weightPct(50)
                .sector("Unknown")
                .industry("IT Services")
                .marketCap("LARGE_CAP")
                .assetClass("EQUITY")
                .build();
        PortfolioIntelligenceSnapshot snap = snapshot(1.0, tcs, holding("RELIANCE", 100, 50, "Energy"));
        StressRequest.CustomShock custom = StressRequest.CustomShock.builder()
                .sector("IT")
                .shockPct(-15.0)
                .build();
        StressResponse res = engine.run(snap, StressRequest.builder().custom(custom).build());
        assertEquals(-7.5, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(1, res.getScenarios().get(0).getMatchedHoldings());
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
    void niftyDown10_cashWeight_doesNotTakeIndexShock() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.2,
                holding("RELIANCE", 70, 70, "Energy", "EQUITY"),
                holding("CASH", 30, 30, null, "CASH"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(-8.4, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(70.0, res.getEquityWeightPct(), 0.01);
    }

    @Test
    void bankingShock_skipsBroadNiftyEtf() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("NIFTYBEES", 100, 40, "Financial Services"),
                holding("HDFCBANK", 100, 60, "Financial Services"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("BANKING_DOWN_20").build());
        assertEquals(-12.0, res.getScenarios().get(0).getPctImpact(), 0.01);
    }

    @Test
    void niftyDown10_niftyBees_isInEquitySleeve() {
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("NIFTYBEES", 100, 100, "Financial Services"));
        StressResponse res = engine.run(snap, StressRequest.builder().preset("NIFTY_DOWN_10").build());
        assertEquals(-10.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(100.0, res.getEquityWeightPct(), 0.01);
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

    @Test
    void customProperties_registersRawIndicesAndBroadEtfs() {
        StressProperties props = new StressProperties();
        props.setRawIndices(List.of("MY_CUSTOM_INDEX"));
        props.setBroadNiftyEtfs(List.of("MY_CUSTOM_ETF"));
        new StressEngine(props);

        assertTrue(IndexEtfSleeves.isRawIndexAlias("MY_CUSTOM_INDEX"));
        assertTrue(IndexEtfSleeves.isBroadNiftyEtf("MY_CUSTOM_ETF"));
    }

    @Test
    void customSectorScenario_configuredViaProperties_isAppliedCorrectly() {
        StressProperties props = new StressProperties();
        props.setScenarios(java.util.Map.of(
                "DEFENSE_DOWN_15",
                StressProperties.ScenarioConfig.builder()
                        .type(StressProperties.ScenarioType.SECTOR)
                        .targetSector("Defense")
                        .shockPct(-15.0)
                        .description("Defense -15%")
                        .build()));

        StressEngine customEngine = new StressEngine(props);
        PortfolioIntelligenceSnapshot snap = snapshot(
                1.0,
                holding("HAL", 100, 40, "Defense"),
                holding("RELIANCE", 100, 60, "Energy"));

        StressResponse res = customEngine.run(snap, StressRequest.builder().preset("DEFENSE_DOWN_15").build());
        assertEquals(1, res.getScenarios().size());
        assertEquals(-6.0, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(-15.0, res.getScenarios().get(0).getAppliedShockPct(), 0.01);
    }

    @Test
    void customUniformScenario_configuredViaProperties_isAppliedCorrectly() {
        StressProperties props = new StressProperties();
        props.setScenarios(java.util.Map.of(
                "GEOPOLITICAL_DOWN_25",
                StressProperties.ScenarioConfig.builder()
                        .type(StressProperties.ScenarioType.UNIFORM)
                        .shockPct(-25.0)
                        .description("Uniform -25% * betaProxy")
                        .build()));

        StressEngine customEngine = new StressEngine(props);
        PortfolioIntelligenceSnapshot snap = snapshot(
                0.5,
                holding("A", 100, 100, "Energy"));

        StressResponse res = customEngine.run(snap, StressRequest.builder().preset("GEOPOLITICAL_DOWN_25").build());
        assertEquals(1, res.getScenarios().size());
        assertEquals(-12.5, res.getScenarios().get(0).getPctImpact(), 0.01);
        assertEquals(-25.0, res.getScenarios().get(0).getAppliedShockPct(), 0.01);
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
        return holding(symbol, value, weightPct, sector, null);
    }

    private static PortfolioIntelligenceSnapshot.Holding holding(
            String symbol, double value, double weightPct, String sector, String assetClass) {
        return PortfolioIntelligenceSnapshot.Holding.builder()
                .symbol(symbol)
                .value(value)
                .weightPct(weightPct)
                .sector(sector)
                .industry(sector)
                .marketCap("LARGE_CAP")
                .assetClass(assetClass)
                .build();
    }
}
