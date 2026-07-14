package com.portfolio.service.portfolio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.document.portfolio.HoldingSnapshotItem;
import com.am.common.amcommondata.document.portfolio.PortfolioSnapshotEntry;
import com.am.common.amcommondata.model.portfolio.HoldingSnapshotItemModel;
import com.am.common.amcommondata.model.portfolio.PortfolioSnapshotEntryModel;
import com.am.common.amcommondata.model.portfolio.PortfolioSnapshotModel;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.model.MarketData;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.market.TimeFrame;
import com.portfolio.model.portfolio.IntradayDataPoint;
import com.portfolio.model.portfolio.PortfolioHoldings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioIntradayService {

    private final PortfolioSnapshotService snapshotService;
    private final PortfolioHoldingsService holdingsService;
    private final MarketDataService marketDataService;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    public List<IntradayDataPoint> getIntraday(String userId, String portfolioId) {
        LocalDate today = LocalDate.now(IST);
        LocalTime nowIST = LocalTime.now(IST);
        boolean marketOpen = !nowIST.isBefore(MARKET_OPEN) && !nowIST.isAfter(MARKET_CLOSE);

        // ── STEP 1: Get yesterday's EOD snapshot ──────────────────────────────
        // Fetch last 7 days to handle weekends & holidays gracefully
        List<PortfolioSnapshotModel> recentSnaps = snapshotService.getHistory(userId, portfolioId, "1W");

        // Find the MOST RECENT snapshot that is NOT today
        PortfolioSnapshotModel baselineSnap = recentSnaps.stream()
                .filter(s -> s.getSnapshotDate() != null && !today.equals(s.getSnapshotDate()))
                .max(Comparator.comparing(PortfolioSnapshotModel::getSnapshotDate))
                .orElse(null);

        if (baselineSnap == null) {
            // No history at all — return empty, UI shows "No history yet"
            return List.of();
        }

        double baselineWealth = baselineSnap.getTotalUserWealth() != null ? baselineSnap.getTotalUserWealth() : 0.0;

        // ── STEP 2: Get holdings from the snapshot document (most reliable) ──
        Map<String, Double> symbolQty = new HashMap<>();

        if (baselineSnap.getPortfolios() != null) {
            for (PortfolioSnapshotEntryModel entry : baselineSnap.getPortfolios()) {
                // If specific portfolio filter, skip non-matching
                if (portfolioId != null && !portfolioId.equals(entry.getPortfolioId())) {
                    continue;
                }

                if (entry.getHoldings() != null) {
                    for (HoldingSnapshotItemModel h : entry.getHoldings()) {
                        if (h.getSymbol() != null) {
                            symbolQty.merge(h.getSymbol(), h.getQuantity() != null ? h.getQuantity() : 0.0,
                                    Double::sum);
                        }
                    }
                }
            }
        }

        if (symbolQty.isEmpty()) {
            // Fallback: fetch live holdings from broker
            log.warn("[Intraday] No holdings in snapshot for user={}, falling back to live holdings", userId);
            PortfolioHoldings live = holdingsService.getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY);
            if (live != null && live.getEquityHoldings() != null) {
                live.getEquityHoldings().forEach(h -> {
                    if (h.getSymbol() != null) {
                        symbolQty.merge(h.getSymbol(), (double) h.getQuantity(), Double::sum);
                    }
                });
            }
        }

        if (symbolQty.isEmpty()) {
            return List.of(makeGlobalPoint("09:15", baselineWealth, baselineWealth, false));
        }

        // ── STEP 3: Fetch 15-min OHLC candles for all symbols ─────────────────
        List<String> symbols = new ArrayList<>(symbolQty.keySet());
        Map<String, MarketData> intradayData = Collections.emptyMap();

        if (marketOpen || nowIST.isAfter(MARKET_OPEN)) {
            // Only fetch if market has opened today
            try {
                intradayData = marketDataService.getHistoricalData(
                        symbols,
                        today, today,
                        TimeFrame.FIFTEEN_MIN,
                        InstrumentType.STOCK,
                        FilterType.ALL,
                        null, null, false);
                log.info("[Intraday] Fetched 15-min candles for {} symbols", intradayData.size());
            } catch (Exception e) {
                log.error("[Intraday] Failed to fetch OHLC data: {}", e.getMessage());
            }
        }

        // ── STEP 4: Build time-series: candle time → {symbol → closePrice} ───
        TreeMap<LocalTime, Map<String, Double>> priceSeries = new TreeMap<>();
        for (Map.Entry<String, MarketData> entry : intradayData.entrySet()) {
            String sym = entry.getKey();
            MarketData md = entry.getValue();
            if (md.getDataPoints() == null) {
                continue;
            }
            for (MarketData.MarketDataPoint pt : md.getDataPoints()) {
                if (pt.getTimestamp() == null || pt.getOhlcData() == null || pt.getOhlcData().getClose() == null) {
                    continue;
                }
                LocalTime t = pt.getTimestamp().atZone(IST).toLocalTime()
                        .withSecond(0).withNano(0); // normalize to minute
                if (t.isBefore(MARKET_OPEN) || t.isAfter(MARKET_CLOSE)) {
                    continue;
                }
                priceSeries.computeIfAbsent(t, k -> new HashMap<>()).put(sym, pt.getOhlcData().getClose());
            }
        }

        // ── STEP 5: Compute portfolio value per candle with carry-forward ──────
        Map<String, Double> lastKnown = new HashMap<>();
        List<IntradayDataPoint> result = new ArrayList<>();

        // Anchor: 9:15 AM = yesterday's close
        result.add(makeGlobalPoint("09:15", baselineWealth, baselineWealth, false));

        LocalTime latestCandle = priceSeries.isEmpty() ? null : priceSeries.lastKey();

        for (Map.Entry<LocalTime, Map<String, Double>> entry : priceSeries.entrySet()) {
            LocalTime t = entry.getKey();
            lastKnown.putAll(entry.getValue()); // carry-forward update

            double portfolioValue = symbolQty.entrySet().stream()
                    .mapToDouble(sq -> lastKnown.getOrDefault(sq.getKey(), 0.0) * sq.getValue())
                    .sum();

            if (portfolioValue <= 0) {
                continue;
            }

            boolean isLive = t.equals(latestCandle) && marketOpen;
            result.add(makeGlobalPoint(t.toString(), portfolioValue, baselineWealth, isLive));
        }

        return result;
    }

    private IntradayDataPoint makeGlobalPoint(String ts, double value, double baseline, boolean isLive) {
        double change = value - baseline;
        double changePct = baseline > 0 ? (change / baseline) * 100.0 : 0.0;
        return IntradayDataPoint.builder()
                .timestamp(ts).totalWealth(value)
                .changeFromOpen(change).changeFromOpenPct(changePct)
                .isLive(isLive)
                .build();
    }
}
