package com.portfolio.basket.service;

import com.portfolio.basket.model.EtfData;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.TimeFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fills missing Discover return1Y/3Y/5Y and sparklineCloses from market hist.
 * Prefers existing parser values; never invents 0 when series is too short.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EtfPerformanceEnricher {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int SPARKLINE_MAX_POINTS = 24;
    private static final DateTimeFormatter AS_OF = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MarketDataService marketDataService;

    static boolean needsPerformanceFill(EtfData data) {
        if (data == null) {
            return false;
        }
        // Always recompute from hist once per cache generation so Discover CAGR matches labels
        // and sparklines are present. Cached complete payloads skip via L1/L2 hit without fill.
        boolean missingReturns = data.getReturn1Y() == null
                || data.getReturn3Y() == null
                || data.getReturn5Y() == null;
        boolean missingSpark = data.getSparklineCloses() == null || data.getSparklineCloses().size() < 2;
        boolean looksLikeUnnormalizedParser =
                hasExcessPrecision(data.getReturn1Y())
                        || hasExcessPrecision(data.getReturn3Y())
                        || hasExcessPrecision(data.getReturn5Y());
        return missingReturns || missingSpark || looksLikeUnnormalizedParser;
    }

    /** Parser totals often arrive with >2 dp; Discover stores hist CAGR at 2 dp. */
    static boolean hasExcessPrecision(Double value) {
        if (value == null) {
            return false;
        }
        double scaled = value * 100.0;
        return Math.abs(scaled - Math.round(scaled)) > 0.001;
    }

    /**
     * Mutates ETF payloads in place for symbols that still need performance fields.
     *
     * @return count of ETFs that received at least one filled field
     */
    public int fillMissing(List<EtfData> etfs) {
        if (etfs == null || etfs.isEmpty()) {
            return 0;
        }
        List<EtfData> needing = etfs.stream()
                .filter(EtfPerformanceEnricher::needsPerformanceFill)
                .filter(e -> e.getSymbol() != null && !e.getSymbol().isBlank())
                .collect(Collectors.toList());
        if (needing.isEmpty()) {
            return 0;
        }

        List<String> symbols = needing.stream()
                .map(EtfData::getSymbol)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());

        long startMs = System.currentTimeMillis();
        Map<String, MarketData> hist = fetchFiveYearWeekly(symbols);
        int filled = 0;
        for (EtfData etf : needing) {
            String key = etf.getSymbol().trim().toUpperCase(Locale.ROOT);
            MarketData md = resolveHist(hist, key);
            if (md != null && applyFromSeries(etf, md)) {
                filled++;
            }
        }
        int longFill = fillMissingLongHorizons(needing);
        filled += longFill;
        log.info("basket.perf.enrich symbols={} histHits={} filled={} longFill={} durationMs={}",
                symbols.size(), hist.size(), filled, longFill, System.currentTimeMillis() - startMs);
        return filled;
    }

    /**
     * START_END daily endpoints for 3Y/5Y when weekly series is too short (e.g. METALIETF).
     */
    private int fillMissingLongHorizons(List<EtfData> etfs) {
        int filled = 0;
        filled += applyStartEndReturns(etfs.stream()
                .filter(e -> e.getReturn3Y() == null)
                .collect(Collectors.toList()), 3, true);
        filled += applyStartEndReturns(etfs.stream()
                .filter(e -> e.getReturn5Y() == null)
                .collect(Collectors.toList()), 5, true);
        // Young listings: if 3Y exists but 5Y still null, reuse 3Y START_END span as inception CAGR.
        filled += applyStartEndReturns(etfs.stream()
                .filter(e -> e.getReturn5Y() == null && e.getReturn3Y() != null)
                .collect(Collectors.toList()), 3, true, true);
        return filled;
    }

    private int applyStartEndReturns(List<EtfData> etfs, int years, boolean cagr) {
        return applyStartEndReturns(etfs, years, cagr, false);
    }

    private int applyStartEndReturns(List<EtfData> etfs, int years, boolean cagr, boolean asFiveYearProxy) {
        if (etfs == null || etfs.isEmpty()) {
            return 0;
        }
        List<String> symbols = etfs.stream()
                .map(EtfData::getSymbol)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
        Map<String, MarketData> hist = fetchStartEnd(symbols, years);
        int filled = 0;
        for (EtfData etf : etfs) {
            String key = etf.getSymbol().trim().toUpperCase(Locale.ROOT);
            MarketData md = resolveHist(hist, key);
            if (md == null) {
                continue;
            }
            List<ClosePoint> points = extractCloses(md);
            if (points.size() < 2) {
                continue;
            }
            points.sort(Comparator.comparing(ClosePoint::instant));
            ClosePoint last = points.get(points.size() - 1);
            Double r = periodReturnPct(points, last, years, cagr);
            if (r == null) {
                continue;
            }
            if (asFiveYearProxy || years == 5) {
                etf.setReturn5Y(r);
            } else if (years == 3) {
                etf.setReturn3Y(r);
            }
            filled++;
        }
        return filled;
    }

    private Map<String, MarketData> fetchStartEnd(List<String> symbols, int years) {
        LocalDate to = LocalDate.now(MARKET_ZONE);
        LocalDate from = to.minusYears(years).minusDays(14);
        HistoricalDataRequest request = HistoricalDataRequest.builder()
                .symbols(String.join(",", symbols))
                .fromDate(from.toString())
                .toDate(to.toString())
                .interval(TimeFrame.DAY.getValue())
                .filterType(FilterType.START_END.getValue())
                .instrumentType(InstrumentType.EQ.getValue())
                .continuous(false)
                .build();
        try {
            Map<String, MarketData> result = marketDataService.getHistoricalData(request);
            return result != null ? result : Map.of();
        } catch (Exception e) {
            log.warn("basket.perf.enrich start_end {}Y failed: {}", years, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, MarketData> fetchFiveYearWeekly(List<String> symbols) {
        LocalDate to = LocalDate.now(MARKET_ZONE);
        LocalDate from = to.minusYears(5).minusWeeks(2);
        HistoricalDataRequest request = HistoricalDataRequest.builder()
                .symbols(String.join(",", symbols))
                .fromDate(from.toString())
                .toDate(to.toString())
                .interval(TimeFrame.WEEK.getValue())
                .filterType(FilterType.ALL.getValue())
                .instrumentType(InstrumentType.EQ.getValue())
                .continuous(false)
                .build();
        try {
            Map<String, MarketData> result = marketDataService.getHistoricalData(request);
            return result != null ? result : Map.of();
        } catch (Exception e) {
            log.warn("basket.perf.enrich hist failed symbols={}: {}", symbols.size(), e.getMessage());
            return Map.of();
        }
    }

    private static MarketData resolveHist(Map<String, MarketData> hist, String symbol) {
        if (hist == null || hist.isEmpty()) {
            return null;
        }
        MarketData direct = hist.get(symbol);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, MarketData> e : hist.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(symbol)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * @return true if any field was written
     */
    static boolean applyFromSeries(EtfData etf, MarketData md) {
        List<ClosePoint> points = extractCloses(md);
        if (points.size() < 2) {
            return false;
        }
        points.sort(Comparator.comparing(ClosePoint::instant));
        ClosePoint last = points.get(points.size() - 1);
        boolean changed = false;

        // Always replace parser totals with hist-derived values (null clears stale high-precision junk).
        Double r1 = periodReturnPct(points, last, 1, false);
        Double r3 = periodReturnPct(points, last, 3, true);
        Double r5 = periodReturnPct(points, last, 5, true);
        if (!Objects.equals(etf.getReturn1Y(), r1)) {
            etf.setReturn1Y(r1);
            changed = true;
        }
        if (!Objects.equals(etf.getReturn3Y(), r3)) {
            etf.setReturn3Y(r3);
            changed = true;
        }
        if (!Objects.equals(etf.getReturn5Y(), r5)) {
            etf.setReturn5Y(r5);
            changed = true;
        }
        List<Double> spark = downsample(points, SPARKLINE_MAX_POINTS);
        if (spark.size() >= 2) {
            etf.setSparklineCloses(spark);
            changed = true;
        }
        etf.setReturnsAsOf(AS_OF.format(LocalDate.ofInstant(last.instant(), MARKET_ZONE)));
        changed = true;
        return changed;
    }

    /**
     * 1Y = total return; 3Y/5Y = CAGR (matches Discover column labels).
     * If the listing is younger than the window, use the earliest bar and CAGR over the actual span
     * when coverage is at least 70% of the requested years.
     */
    static Double periodReturnPct(List<ClosePoint> points, ClosePoint last, int years, boolean cagr) {
        LocalDate lastDay = LocalDate.ofInstant(last.instant(), MARKET_ZONE);
        LocalDate targetDay = lastDay.minusYears(years);
        ClosePoint start = nearestOnOrBefore(points, targetDay, 120);
        double yearBasis = years;
        if (start == null) {
            start = points.get(0);
            LocalDate startDay = LocalDate.ofInstant(start.instant(), MARKET_ZONE);
            double actualYears = ChronoUnit.DAYS.between(startDay, lastDay) / 365.25;
            if (actualYears < 2.0) {
                return null;
            }
            yearBasis = actualYears;
        }
        if (start.close() <= 0 || last.close() <= 0) {
            return null;
        }
        double ratio = last.close() / start.close();
        if (!(ratio > 0) || Double.isInfinite(ratio) || Double.isNaN(ratio)) {
            return null;
        }
        double pct;
        if (cagr && yearBasis > 1.0) {
            pct = (Math.pow(ratio, 1.0 / yearBasis) - 1.0) * 100.0;
        } else {
            pct = (ratio - 1.0) * 100.0;
        }
        if (Double.isInfinite(pct) || Double.isNaN(pct)) {
            return null;
        }
        // Reject absurd hist glitches (bad ticks / corporate-action holes).
        if (Math.abs(pct) > 150.0) {
            return null;
        }
        return Math.round(pct * 100.0) / 100.0;
    }

    /**
     * Last bar on/before targetDay, requiring it within {@code maxDaySlop} calendar days.
     */
    static ClosePoint nearestOnOrBefore(List<ClosePoint> points, LocalDate targetDay, int maxDaySlop) {
        ClosePoint best = null;
        for (ClosePoint p : points) {
            LocalDate d = LocalDate.ofInstant(p.instant(), MARKET_ZONE);
            if (!d.isAfter(targetDay)) {
                best = p;
            }
        }
        if (best == null) {
            return null;
        }
        long daysBefore = ChronoUnit.DAYS.between(
                LocalDate.ofInstant(best.instant(), MARKET_ZONE), targetDay);
        return daysBefore <= maxDaySlop ? best : null;
    }

    static ClosePoint closestAtOrBefore(List<ClosePoint> points, Instant target) {
        return nearestOnOrBefore(points, LocalDate.ofInstant(target, MARKET_ZONE), 120);
    }

    static List<Double> downsample(List<ClosePoint> points, int max) {
        if (points.size() <= max) {
            return points.stream().map(ClosePoint::close).collect(Collectors.toCollection(ArrayList::new));
        }
        List<Double> out = new ArrayList<>(max);
        int lastIdx = points.size() - 1;
        for (int i = 0; i < max; i++) {
            int idx = (int) Math.round((double) i * lastIdx / (max - 1));
            out.add(points.get(idx).close());
        }
        return out;
    }

    static List<ClosePoint> extractCloses(MarketData md) {
        List<ClosePoint> points = new ArrayList<>();
        if (md.getDataPoints() != null) {
            for (MarketData.MarketDataPoint dp : md.getDataPoints()) {
                if (dp == null || dp.getTimestamp() == null || dp.getOhlcData() == null) {
                    continue;
                }
                double close = dp.getOhlcData().getClose();
                if (close > 0) {
                    points.add(new ClosePoint(dp.getTimestamp(), close));
                }
            }
        }
        if (points.size() < 2
                && md.getFromDate() != null
                && md.getToDate() != null
                && md.getPreviousClose() != null
                && md.getPreviousClose() > 0
                && md.getLastPrice() != null
                && md.getLastPrice() > 0) {
            points.add(new ClosePoint(
                    md.getFromDate().atStartOfDay(MARKET_ZONE).toInstant(), md.getPreviousClose()));
            points.add(new ClosePoint(
                    md.getToDate().atStartOfDay(MARKET_ZONE).toInstant(), md.getLastPrice()));
        }
        if (points.size() < 2 && md.getOhlc() != null && md.getOhlc().getClose() > 0 && md.getPreviousClose() != null
                && md.getPreviousClose() > 0 && md.getTimestamp() != null) {
            // START_END-style payloads sometimes only expose endpoints on the parent.
            Instant startTs = md.getFromDate() != null
                    ? md.getFromDate().atStartOfDay(MARKET_ZONE).toInstant()
                    : md.getTimestamp().minusSeconds(86400L * 365);
            points.add(new ClosePoint(startTs, md.getPreviousClose()));
            points.add(new ClosePoint(md.getTimestamp(), md.getOhlc().getClose()));
        }
        return points;
    }

    record ClosePoint(Instant instant, double close) {
        ClosePoint {
            Objects.requireNonNull(instant, "instant");
        }
    }
}
