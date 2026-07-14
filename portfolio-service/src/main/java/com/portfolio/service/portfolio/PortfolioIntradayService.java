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
import com.am.common.amcommondata.repository.portfolio.PortfolioDocumentRepository;
import com.am.common.amcommondata.document.portfolio.PortfolioDocument;

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
    private final PortfolioDocumentRepository portfolioDocumentRepository;

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

        // Fetch current portfolio value from MongoDB to compute the Mutual Fund offset
        double currentMongoValue = 0.0;
        try {
            if (portfolioId == null || portfolioId.trim().isEmpty()) {
                List<PortfolioDocument> portfolios = portfolioDocumentRepository.findByOwner(userId);
                if (portfolios != null) {
                    currentMongoValue = portfolios.stream()
                            .mapToDouble(p -> p.getTotalValue() != null ? p.getTotalValue() : 0.0)
                            .sum();
                }
            } else {
                Optional<PortfolioDocument> pOpt = portfolioDocumentRepository.findById(portfolioId);
                if (pOpt.isPresent()) {
                    currentMongoValue = pOpt.get().getTotalValue() != null ? pOpt.get().getTotalValue() : 0.0;
                }
            }
        } catch (Exception e) {
            log.error("[Intraday] Failed to fetch current totalValue from MongoDB", e);
        }

        // ── STEP 3: Fetch 1D OHLC candles for all symbols in batch ─────────────
        List<String> symbols = new ArrayList<>(symbolQty.keySet());
        com.portfolio.marketdata.model.HistoricalChartsResponse chartResponse = null;

        try {
            chartResponse = marketDataService.getHistoricalCharts(symbols, "1D");
            if (chartResponse != null && chartResponse.getData() != null) {
                int totalParsed = chartResponse.getData().values().stream()
                        .filter(hd -> hd != null && hd.getDataPoints() != null)
                        .mapToInt(hd -> hd.getDataPoints().size())
                        .sum();
                log.info("[Intraday] Fetched historical charts for {} symbols, total points parsed: {}", symbols.size(), totalParsed);
            }
        } catch (Exception e) {
            log.error("[Intraday] Failed to fetch historical charts: {}", e.getMessage());
        }

        // ── STEP 4: Build time-series: candle time → {symbol → closePrice} ───
        TreeMap<LocalTime, Map<String, Double>> priceSeries = new TreeMap<>();
        if (chartResponse != null && chartResponse.getData() != null) {
            for (Map.Entry<String, com.portfolio.marketdata.model.HistoricalData> entry : chartResponse.getData().entrySet()) {
                String sym = entry.getKey();
                com.portfolio.marketdata.model.HistoricalData hd = entry.getValue();
                if (hd == null || hd.getDataPoints() == null) {
                    continue;
                }
                for (com.am.common.investment.model.historical.OHLCVTPoint pt : hd.getDataPoints()) {
                    if (pt.getTime() == null || pt.getClose() == null) {
                        continue;
                    }
                    LocalTime t = pt.getTime().toLocalTime().withSecond(0).withNano(0);
                    if (t.isBefore(MARKET_OPEN) || t.isAfter(MARKET_CLOSE)) {
                        continue;
                    }
                    priceSeries.computeIfAbsent(t, k -> new HashMap<>()).put(sym, pt.getClose());
                }
            }
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

        // Compute flat offset for unsupported assets (Mutual Funds / Bonds)
        double finalMongoVal = currentMongoValue > 0.0 ? currentMongoValue : baselineWealth;
        double missingAssetValue = finalMongoVal - liveEquityWealth;
        if (missingAssetValue < 0.0) {
            missingAssetValue = 0.0;
        }

        // Fallback: If 1D candles are empty (e.g., market not open yet), generate flat 75-point chart using previous close
        if (priceSeries.isEmpty()) {
            if (livePrices != null && !livePrices.isEmpty()) {
                LocalTime t = MARKET_OPEN;
                while (!t.isAfter(MARKET_CLOSE)) {
                    priceSeries.put(t, livePrices);
                    t = t.plusMinutes(5);
                }
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

            double equityValue = symbolQty.entrySet().stream()
                    .mapToDouble(sq -> {
                        Double price = lastKnown.get(sq.getKey());
                        return (price != null ? price : 0.0) * sq.getValue();
                    })
                    .sum();

            if (equityValue <= 0) {
                continue;
            }

            double totalWealth = equityValue + missingAssetValue;
            boolean isLive = t.equals(latestCandle) && marketOpen;
            result.add(makeGlobalPoint(t.toString(), totalWealth, baselineWealth, isLive));
        }

        // ── STEP 6: LTP Stitching (Industry Standard real-time update) ───
        if (marketOpen && livePrices != null && !livePrices.isEmpty()) {
            LocalTime nowTime = nowIST.withSecond(0).withNano(0);
            if (latestCandle == null || nowTime.isAfter(latestCandle)) {
                double totalWealth = liveEquityWealth + missingAssetValue;
                result.add(makeGlobalPoint(nowTime.toString(), totalWealth, baselineWealth, true));
            }
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
