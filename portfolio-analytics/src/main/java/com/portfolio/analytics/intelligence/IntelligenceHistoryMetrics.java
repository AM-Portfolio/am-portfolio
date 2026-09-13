package com.portfolio.analytics.intelligence;

import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.OhlcData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Pure helper: builds aligned portfolio vs NIFTY daily returns and derived history metrics.
 * No Spring; safe for null/partial market series.
 */
public final class IntelligenceHistoryMetrics {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final double MIN_COVERAGE = 0.50;

    private IntelligenceHistoryMetrics() {
    }

    /**
     * Compute history metrics from normalized historical market data and holding quantities.
     *
     * @param historicalBySymbol map keyed by normalized symbol (includes NIFTY)
     * @param quantities         holding quantities by normalized symbol (excludes index)
     * @param niftySymbol        normalized NIFTY key in {@code historicalBySymbol}
     */
    public static Result compute(
            Map<String, MarketData> historicalBySymbol,
            Map<String, Double> quantities,
            String niftySymbol) {

        if (historicalBySymbol == null
                || quantities == null
                || niftySymbol == null
                || niftySymbol.isBlank()) {
            return Result.empty();
        }

        MarketData niftyMd = historicalBySymbol.get(niftySymbol);
        NavigableMap<LocalDate, Double> niftyCloses = extractCloseByDate(niftyMd);
        if (niftyCloses.isEmpty()) {
            return Result.empty();
        }

        List<HoldingSeries> holdings = buildHoldingSeries(historicalBySymbol, quantities, niftySymbol);
        if (holdings.isEmpty()) {
            return Result.empty();
        }

        List<Double> portfolioValues = new ArrayList<>();
        List<Double> niftyValues = new ArrayList<>();

        for (Map.Entry<LocalDate, Double> niftyDay : niftyCloses.entrySet()) {
            LocalDate day = niftyDay.getKey();
            Double niftyClose = niftyDay.getValue();
            if (niftyClose == null || niftyClose <= 0 || !Double.isFinite(niftyClose)) {
                continue;
            }

            double dayValue = 0.0;
            int availableCount = 0;
            double availableWeight = 0.0;
            double totalWeight = 0.0;

            for (HoldingSeries h : holdings) {
                totalWeight += h.refWeight;
                Double close = h.closes.get(day);
                if (close != null && close > 0 && Double.isFinite(close)) {
                    dayValue += h.qty * close;
                    availableCount++;
                    availableWeight += h.refWeight;
                }
            }

            int totalCount = holdings.size();
            double countCoverage = totalCount > 0 ? (double) availableCount / totalCount : 0.0;
            double weightCoverage = totalWeight > 0 ? availableWeight / totalWeight : countCoverage;
            if (countCoverage < MIN_COVERAGE || weightCoverage < MIN_COVERAGE) {
                continue;
            }
            if (dayValue <= 0 || !Double.isFinite(dayValue)) {
                continue;
            }

            portfolioValues.add(dayValue);
            niftyValues.add(niftyClose);
        }

        if (portfolioValues.size() < 2) {
            return Result.empty();
        }

        List<Double> portReturns = dailyReturns(portfolioValues);
        List<Double> niftyReturns = dailyReturns(niftyValues);
        int n = Math.min(portReturns.size(), niftyReturns.size());
        if (n == 0) {
            return Result.empty();
        }
        if (portReturns.size() != n) {
            portReturns = new ArrayList<>(portReturns.subList(0, n));
        }
        if (niftyReturns.size() != n) {
            niftyReturns = new ArrayList<>(niftyReturns.subList(0, n));
        }

        double portRetPct = totalReturnPct(portfolioValues);
        double niftyRetPct = totalReturnPct(niftyValues);
        Double dailyVolPct = sampleStdDev(portReturns);
        if (dailyVolPct != null) {
            dailyVolPct = dailyVolPct * 100.0;
        }
        Double beta = beta(portReturns, niftyReturns);

        return new Result(
                n,
                portRetPct,
                niftyRetPct,
                dailyVolPct,
                beta,
                List.copyOf(portReturns),
                List.copyOf(niftyReturns));
    }

    private static List<HoldingSeries> buildHoldingSeries(
            Map<String, MarketData> historicalBySymbol,
            Map<String, Double> quantities,
            String niftySymbol) {

        List<HoldingSeries> holdings = new ArrayList<>();
        for (Map.Entry<String, Double> entry : quantities.entrySet()) {
            String symbol = entry.getKey();
            Double qty = entry.getValue();
            if (symbol == null || symbol.isBlank() || Objects.equals(symbol, niftySymbol)) {
                continue;
            }
            if (qty == null || !Double.isFinite(qty) || qty == 0.0) {
                continue;
            }
            NavigableMap<LocalDate, Double> closes = extractCloseByDate(historicalBySymbol.get(symbol));
            if (closes.isEmpty()) {
                continue;
            }
            Double refClose = closes.lastEntry().getValue();
            if (refClose == null || refClose <= 0 || !Double.isFinite(refClose)) {
                continue;
            }
            double refWeight = Math.abs(qty) * refClose;
            holdings.add(new HoldingSeries(qty, refWeight, closes));
        }
        return holdings;
    }

    private static NavigableMap<LocalDate, Double> extractCloseByDate(MarketData md) {
        if (md == null || md.getDataPoints() == null || md.getDataPoints().isEmpty()) {
            return Collections.emptyNavigableMap();
        }
        NavigableMap<LocalDate, Double> byDate = new TreeMap<>();
        for (MarketData.MarketDataPoint point : md.getDataPoints()) {
            if (point == null || point.getTimestamp() == null) {
                continue;
            }
            OhlcData ohlc = point.getOhlcData();
            if (ohlc == null) {
                continue;
            }
            double close = ohlc.getClose();
            if (!Double.isFinite(close) || close <= 0) {
                continue;
            }
            LocalDate day = toMarketDate(point.getTimestamp());
            byDate.put(day, close);
        }
        return byDate;
    }

    private static LocalDate toMarketDate(Instant timestamp) {
        return timestamp.atZone(MARKET_ZONE).toLocalDate();
    }

    private static List<Double> dailyReturns(List<Double> values) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            double prev = values.get(i - 1);
            double curr = values.get(i);
            if (prev <= 0 || !Double.isFinite(prev) || !Double.isFinite(curr)) {
                continue;
            }
            returns.add((curr - prev) / prev);
        }
        return returns;
    }

    private static double totalReturnPct(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }
        double first = values.get(0);
        double last = values.get(values.size() - 1);
        if (first <= 0 || !Double.isFinite(first) || !Double.isFinite(last)) {
            return 0.0;
        }
        return ((last - first) / first) * 100.0;
    }

    /** Sample standard deviation (n−1). Null if fewer than 2 observations. */
    static Double sampleStdDev(List<Double> values) {
        if (values == null || values.size() < 2) {
            return null;
        }
        double mean = 0.0;
        for (double v : values) {
            mean += v;
        }
        mean /= values.size();
        double sumSq = 0.0;
        for (double v : values) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (values.size() - 1));
    }

    /** Sample beta = cov(r_p, r_m) / var(r_m). Null if undefined. */
    static Double beta(List<Double> portReturns, List<Double> marketReturns) {
        if (portReturns == null || marketReturns == null) {
            return null;
        }
        int n = Math.min(portReturns.size(), marketReturns.size());
        if (n < 2) {
            return null;
        }
        double meanP = 0.0;
        double meanM = 0.0;
        for (int i = 0; i < n; i++) {
            meanP += portReturns.get(i);
            meanM += marketReturns.get(i);
        }
        meanP /= n;
        meanM /= n;

        double cov = 0.0;
        double varM = 0.0;
        for (int i = 0; i < n; i++) {
            double dp = portReturns.get(i) - meanP;
            double dm = marketReturns.get(i) - meanM;
            cov += dp * dm;
            varM += dm * dm;
        }
        cov /= (n - 1);
        varM /= (n - 1);
        if (varM <= 0 || !Double.isFinite(varM) || !Double.isFinite(cov)) {
            return null;
        }
        return cov / varM;
    }

    private record HoldingSeries(double qty, double refWeight, NavigableMap<LocalDate, Double> closes) {
    }

    /**
     * History window metrics. {@code historyPoints} is the number of daily returns.
     * Callers typically omit vol/beta/perf when {@code historyPoints < 20}.
     */
    public record Result(
            int historyPoints,
            Double portRetPct,
            Double niftyRetPct,
            Double dailyVolPct,
            Double beta,
            List<Double> portfolioDailyReturns,
            List<Double> niftyDailyReturns) {

        public Result {
            portfolioDailyReturns = portfolioDailyReturns == null
                    ? List.of()
                    : List.copyOf(portfolioDailyReturns);
            niftyDailyReturns = niftyDailyReturns == null
                    ? List.of()
                    : List.copyOf(niftyDailyReturns);
        }

        public static Result empty() {
            return new Result(0, null, null, null, null, List.of(), List.of());
        }
    }
}
