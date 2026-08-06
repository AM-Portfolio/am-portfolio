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
@Slf4j
public class PortfolioIntradayService {

    private final PortfolioSnapshotService snapshotService;
    private final PortfolioHoldingsService holdingsService;
    private final MarketDataService marketDataService;
    
    @org.springframework.lang.Nullable
    private final PortfolioIntradayRedisService intradayRedisService;
    
    private final PortfolioDocumentRepository portfolioDocumentRepository;

    public PortfolioIntradayService(
            PortfolioSnapshotService snapshotService,
            PortfolioHoldingsService holdingsService,
            MarketDataService marketDataService,
            @org.springframework.lang.Nullable PortfolioIntradayRedisService intradayRedisService,
            PortfolioDocumentRepository portfolioDocumentRepository) {
        this.snapshotService = snapshotService;
        this.holdingsService = holdingsService;
        this.marketDataService = marketDataService;
        this.intradayRedisService = intradayRedisService;
        this.portfolioDocumentRepository = portfolioDocumentRepository;
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    public List<IntradayDataPoint> getIntraday(String userId, String portfolioId) {
        LocalDate today = LocalDate.now(IST);
        LocalTime nowIST = LocalTime.now(IST);
        boolean marketOpen = !nowIST.isBefore(MARKET_OPEN) && !nowIST.isAfter(MARKET_CLOSE);

        // ── CACHE CHECK ───────────────────────────────────────────────────────────
        Optional<List<IntradayDataPoint>> cached = Optional.empty();
        if (intradayRedisService != null) {
            cached = intradayRedisService.getIntradayData(userId, portfolioId);
            if (cached.isPresent()) {
                log.info("[Intraday] Serving intraday chart from cache for user={}, portfolioId={}", userId, portfolioId);
                return cached.get();
            }
        }

        // ── STEP 1 & 2: Get LIVE Holdings and compute baseline ──────────────
        // For Intraday (1D), the portfolio is exactly what the user holds right now.
        PortfolioHoldings liveHoldings = (portfolioId == null || portfolioId.trim().isEmpty()) 
            ? holdingsService.getPortfolioHoldings(userId, TimeInterval.ONE_DAY, true)
            : holdingsService.getPortfolioHoldings(userId, portfolioId, TimeInterval.ONE_DAY, true);

        double liveTotalValue = 0.0;
        double liveTodayGainLoss = 0.0;
        Map<String, Double> symbolQty = new HashMap<>();

        if (liveHoldings != null && liveHoldings.getEquityHoldings() != null) {
            for (com.portfolio.model.portfolio.EquityHoldings h : liveHoldings.getEquityHoldings()) {
                if (h.getSymbol() != null) {
                    double qty = h.getQuantity() != null ? h.getQuantity() : 0.0;
                    symbolQty.merge(h.getSymbol(), qty, Double::sum);
                }
                
                if (h.getCurrentValue() != null) {
                    liveTotalValue += h.getCurrentValue();
                } else if (h.getInvestmentCost() != null && h.getInvestmentCost() > 0) {
                    liveTotalValue += h.getInvestmentCost();
                } else if (h.getCurrentPrice() != null && h.getQuantity() != null) {
                    liveTotalValue += h.getCurrentPrice() * h.getQuantity();
                }
                
                if (h.getTodayGainLoss() != null) {
                    liveTodayGainLoss += h.getTodayGainLoss();
                }
            }
        }

        // The opening wealth is simply the current wealth minus today's gain/loss
        double baselineWealth = liveTotalValue - liveTodayGainLoss;
        double missingAssetValue = 0.0;

        if (symbolQty.isEmpty()) {
            return List.of(makeGlobalPoint("09:15", baselineWealth, baselineWealth, false));
        }

        // ── STEP 3: Fetch 1D OHLC candles for all symbols in batch ─────────────
        List<String> symbols = new ArrayList<>(symbolQty.keySet());
        com.portfolio.marketdata.model.HistoricalChartsResponse chartResponse = null;

        java.time.DayOfWeek dayOfWeek = today.getDayOfWeek();
        boolean isWeekend = dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY;
        boolean preMarket = nowIST.isBefore(MARKET_OPEN);

        if (isWeekend || preMarket) {
            log.info("[Intraday] Skipping 1D chart fetch because market is closed (weekend/pre-market).");
        } else {
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
        }

        // ── STEP 4: Build time-series: candle time → {symbol → closePrice} ───
        TreeMap<LocalTime, Map<String, Double>> priceSeries = new TreeMap<>();
        if (chartResponse != null && chartResponse.getData() != null) {
            for (Map.Entry<String, com.portfolio.marketdata.model.HistoricalData> entry : chartResponse.getData().entrySet()) {
                String sym = entry.getKey();
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

        // ── STEP 4.5: Weekend/Non-Trading Day Real Lookback ───
        // (The 1W fallback hack was removed to eliminate 30s timeouts.
        // It seamlessly falls through to the native flat chart generator below.)

        // Fallback: If STILL empty, generate a flat chart using the last known prices
        if (priceSeries.isEmpty()) {
            if (livePrices != null && !livePrices.isEmpty()) {
                LocalTime t = MARKET_OPEN;
                
                dayOfWeek = today.getDayOfWeek();
                isWeekend = dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY;
                
                LocalTime limit;
                if (isWeekend || nowIST.isAfter(MARKET_CLOSE)) {
                    limit = MARKET_CLOSE; // Full day flatline
                } else if (nowIST.isBefore(MARKET_OPEN)) {
                    limit = MARKET_OPEN; // Only the opening point
                } else {
                    limit = nowIST; // Fill up to current time
                }

                while (!t.isAfter(limit)) {
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
        if (intradayRedisService != null) {
            intradayRedisService.cacheIntradayData(userId, portfolioId, result, marketOpen);
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
