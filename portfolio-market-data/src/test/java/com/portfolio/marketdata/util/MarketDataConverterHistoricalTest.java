package com.portfolio.marketdata.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.am.common.investment.model.historical.OHLCVTPoint;
import com.portfolio.marketdata.model.HistoricalData;
import com.portfolio.marketdata.model.HistoricalDataResponse;
import com.portfolio.model.market.MarketData;

class MarketDataConverterHistoricalTest {

    @Test
    void fromHistorical_twoPoints_setsPreviousCloseFromFirstOpen() {
        HistoricalDataResponse response = responseWithPoints(
                point(100, 110),
                point(120, 130));
        MarketData md = MarketDataConverter.fromHistoricalDataResponse(response);
        assertNotNull(md);
        assertEquals(130.0, md.getLastPrice());
        assertEquals(100.0, md.getPreviousClose());
    }

    @Test
    void fromHistorical_singlePoint_stillSetsPreviousCloseFromOpen() {
        HistoricalDataResponse response = responseWithPoints(point(90, 95));
        MarketData md = MarketDataConverter.fromHistoricalDataResponse(response);
        assertNotNull(md);
        assertEquals(95.0, md.getLastPrice());
        assertEquals(90.0, md.getPreviousClose());
    }

    @Test
    void fromHistorical_empty_returnsNull() {
        HistoricalDataResponse response = HistoricalDataResponse.builder()
                .symbol("X")
                .interval("1D")
                .data(HistoricalData.builder().dataPoints(List.of()).build())
                .build();
        assertNull(MarketDataConverter.fromHistoricalDataResponse(response));
    }

    @Test
    void fromHistorical_flatMarketShape_converts() {
        OHLCVTPoint first = point(100, 110, LocalDateTime.of(2026, 9, 1, 15, 30));
        OHLCVTPoint last = point(120, 130, LocalDateTime.of(2026, 9, 8, 15, 30));
        HistoricalDataResponse response = HistoricalDataResponse.builder()
                .tradingSymbol("RELIANCE")
                .interval("1D")
                .dataPoints(List.of(first, last))
                .dataPointCount(2)
                .build();
        MarketData md = MarketDataConverter.fromHistoricalDataResponse(response);
        assertNotNull(md);
        assertEquals("RELIANCE", md.getSymbol());
        assertEquals(130.0, md.getLastPrice());
        assertEquals(100.0, md.getPreviousClose());
    }

    @Test
    void fromHistorical_unsortedPoints_usesChronologicalFirstOpen() {
        OHLCVTPoint later = point(120, 130, LocalDateTime.of(2026, 9, 8, 15, 30));
        OHLCVTPoint earlier = point(100, 110, LocalDateTime.of(2026, 9, 1, 15, 30));
        HistoricalDataResponse response = HistoricalDataResponse.builder()
                .tradingSymbol("RELIANCE")
                .interval("1D")
                .dataPoints(List.of(later, earlier))
                .dataPointCount(2)
                .build();
        MarketData md = MarketDataConverter.fromHistoricalDataResponse(response);
        assertNotNull(md);
        assertEquals(130.0, md.getLastPrice());
        assertEquals(100.0, md.getPreviousClose());
    }

    private static HistoricalDataResponse responseWithPoints(OHLCVTPoint... points) {
        return HistoricalDataResponse.builder()
                .symbol("RELIANCE")
                .interval("1D")
                .data(HistoricalData.builder().dataPoints(List.of(points)).build())
                .build();
    }

    private static OHLCVTPoint point(double open, double close) {
        return point(open, close, LocalDateTime.of(2026, 9, 1, 15, 30));
    }

    private static OHLCVTPoint point(double open, double close, LocalDateTime time) {
        OHLCVTPoint p = new OHLCVTPoint();
        p.setTime(time);
        p.setOpen(open);
        p.setHigh(Math.max(open, close));
        p.setLow(Math.min(open, close));
        p.setClose(close);
        p.setVolume(1000L);
        return p;
    }
}
