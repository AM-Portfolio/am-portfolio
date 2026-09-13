package com.portfolio.analytics.intelligence;

import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic series proving portfolio returns, sample vol, and beta.
 */
class IntelligenceHistoryMetricsTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String NIFTY = "NIFTY 50";
    private static final String AAA = "AAA";

    @Test
    void compute_syntheticSeries_returnsVolAndBeta() {
        // Portfolio: 1 share AAA. Closes chosen so daily returns are exactly [0.02, 0.00, 0.04]
        // NIFTY closes chosen so daily returns are exactly [0.01, 0.00, 0.02] → beta = 2.0
        LocalDate d0 = LocalDate.of(2024, 1, 2);
        LocalDate d1 = LocalDate.of(2024, 1, 3);
        LocalDate d2 = LocalDate.of(2024, 1, 4);
        LocalDate d3 = LocalDate.of(2024, 1, 5);

        Map<String, MarketData> historical = new HashMap<>();
        historical.put(AAA, series(AAA, List.of(
                point(d0, 100.0),
                point(d1, 102.0),
                point(d2, 102.0),
                point(d3, 106.08))));
        historical.put(NIFTY, series(NIFTY, List.of(
                point(d0, 1000.0),
                point(d1, 1010.0),
                point(d2, 1010.0),
                point(d3, 1030.2))));

        Map<String, Double> quantities = Map.of(AAA, 1.0);

        IntelligenceHistoryMetrics.Result result =
                IntelligenceHistoryMetrics.compute(historical, quantities, NIFTY);

        assertEquals(3, result.historyPoints());
        assertReturnsClose(List.of(0.02, 0.00, 0.04), result.portfolioDailyReturns());
        assertReturnsClose(List.of(0.01, 0.00, 0.02), result.niftyDailyReturns());

        // total return over window * 100
        assertEquals(6.08, result.portRetPct(), 1e-9);
        assertEquals(3.02, result.niftyRetPct(), 1e-9);

        // sample stddev of [0.02, 0, 0.04] = 0.02 → dailyVolPct = 2.0
        assertNotNull(result.dailyVolPct());
        assertEquals(2.0, result.dailyVolPct(), 1e-9);

        // cov/var → beta = 2.0
        assertNotNull(result.beta());
        assertEquals(2.0, result.beta(), 1e-9);
    }

    private static void assertReturnsClose(List<Double> expected, List<Double> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), 1e-12, "return[" + i + "]");
        }
    }

    @Test
    void sampleStdDev_usesNMinus1() {
        // sample stddev of [1, 3] = sqrt(((1-2)^2+(3-2)^2)/1) = 1.414...
        Double s = IntelligenceHistoryMetrics.sampleStdDev(List.of(1.0, 3.0));
        assertNotNull(s);
        assertEquals(Math.sqrt(2.0), s, 1e-12);
    }

    @Test
    void compute_nullSafe_emptyWhenMissingNifty() {
        IntelligenceHistoryMetrics.Result result =
                IntelligenceHistoryMetrics.compute(Map.of(), Map.of(AAA, 1.0), NIFTY);
        assertEquals(0, result.historyPoints());
        assertTrue(result.portfolioDailyReturns().isEmpty());
    }

    private static MarketData series(String symbol, List<MarketData.MarketDataPoint> points) {
        return MarketData.builder()
                .symbol(symbol)
                .historical(true)
                .dataPoints(new ArrayList<>(points))
                .build();
    }

    private static MarketData.MarketDataPoint point(LocalDate day, double close) {
        Instant ts = day.atStartOfDay(IST).toInstant();
        return MarketData.MarketDataPoint.builder()
                .timestamp(ts)
                .ohlcData(OhlcData.builder().open(close).high(close).low(close).close(close).build())
                .build();
    }
}
