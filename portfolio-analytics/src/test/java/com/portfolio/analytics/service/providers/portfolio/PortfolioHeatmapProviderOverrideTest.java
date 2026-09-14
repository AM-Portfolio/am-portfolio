package com.portfolio.analytics.service.providers.portfolio;

import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.market.TimeFrame;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
}
