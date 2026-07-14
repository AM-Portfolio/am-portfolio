package com.portfolio.service.portfolio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.HoldingSnapshotItemModel;
import com.am.common.amcommondata.model.PortfolioSnapshotEntryModel;
import com.am.common.amcommondata.model.PortfolioSnapshotModel;
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.model.HistoricalDataRequest;
import com.portfolio.model.market.MarketData;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.redis.service.PortfolioIntradayRedisService;
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
    private final PortfolioIntradayRedisService intradayRedisService;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    public List<IntradayDataPoint> getIntraday(String userId, String portfolioId) {
        LocalDate today = LocalDate.now(IST);
        LocalTime nowIST = LocalTime.now(IST);
        boolean marketOpen = !nowIST.isBefore(MARKET_OPEN) && !nowIST.isAfter(MARKET_CLOSE);

        // ── CACHE CHECK ───────────────────────────────────────────────────────────
        Optional<List<IntradayDataPoint>> cached = intradayRedisService.getIntradayData(userId, portfolioId);
        if (cached.isPresent()) {
            log.info("[Intraday] Serving intraday chart from cache for user={}, portfolioId={}", userId, portfolioId);
            return cached.get();
        }

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
            if (portfolioId != null) {
                // For a single portfolio, recompute the baseline from its specific components
                baselineWealth = 0.0;
                if (baselineSnap.getPortfolios() != null) {
                    for (PortfolioSnapshotEntryModel entry : baselineSnap.getPortfolios()) {
                        if (portfolioId.equals(entry.getPortfolioId())) {
                            baselineWealth = entry.getClose() != null ? entry.getClose() : 0.0;
                            break;
                        }
                    }
                }
            }
        }

        if (symbolQty.isEmpty()) {
            // Fallback: fetch live holdings from broker
            log.warn("[Intraday] No holdings in snapshot for user={}, falling back to live holdings", userId);
            PortfolioHoldings live = (portfolioId == null || portfolioId.trim().isEmpty()) 
                ? holdingsService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY)
                : holdingsService.getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY);
            if (live != null && live.getEquityHoldings() != null) {
                live.getEquityHoldings().forEach(h -> {
                    if (h.getSymbol() != null) {
                        double qty = h.getQuantity() != null ? h.getQuantity() : 0.0;
                        symbolQty.merge(h.getSymbol(), qty, Double::sum);
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
                HistoricalDataRequest req = HistoricalDataRequest.builder()
                        .symbols(String.join(",", symbols))
                        .fromDate(today.toString())
                        .toDate(today.toString())
                        .interval(TimeFrame.FIVE_MIN.getValue())
                        .filterType(FilterType.ALL.getValue())
                        .instrumentType(InstrumentType.STOCK.getValue())
                        .continuous(false)
                        .forceRefresh(false)
                        .build();
                intradayData = marketDataService.getHistoricalData(req);
                log.info("[Intraday] Fetched 5-min candles for {} symbols via historical-data", intradayData.size());
            } catch (Exception e) {
                log.error("[Intraday] Failed to fetch historical data: {}", e.getMessage());
            }
        }

        // ── STEP 4: Build time-series: candle time → {symbol → closePrice} ───
        TreeMap<LocalTime, Map<String, Double>> priceSeries = new TreeMap<>();
        
        if (intradayData != null) {
            for (Map.Entry<String, MarketData> entry : intradayData.entrySet()) {
                String sym = entry.getKey();
                MarketData md = entry.getValue();
                if (md == null || md.getDataPoints() == null) {
                    continue;
                }
                for (MarketData.MarketDataPoint pt : md.getDataPoints()) {
                    if (pt.getTimestamp() == null || pt.getOhlcData() == null) {
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
        }

        // ── STEP 5: Compute portfolio value per candle with carry-forward ──────
        Map<String, Double> lastKnown = new HashMap<>();
        List<IntradayDataPoint> result = new ArrayList<>();

        // Anchor: 9:15 AM = yesterday's close
        result.add(makeGlobalPoint("09:15", baselineWealth, baselineWealth, false));

        // Fallback: If Upstox historical API didn't return today's 5-min candles, fetch the current live prices
        if (priceSeries.isEmpty() && marketOpen || (priceSeries.isEmpty() && nowIST.isAfter(MARKET_OPEN))) {
            try {
                log.info("[Intraday] 5-min candles empty, fetching live prices as fallback for user={}, portfolioId={}", userId, portfolioId);
                Map<String, Double> livePrices = marketDataService.getCurrentPrices(new ArrayList<>(symbols));
                if (livePrices != null && !livePrices.isEmpty()) {
                    LocalTime now = nowIST.withSecond(0).withNano(0);
                    if (now.isAfter(MARKET_CLOSE)) {
                        now = MARKET_CLOSE;
                    }
                    priceSeries.put(now, livePrices);
                }
            } catch (Exception e) {
                log.error("[Intraday] Failed to fetch live prices fallback: {}", e.getMessage());
            }
        }

        LocalTime latestCandle = priceSeries.isEmpty() ? null : priceSeries.lastKey();

        for (Map.Entry<LocalTime, Map<String, Double>> entry : priceSeries.entrySet()) {
            LocalTime t = entry.getKey();
            lastKnown.putAll(entry.getValue()); // carry-forward update

            double portfolioValue = symbolQty.entrySet().stream()
                    .mapToDouble(sq -> {
                        Double price = lastKnown.get(sq.getKey());
                        return (price != null ? price : 0.0) * sq.getValue();
                    })
                    .sum();

            if (portfolioValue <= 0) {
                continue;
            }

            boolean isLive = t.equals(latestCandle) && marketOpen;
            result.add(makeGlobalPoint(t.toString(), portfolioValue, baselineWealth, isLive));
        }

        // Cache the result before returning
        intradayRedisService.cacheIntradayData(userId, portfolioId, result, marketOpen);

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
