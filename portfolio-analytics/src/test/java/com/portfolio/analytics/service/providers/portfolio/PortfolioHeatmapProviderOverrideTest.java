package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.market.TimeFrame;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioHeatmapProviderOverrideTest {

    @Test
    void liveOrMissingTimeFrameUsesTodayOverride() {
        assertTrue(PortfolioHeatmapProvider.shouldApplyTodayChangeOverride(null));
        assertTrue(PortfolioHeatmapProvider.shouldApplyTodayChangeOverride(new AdvancedAnalyticsRequest()));
    }

    @Test
    void dayTimeFrameUsesTodayOverride() {
        AdvancedAnalyticsRequest request = new AdvancedAnalyticsRequest();
        request.setFromDate(LocalDate.now());
        request.setToDate(LocalDate.now());
        request.setTimeFrame(TimeFrame.DAY);
        assertTrue(PortfolioHeatmapProvider.shouldApplyTodayChangeOverride(request));
    }

    @Test
    void weekTimeFrameSkipsTodayOverride() {
        AdvancedAnalyticsRequest request = new AdvancedAnalyticsRequest();
        request.setFromDate(LocalDate.now().minusDays(7));
        request.setToDate(LocalDate.now());
        request.setTimeFrame(TimeFrame.WEEK);
        assertFalse(PortfolioHeatmapProvider.shouldApplyTodayChangeOverride(request));
    }

    @Test
    void mutualFundScopeDropsEquityAndKeepsSchemeIsin() {
        EquityModel tcs = EquityModel.builder()
                .symbol("TCS")
                .isin("INE467B01029")
                .name("Tata Consultancy")
                .assetClass("EQUITY")
                .build();
        EquityModel scheme = EquityModel.builder()
                .symbol("INF204KB17I5")
                .isin("INF204KB17I5")
                .name("Nippon India Large Cap")
                .assetClass("MUTUAL_FUND")
                .build();
        assertFalse(PortfolioHeatmapProvider.matchesAssetScope(tcs, "MUTUAL_FUND"));
        assertTrue(PortfolioHeatmapProvider.matchesAssetScope(scheme, "MUTUAL_FUND"));
        assertTrue(PortfolioHeatmapProvider.matchesAssetScope(tcs, "EQUITY"));
        assertFalse(PortfolioHeatmapProvider.matchesAssetScope(scheme, "EQUITY"));

        PortfolioModelV1 filtered = PortfolioHeatmapProvider.filterPortfolioByAssetScope(
                PortfolioModelV1.builder().equityModels(List.of(tcs, scheme)).build(),
                "MUTUAL_FUND");
        assertEquals(1, filtered.getEquityModels().size());
        assertEquals("INF204KB17I5", filtered.getEquityModels().get(0).getIsin());
    }

    @Test
    void equityScopeExcludesEtfAndEtfScopeKeepsBees() {
        EquityModel tcs = EquityModel.builder()
                .symbol("TCS")
                .isin("INE467B01029")
                .name("Tata Consultancy")
                .assetClass("EQUITY")
                .build();
        EquityModel niftyBees = EquityModel.builder()
                .symbol("NIFTYBEES")
                .isin("INF732E01037")
                .name("Nippon India ETF Nifty BeES")
                .assetClass("EQUITY")
                .build();

        assertTrue(PortfolioHeatmapProvider.matchesAssetScope(tcs, "EQUITY"));
        assertFalse(PortfolioHeatmapProvider.matchesAssetScope(niftyBees, "EQUITY"));
        assertTrue(PortfolioHeatmapProvider.matchesAssetScope(niftyBees, "ETF"));
        assertFalse(PortfolioHeatmapProvider.matchesAssetScope(tcs, "ETF"));
        assertEquals("ETF", PortfolioHeatmapProvider.normalizeAssetScope("ETFS"));

        PortfolioModelV1 etfOnly = PortfolioHeatmapProvider.filterPortfolioByAssetScope(
                PortfolioModelV1.builder().equityModels(List.of(tcs, niftyBees)).build(),
                "ETF");
        assertEquals(1, etfOnly.getEquityModels().size());
        assertEquals("NIFTYBEES", etfOnly.getEquityModels().get(0).getSymbol());
    }
}
