package com.portfolio.marketdata.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.TimeFrame;

class HistoricalDataRequestFactoryTest {

    @Test
    void forPeriodEndpoints_alwaysUsesDailyCandleInterval() {
        LocalDate to = LocalDate.of(2026, 9, 14);
        for (TimeFrame tf : List.of(TimeFrame.WEEK, TimeFrame.MONTH, TimeFrame.YEAR)) {
            TimeFrameRequest tfr = TimeFrameRequest.builder()
                    .fromDate(to.minusDays(30))
                    .toDate(to)
                    .timeFrame(tf)
                    .build();
            HistoricalDataRequest req = HistoricalDataRequestFactory.forPeriodEndpoints(List.of("RELIANCE", "TCS"), tfr);
            assertEquals("1D", req.getInterval(), "analysis TF " + tf + " must use daily candle interval");
            assertEquals("START_END", req.getFilterType());
            assertTrue(req.getSymbols().contains("RELIANCE"));
        }
    }

    @Test
    void resolveWindow_fillsMissingDatesFromTimeFrame() {
        TimeFrameRequest tfr = TimeFrameRequest.builder().timeFrame(TimeFrame.WEEK).build();
        TimeFrameRequest resolved = HistoricalDataRequestFactory.resolveWindow(tfr);
        assertNotNull(resolved.getFromDate());
        assertNotNull(resolved.getToDate());
        assertEquals(TimeFrame.WEEK, resolved.getTimeFrame());
    }

    @Test
    void isSameDayLiveWindow_detectsTodayRange() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        TimeFrameRequest tfr = TimeFrameRequest.builder()
                .fromDate(today)
                .toDate(today)
                .timeFrame(TimeFrame.DAY)
                .build();
        assertTrue(HistoricalDataRequestFactory.isSameDayLiveWindow(tfr));

        TimeFrameRequest week = TimeFrameRequest.builder()
                .fromDate(today.minusWeeks(1))
                .toDate(today)
                .timeFrame(TimeFrame.WEEK)
                .build();
        assertFalse(HistoricalDataRequestFactory.isSameDayLiveWindow(week));
    }
}
