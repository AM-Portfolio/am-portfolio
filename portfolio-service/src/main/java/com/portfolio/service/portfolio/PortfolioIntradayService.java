package com.portfolio.service.portfolio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.TimeFrame;
import com.portfolio.redis.service.PortfolioIntradayRedisService;
import com.portfolio.redis.session.CashSessionClock;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.IntradayDataPoint;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.PortfolioIntradayEntry;
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PortfolioIntradayService {

    private final PortfolioSnapshotService snapshotService;
    private final PortfolioHoldingsService holdingsService;
    private final MarketDataService marketDataService;
    
    @org.springframework.lang.Nullable
    private final PortfolioIntradayRedisService intradayRedisService;
    
    private final PortfolioDocumentRepository portfolioDocumentRepository;

    @org.springframework.lang.Nullable
    private final CashSessionClock cashSessionClock;

    public PortfolioIntradayService(
            PortfolioSnapshotService snapshotService,
            PortfolioHoldingsService holdingsService,
            MarketDataService marketDataService,
            @org.springframework.lang.Nullable PortfolioIntradayRedisService intradayRedisService,
            PortfolioDocumentRepository portfolioDocumentRepository,
            @org.springframework.lang.Nullable CashSessionClock cashSessionClock) {
        this.snapshotService = snapshotService;
        this.holdingsService = holdingsService;
        this.marketDataService = marketDataService;
        this.intradayRedisService = intradayRedisService;
        this.portfolioDocumentRepository = portfolioDocumentRepository;
        this.cashSessionClock = cashSessionClock;
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    public List<IntradayDataPoint> getIntraday(String userId, String portfolioId) {
        return getIntraday(userId, portfolioId, null);
    }

    public List<IntradayDataPoint> getIntraday(String userId, String portfolioId, String persona) {
        LocalDate today = LocalDate.now(IST);
        LocalTime nowIST = LocalTime.now(IST);
        boolean marketOpen = cashSessionClock != null
                ? cashSessionClock.isCashOpen()
                : (!nowIST.isBefore(MARKET_OPEN) && !nowIST.isAfter(MARKET_CLOSE));

        String cachePortfolioId = portfolioId;
        if ((portfolioId == null || portfolioId.isBlank())
                && persona != null
                && !persona.isBlank()) {
            cachePortfolioId = "all:" + persona.trim();
        }

        // ── CACHE CHECK ───────────────────────────────────────────────────────────
        Optional<List<IntradayDataPoint>> cached = Optional.empty();
        if (intradayRedisService != null) {
            cached = intradayRedisService.getIntradayData(userId, cachePortfolioId);
            if (cached.isPresent()) {
                log.info("[Intraday] Serving intraday chart from cache for user={}, portfolioId={}", userId, cachePortfolioId);
                return cached.get();
            }
        }

        // ── STEP 1 & 2: Get LIVE Holdings and compute baseline ──────────────
        // For Intraday (1D), the portfolio is exactly what the user holds right now.
        PortfolioHoldings liveHoldings = (portfolioId == null || portfolioId.trim().isEmpty())
            ? holdingsService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true, persona)
            : holdingsService.getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY, true);

        double liveTotalValue = 0.0;
        double liveTodayGainLoss = 0.0;
        Map<String, Double> symbolQty = new HashMap<>();
        Map<String, Map<String, Double>> qtyByPortfolio = new HashMap<>();
        Map<String, String> nameByPortfolio = new HashMap<>();
        Map<String, Double> liveValueByPortfolio = new HashMap<>();
        Map<String, Double> todayGlByPortfolio = new HashMap<>();

        if (liveHoldings != null && liveHoldings.getEquityHoldings() != null) {
            for (com.portfolio.model.portfolio.EquityHoldings h : liveHoldings.getEquityHoldings()) {
                if (h.getSymbol() != null) {
                    double qty = h.getQuantity() != null ? h.getQuantity() : 0.0;
                    symbolQty.merge(h.getSymbol(), qty, Double::sum);
                    if (h.getPortfolioId() != null && !h.getPortfolioId().isBlank()) {
                        qtyByPortfolio
                                .computeIfAbsent(h.getPortfolioId(), k -> new HashMap<>())
                                .merge(h.getSymbol(), qty, Double::sum);
                        if (h.getPortfolioName() != null && !h.getPortfolioName().isBlank()) {
                            nameByPortfolio.putIfAbsent(h.getPortfolioId(), h.getPortfolioName());
                        }
                    }
                }
                
                double holdingValue = 0.0;
                if (h.getCurrentValue() != null) {
                    holdingValue = h.getCurrentValue();
                } else if (h.getInvestmentCost() != null && h.getInvestmentCost() > 0) {
                    holdingValue = h.getInvestmentCost();
                } else if (h.getCurrentPrice() != null && h.getQuantity() != null) {
                    holdingValue = h.getCurrentPrice() * h.getQuantity();
                }
                liveTotalValue += holdingValue;
                if (h.getPortfolioId() != null && !h.getPortfolioId().isBlank() && holdingValue != 0.0) {
                    liveValueByPortfolio.merge(h.getPortfolioId(), holdingValue, Double::sum);
                }
                
                if (h.getTodayGainLoss() != null) {
                    liveTodayGainLoss += h.getTodayGainLoss();
                    if (h.getPortfolioId() != null && !h.getPortfolioId().isBlank()) {
                        todayGlByPortfolio.merge(h.getPortfolioId(), h.getTodayGainLoss(), Double::sum);
                    }
                }
            }
        }

        // When cash is closed, quotes often omit day P&L — rebuild session baseline from hist priorClose
        // so the chart end % matches Today's card (not a flat 0% LTP line).
        if (!marketOpen && Math.abs(liveTodayGainLoss) < 0.005
                && liveHoldings != null
                && liveHoldings.getEquityHoldings() != null
                && !liveHoldings.getEquityHoldings().isEmpty()) {
            Double sessionDay = estimateSessionDayGainFromHist(liveHoldings.getEquityHoldings());
            if (sessionDay != null) {
                liveTodayGainLoss = sessionDay;
                log.info("[Intraday] Session day baseline from holdings-hist gain={}", sessionDay);
            }
        }

        // The opening wealth is simply the current wealth minus today's gain/loss
        double baselineWealth = liveTotalValue - liveTodayGainLoss;
        double missingAssetValue = 0.0;

        if (symbolQty.isEmpty()) {
            return List.of(makeGlobalPoint(
                    "09:15",
                    baselineWealth,
                    baselineWealth,
                    false,
                    portfolioEntries(qtyByPortfolio, nameByPortfolio, liveValueByPortfolio, todayGlByPortfolio, Map.of())));
        }

        // ── STEP 3: Fetch 1D OHLC candles (always — market rolls holiday/weekend) ──
        List<String> symbols = new ArrayList<>(symbolQty.keySet());
        com.portfolio.marketdata.model.HistoricalChartsResponse chartResponse = null;
        LocalDate sessionDate = resolveSessionDate(today, nowIST, marketOpen);
        log.info("[Intraday] Session date={} marketOpen={} (today={})", sessionDate, marketOpen, today);

        // Always fetch 1D candles — market-data resolves last trading day when cash is closed.
        // Dense grid / flat LTP seed is applied in STEP 4.5 when the series is empty.
        try {
            chartResponse = marketDataService.getHistoricalCharts(symbols, "1D");
            if (chartResponse != null && chartResponse.getData() != null) {
                int totalParsed = chartResponse.getData().values().stream()
                        .filter(hd -> hd != null && hd.getDataPoints() != null)
                        .mapToInt(hd -> hd.getDataPoints().size())
                        .sum();
                log.info("[Intraday] Fetched historical charts for {} symbols, total points parsed: {}",
                        symbols.size(), totalParsed);
            }
        } catch (Exception e) {
            log.error("[Intraday] Failed to fetch historical charts: {}", e.getMessage());
        }

        // ── STEP 4: Build time-series: candle time → {symbol → closePrice} ───
        // Keys must match symbolQty (holdings symbols); API/Mongo may return NSE_EQ:X.
        TreeMap<LocalTime, Map<String, Double>> priceSeries = new TreeMap<>();
        if (chartResponse != null && chartResponse.getData() != null) {
            for (Map.Entry<String, com.portfolio.marketdata.model.HistoricalData> entry : chartResponse.getData().entrySet()) {
                String chartSym = entry.getKey();
                String qtySym = resolveQtySymbol(chartSym, symbolQty);
                if (qtySym == null) {
                    continue;
                }
                com.portfolio.marketdata.model.HistoricalData hd = entry.getValue();
                if (hd == null || hd.getDataPoints() == null || hd.getDataPoints().isEmpty()) {
                    continue;
                }

                // --- TIMEZONE AUTO-DETECTION HEURISTIC ---
                // We handle both IST (local) and UTC (cloud) responses without touching am-market.
                // The Indian market opens at 09:15 IST (which is 03:45 UTC).
                // By checking the first candle's hour, we can detect the timezone of the payload.
                boolean isUtcPayload = false;
                com.am.common.investment.model.historical.OHLCVTPoint firstPt = hd.getDataPoints().get(0);
                if (firstPt != null && firstPt.getTime() != null) {
                    int firstHour = firstPt.getTime().toLocalTime().getHour();
                    // If the first candle starts before 9 AM (e.g. 3 AM or 4 AM), it is definitely UTC-shifted.
                    if (firstHour < 9) {
                        isUtcPayload = true;
                    }
                }

                for (com.am.common.investment.model.historical.OHLCVTPoint pt : hd.getDataPoints()) {
                    if (pt.getTime() == null || pt.getClose() == null) {
                        continue;
                    }
                    LocalTime t = pt.getTime().toLocalTime().withSecond(0).withNano(0);
                    
                    if (isUtcPayload) {
                        t = t.plusHours(5).plusMinutes(30);
                    }

                    if (t.isBefore(MARKET_OPEN) || t.isAfter(MARKET_CLOSE)) {
                        continue;
                    }
                    priceSeries.computeIfAbsent(t, k -> new HashMap<>()).put(qtySym, pt.getClose());
                }
            }
        }

        if (priceSeries.isEmpty()) {
            log.warn("[Intraday] No 1D candles after charts fetch — using flat LTP fallback (last resort) user={} portfolioId={} symbols={}",
                    userId, portfolioId, symbols.size());
        } else {
            log.info("[Intraday] Built price series slots={} uniqueTimes covering symbols with candles",
                    priceSeries.size());
        }

        // Fetch current live prices of equities
        Map<String, Double> livePrices = new HashMap<>();
        double liveEquityWealth = 0.0;
        try {
            livePrices = marketDataService.getCurrentPrices(symbols);
            if (livePrices != null) {
                for (Map.Entry<String, Double> sq : symbolQty.entrySet()) {
                    Double price = livePrices.get(sq.getKey());
                    if (price != null) {
                        liveEquityWealth += price * sq.getValue();
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Intraday] Failed to fetch live prices or compute live equity wealth", e);
        }

        // ── STEP 4.5: Dense 09:15→end grid (fixes afternoon-only / holiday gaps) ──
        LocalTime sessionEnd = resolveSessionEnd(nowIST, marketOpen, sessionDate, today);
        // Only seed flatline when there are NO candles (closed day / empty feed).
        Map<String, Double> flatSeed = priceSeries.isEmpty() && livePrices != null
                ? new HashMap<>(livePrices)
                : Map.of();
        priceSeries = fillSessionGrid(priceSeries, flatSeed, sessionEnd);

        // ── STEP 5: Compute portfolio value per candle with carry-forward ──────
        Map<String, Double> lastKnown = new HashMap<>();
        // Seed LTPs for holdings that lack session candles so they are not priced as 0
        // once any other symbol's candle appears (avoids 09:15→09:20 wealth cliffs).
        seedLastKnownFromLivePrices(lastKnown, livePrices, symbolQty);
        List<IntradayDataPoint> result = new ArrayList<>();

        // Anchor: 9:15 AM = session open baseline
        result.add(makeGlobalPoint(
                "09:15",
                baselineWealth,
                baselineWealth,
                false,
                portfolioEntries(qtyByPortfolio, nameByPortfolio, liveValueByPortfolio, todayGlByPortfolio, Map.of())));

        LocalTime latestCandle = priceSeries.isEmpty() ? null : priceSeries.lastKey();
        LocalTime t = MARKET_OPEN.plusMinutes(5);
        while (!t.isAfter(sessionEnd)) {
            Map<String, Double> at = priceSeries.get(t);
            if (at != null && !at.isEmpty()) {
                lastKnown.putAll(at);
            }

            double equityValue = 0.0;
            if (!lastKnown.isEmpty()) {
                equityValue = symbolQty.entrySet().stream()
                        .mapToDouble(sq -> {
                            Double price = lastKnown.get(sq.getKey());
                            return (price != null ? price : 0.0) * sq.getValue();
                        })
                        .sum();
            }
            if (equityValue <= 0) {
                // Before first candle: hold open baseline so X spans full session.
                equityValue = baselineWealth - missingAssetValue;
            }
            if (equityValue <= 0) {
                t = t.plusMinutes(5);
                continue;
            }

            double totalWealth = equityValue + missingAssetValue;
            boolean isLive = latestCandle != null && t.equals(latestCandle) && marketOpen;
            result.add(makeGlobalPoint(
                    t.toString(),
                    totalWealth,
                    baselineWealth,
                    isLive,
                    portfolioEntries(qtyByPortfolio, nameByPortfolio, liveValueByPortfolio, todayGlByPortfolio, lastKnown)));
            t = t.plusMinutes(5);
        }

        // ── STEP 6: LTP Stitching (Industry Standard real-time update) ───
        if (marketOpen && livePrices != null && !livePrices.isEmpty()) {
            LocalTime nowTime = nowIST.withSecond(0).withNano(0);
            if (latestCandle == null || nowTime.isAfter(latestCandle)) {
                double totalWealth = liveEquityWealth + missingAssetValue;
                result.add(makeGlobalPoint(
                        nowTime.toString(),
                        totalWealth,
                        baselineWealth,
                        true,
                        portfolioEntries(qtyByPortfolio, nameByPortfolio, liveValueByPortfolio, todayGlByPortfolio, lastKnown)));
            }
        }

        result = guardAbsurdSessionCliff(result);

        // Cache only series with real movement — do not pin a flat "one drop" day for 30m.
        if (intradayRedisService != null && !isDegenerateIntradaySeries(result)) {
            intradayRedisService.cacheIntradayData(userId, cachePortfolioId, result, marketOpen);
        } else if (intradayRedisService != null) {
            log.warn("[Intraday] Skipping Redis cache for degenerate series (unique wealth values ≤ 1) user={} portfolioId={} points={}",
                    userId, cachePortfolioId, result.size());
        }

        return result;
    }

    /**
     * If session return exceeds ±25% (bogus open baseline / missing early candles),
     * re-anchor open to the first wealth tick within 25% of close so Overall does not cliff to −76%.
     */
    static List<IntradayDataPoint> guardAbsurdSessionCliff(List<IntradayDataPoint> points) {
        if (points == null || points.size() < 2) {
            return points;
        }
        IntradayDataPoint first = points.get(0);
        IntradayDataPoint last = points.get(points.size() - 1);
        if (first == null || last == null || first.getTotalWealth() <= 0) {
            return points;
        }
        double open = first.getTotalWealth();
        double close = last.getTotalWealth();
        double ret = (close - open) / open;
        if (Math.abs(ret) <= 0.25) {
            return points;
        }
        double baseline = close;
        for (IntradayDataPoint p : points) {
            if (p == null || p.getTotalWealth() <= 0) {
                continue;
            }
            if (Math.abs((close - p.getTotalWealth()) / p.getTotalWealth()) <= 0.25) {
                baseline = p.getTotalWealth();
                break;
            }
        }
        List<IntradayDataPoint> fixed = new ArrayList<>(points.size());
        boolean sawAnchor = false;
        for (IntradayDataPoint p : points) {
            if (p == null) {
                continue;
            }
            double value = p.getTotalWealth();
            // Hold inflated pre-cliff ticks at the re-anchor so session % is sane.
            if (!sawAnchor) {
                if (Math.abs((value - baseline) / baseline) > 0.25) {
                    value = baseline;
                } else {
                    sawAnchor = true;
                }
            }
            double change = value - baseline;
            double changePct = baseline > 0 ? (change / baseline) * 100.0 : 0.0;
            fixed.add(IntradayDataPoint.builder()
                    .timestamp(p.getTimestamp())
                    .totalWealth(value)
                    .changeFromOpen(change)
                    .changeFromOpenPct(changePct)
                    .isLive(p.isLive())
                    .portfolios(p.getPortfolios())
                    .build());
        }
        return fixed.isEmpty() ? points : fixed;
    }

    /**
     * Pre-fill {@code lastKnown} with live LTPs mapped onto holdings keys.
     * Candle closes overwrite these later; symbols never present in candles keep LTP
     * instead of contributing {@code 0} once the series is non-empty.
     */
    static void seedLastKnownFromLivePrices(
            Map<String, Double> lastKnown,
            Map<String, Double> livePrices,
            Map<String, Double> symbolQty) {
        if (lastKnown == null || livePrices == null || livePrices.isEmpty() || symbolQty == null || symbolQty.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> e : livePrices.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            String qtySym = resolveQtySymbol(e.getKey(), symbolQty);
            if (qtySym != null && !lastKnown.containsKey(qtySym)) {
                lastKnown.put(qtySym, e.getValue());
            }
        }
        // Also seed by holdings key when live map uses the same bare ticker.
        for (String qtySym : symbolQty.keySet()) {
            if (qtySym == null || lastKnown.containsKey(qtySym)) {
                continue;
            }
            Double p = livePrices.get(qtySym);
            if (p == null) {
                for (Map.Entry<String, Double> e : livePrices.entrySet()) {
                    if (e.getKey() != null && e.getKey().equalsIgnoreCase(qtySym) && e.getValue() != null && e.getValue() > 0) {
                        p = e.getValue();
                        break;
                    }
                }
            }
            if (p != null && p > 0) {
                lastKnown.put(qtySym, p);
            }
        }
    }

    /** Map chart/API symbol key onto a holdings symbolQty key (exact, strip NSE_EQ:/BSE_EQ:). */
    static String resolveQtySymbol(String chartSym, Map<String, Double> symbolQty) {
        if (chartSym == null || symbolQty == null || symbolQty.isEmpty()) {
            return null;
        }
        if (symbolQty.containsKey(chartSym)) {
            return chartSym;
        }
        String cleaned = chartSym;
        int colon = chartSym.indexOf(':');
        if (colon > 0 && colon < chartSym.length() - 1) {
            cleaned = chartSym.substring(colon + 1);
        }
        if (symbolQty.containsKey(cleaned)) {
            return cleaned;
        }
        for (String k : symbolQty.keySet()) {
            if (k != null && k.equalsIgnoreCase(cleaned)) {
                return k;
            }
            if (k != null && k.equalsIgnoreCase(chartSym)) {
                return k;
            }
        }
        return null;
    }

    /** True when wealth barely moves (flat LTP seed / empty candles). */
    static boolean isDegenerateIntradaySeries(List<IntradayDataPoint> points) {
        if (points == null || points.size() <= 1) {
            return true;
        }
        java.util.HashSet<Long> uniqueCents = new java.util.HashSet<>();
        for (IntradayDataPoint p : points) {
            if (p == null) {
                continue;
            }
            uniqueCents.add(Math.round(p.getTotalWealth() * 100.0));
            if (uniqueCents.size() > 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cash session date for the 1D chart: today while the market is open/post-open on a
     * weekday; otherwise last weekday (weekend / pre-open / holiday walk-back).
     */
    static LocalDate resolveSessionDate(LocalDate today, LocalTime nowIst, boolean marketOpen) {
        LocalDate target = today;
        java.time.DayOfWeek day = today.getDayOfWeek();
        boolean preMarket = nowIst.isBefore(MARKET_OPEN);
        if (day == java.time.DayOfWeek.SATURDAY) {
            target = today.minusDays(1);
        } else if (day == java.time.DayOfWeek.SUNDAY) {
            target = today.minusDays(2);
        } else if (preMarket && !marketOpen) {
            target = today.minusDays(1);
            if (target.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                target = target.minusDays(2);
            } else if (target.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
                target = target.minusDays(1);
            }
        } else if (!marketOpen && nowIst.isAfter(MARKET_CLOSE)) {
            // After close on a weekday — still today's session.
            target = today;
        } else if (!marketOpen) {
            // Midweek holiday / cash closed during day: walk back to prior weekday.
            target = today.minusDays(1);
            while (target.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                    || target.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                target = target.minusDays(1);
            }
        }
        return target;
    }

    static LocalTime resolveSessionEnd(
            LocalTime nowIst, boolean marketOpen, LocalDate sessionDate, LocalDate today) {
        if (!sessionDate.equals(today)) {
            return MARKET_CLOSE;
        }
        if (marketOpen && !nowIst.isBefore(MARKET_OPEN) && !nowIst.isAfter(MARKET_CLOSE)) {
            return nowIst.withSecond(0).withNano(0);
        }
        if (nowIst.isAfter(MARKET_CLOSE)) {
            return MARKET_CLOSE;
        }
        if (nowIst.isBefore(MARKET_OPEN)) {
            return MARKET_OPEN;
        }
        return MARKET_CLOSE;
    }

    /**
     * Ensures a 5-minute grid from market open through {@code sessionEnd}.
     * Carry-forward only applies after the first real candle (does not back-date
     * afternoon prices into the morning). When {@code flatSeed} is non-empty and
     * candles are empty, fills a flat session (holiday / no feed).
     */
    static TreeMap<LocalTime, Map<String, Double>> fillSessionGrid(
            TreeMap<LocalTime, Map<String, Double>> candles,
            Map<String, Double> flatSeed,
            LocalTime sessionEnd) {
        TreeMap<LocalTime, Map<String, Double>> filled = new TreeMap<>();
        LocalTime end = sessionEnd == null || sessionEnd.isBefore(MARKET_OPEN) ? MARKET_OPEN : sessionEnd;
        if (end.isAfter(MARKET_CLOSE)) {
            end = MARKET_CLOSE;
        }
        boolean hasCandles = candles != null && !candles.isEmpty();
        Map<String, Double> carry = new HashMap<>();
        LocalTime t = MARKET_OPEN;
        while (!t.isAfter(end)) {
            Map<String, Double> at = hasCandles ? candles.get(t) : null;
            if (at != null && !at.isEmpty()) {
                carry.putAll(at);
            }
            if (!carry.isEmpty()) {
                filled.put(t, new HashMap<>(carry));
            } else if (!hasCandles && flatSeed != null && !flatSeed.isEmpty()) {
                filled.put(t, new HashMap<>(flatSeed));
            }
            t = t.plusMinutes(5);
        }
        if (hasCandles) {
            for (Map.Entry<LocalTime, Map<String, Double>> e : candles.entrySet()) {
                if (!e.getKey().isAfter(end)) {
                    filled.put(e.getKey(), new HashMap<>(e.getValue()));
                }
            }
            // Re-apply forward carry so gaps between sparse candles are filled.
            carry.clear();
            TreeMap<LocalTime, Map<String, Double>> dense = new TreeMap<>();
            t = MARKET_OPEN;
            while (!t.isAfter(end)) {
                Map<String, Double> at = filled.get(t);
                if (at != null && !at.isEmpty()) {
                    carry.putAll(at);
                }
                if (!carry.isEmpty()) {
                    dense.put(t, new HashMap<>(carry));
                }
                t = t.plusMinutes(5);
            }
            return dense;
        }
        return filled;
    }

    private IntradayDataPoint makeGlobalPoint(
            String ts,
            double value,
            double baseline,
            boolean isLive,
            List<PortfolioIntradayEntry> portfolios) {
        double change = value - baseline;
        double changePct = baseline > 0 ? (change / baseline) * 100.0 : 0.0;
        return IntradayDataPoint.builder()
                .timestamp(ts).totalWealth(value)
                .changeFromOpen(change).changeFromOpenPct(changePct)
                .isLive(isLive)
                .portfolios(portfolios == null || portfolios.isEmpty() ? null : portfolios)
                .build();
    }

    private List<PortfolioIntradayEntry> portfolioEntries(
            Map<String, Map<String, Double>> qtyByPortfolio,
            Map<String, String> nameByPortfolio,
            Map<String, Double> liveValueByPortfolio,
            Map<String, Double> todayGlByPortfolio,
            Map<String, Double> lastKnown) {
        if (qtyByPortfolio.isEmpty()) {
            return List.of();
        }
        List<PortfolioIntradayEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> book : qtyByPortfolio.entrySet()) {
            String id = book.getKey();
            double value = book.getValue().entrySet().stream()
                    .mapToDouble(sq -> {
                        Double price = lastKnown.get(sq.getKey());
                        return (price != null ? price : 0.0) * sq.getValue();
                    })
                    .sum();
            double live = liveValueByPortfolio.getOrDefault(id, 0.0);
            double todayGl = todayGlByPortfolio.getOrDefault(id, 0.0);
            double baseline = live - todayGl;
            if (value <= 0) {
                value = baseline;
            }
            double change = value - baseline;
            double changePct = baseline > 0 ? (change / baseline) * 100.0 : 0.0;
            String name = nameByPortfolio.get(id);
            entries.add(PortfolioIntradayEntry.builder()
                    .portfolioId(id)
                    .portfolioName(name != null && !name.isBlank() ? name : "Portfolio")
                    .value(value)
                    .changeFromOpen(change)
                    .changeFromOpenPct(changePct)
                    .build());
        }
        return entries;
    }

    /**
     * Sum qty × (LTP − hist priorClose) when holdings lack todayGainLoss after hours.
     * Never uses investment cost as the day baseline.
     */
    private Double estimateSessionDayGainFromHist(List<EquityHoldings> holdings) {
        if (marketDataService == null || holdings == null || holdings.isEmpty()) {
            return null;
        }
        List<String> symbols = new ArrayList<>();
        for (EquityHoldings h : holdings) {
            if (h.getSymbol() != null && !h.getSymbol().isBlank()) {
                symbols.add(h.getSymbol());
            }
        }
        if (symbols.isEmpty()) {
            return null;
        }
        Map<String, MarketData> quotes;
        try {
            quotes = marketDataService.getMarketData(symbols);
        } catch (Exception e) {
            log.debug("[Intraday] getMarketData for session baseline failed: {}", e.getMessage());
            return null;
        }
        if (quotes == null || quotes.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now(IST);
        Map<String, MarketData> hist = Map.of();
        try {
            hist = marketDataService.getHistoricalData(
                    symbols,
                    today.minusDays(14),
                    today,
                    TimeFrame.DAY,
                    InstrumentType.STOCK,
                    FilterType.ALL,
                    null,
                    null,
                    Boolean.FALSE);
        } catch (Exception e) {
            log.debug("[Intraday] Daily hist for session baseline skipped: {}", e.getMessage());
        }

        double daySum = 0.0;
        int counted = 0;
        for (EquityHoldings h : holdings) {
            if (h.getSymbol() == null || h.getQuantity() == null || h.getQuantity() <= 0) {
                continue;
            }
            MarketData md = lookupMd(quotes, h.getSymbol());
            Double ltp = h.getCurrentPrice();
            if ((ltp == null || ltp <= 0) && md != null) {
                ltp = md.getLastPrice();
            }
            if (ltp == null || ltp <= 0) {
                continue;
            }
            Double prior = md != null ? md.getPreviousClose() : null;
            if (prior == null || !MarketDataService.isDistinctPrior(prior, ltp)) {
                prior = MarketDataService.resolvePriorCloseFromHistorical(lookupMd(hist, h.getSymbol()), ltp);
            }
            if (prior == null || !MarketDataService.isDistinctPrior(prior, ltp)) {
                continue;
            }
            daySum += (ltp - prior) * h.getQuantity();
            counted++;
        }
        return counted > 0 ? daySum : null;
    }

    private static MarketData lookupMd(Map<String, MarketData> map, String symbol) {
        if (map == null || symbol == null) {
            return null;
        }
        MarketData md = map.get(symbol);
        if (md != null) {
            return md;
        }
        String cleaned = symbol;
        int colon = symbol.indexOf(':');
        if (colon > 0 && colon < symbol.length() - 1) {
            cleaned = symbol.substring(colon + 1);
        }
        md = map.get(cleaned);
        if (md != null) {
            return md;
        }
        return map.get(cleaned.toUpperCase(java.util.Locale.ROOT));
    }
}
