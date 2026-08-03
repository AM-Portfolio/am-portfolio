package com.portfolio.api;

import com.am.common.amcommondata.document.portfolio.PortfolioIntradaySessionDocument;
import com.am.common.amcommondata.model.PortfolioSnapshotModel;
import com.am.common.amcommondata.repository.portfolio.PortfolioIntradaySessionRepository;
import com.am.common.amcommondata.service.PortfolioSnapshotService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/v1/portfolios/dev")
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "local", "default"})
public class DevAdminController {

    private final PortfolioSnapshotService snapshotService;
    private final MarketDataService marketDataService;
    private final PortfolioIntradaySessionRepository intradaySessionRepository;

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Seeds portfolio_intraday_sessions directly with a full 75-point intraday curve.
     * Bypasses getIntraday() entirely to prevent circular fallback issues.
     */
    @PostMapping("/seed-intraday/{userId}")
    public ResponseEntity<Map<String, Object>> seedIntraday(
            @PathVariable String userId,
            @RequestParam(required = false) String portfolioId) {

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // Step 1: Get yesterday's snapshot for holdings
            List<PortfolioSnapshotModel> snaps = snapshotService.getHistory(userId, portfolioId, "1W");
            PortfolioSnapshotModel snap = snaps.stream()
                .filter(s -> s.getSnapshotDate() != null && !today.equals(s.getSnapshotDate()))
                .max(Comparator.comparing(PortfolioSnapshotModel::getSnapshotDate))
                .orElse(null);

            if (snap == null) {
                result.put("error", "No snapshot found for user " + userId);
                return ResponseEntity.badRequest().body(result);
            }

            // Step 2: Extract symbol quantities
            Map<String, Double> symbolQty = new HashMap<>();
            if (snap.getPortfolios() != null) {
                snap.getPortfolios().stream()
                    .filter(e -> portfolioId == null || portfolioId.equals(e.getPortfolioId()))
                    .forEach(e -> {
                        if (e.getHoldings() != null) {
                            e.getHoldings().forEach(h -> {
                                if (h.getSymbol() != null) {
                                    symbolQty.merge(h.getSymbol(),
                                        h.getQuantity() != null ? h.getQuantity() : 0.0, Double::sum);
                                }
                            });
                        }
                    });
            }

            if (symbolQty.isEmpty()) {
                result.put("error", "No holdings found in snapshot");
                return ResponseEntity.badRequest().body(result);
            }

            List<String> symbols = new ArrayList<>(symbolQty.keySet());

            // Step 3: Fetch 1D OHLC — PROVEN WORKING in live tests
            Map<String, MarketData> ohlcData = marketDataService.getOhlcData(symbols, "1D", false);

            if (ohlcData.isEmpty()) {
                result.put("error", "OHLC API returned no data. Market may be closed or symbols invalid.");
                return ResponseEntity.badRequest().body(result);
            }

            // Step 4: Compute baseline (yesterday close) and today close wealth
            double baselineWealth = 0.0;
            double closingWealth = 0.0;
            int priceHits = 0;

            for (Map.Entry<String, Double> sq : symbolQty.entrySet()) {
                String sym = sq.getKey();
                double qty = sq.getValue();
                MarketData md = ohlcData.get(sym);

                if (md != null) {
                    double prevClose = md.getPreviousClose() != null ? md.getPreviousClose() : 0.0;
                    double todayClose = md.getLastPrice() != null && md.getLastPrice() > 0 ? md.getLastPrice() :
                                       (md.getOhlc() != null && md.getOhlc().getClose() > 0 ?
                                        md.getOhlc().getClose() : prevClose);

                    if (prevClose <= 0) prevClose = todayClose;

                    baselineWealth += prevClose * qty;
                    closingWealth += todayClose * qty;
                    if (prevClose > 0 || todayClose > 0) priceHits++;
                }
            }

            if (baselineWealth <= 0) {
                result.put("error", "Could not compute baseline wealth. OHLC prices <= 0.");
                result.put("ohlcSymbolsFound", ohlcData.keySet());
                return ResponseEntity.badRequest().body(result);
            }

            // Step 5: Build a full 75-point intraday curve from 09:15 to 15:30 with realistic variation
            long totalSlots = java.time.temporal.ChronoUnit.MINUTES.between(MARKET_OPEN, MARKET_CLOSE) / 5; // 75 slots
            List<PortfolioIntradaySessionDocument.SessionDataPoint> points = new ArrayList<>();

            LocalTime t = MARKET_OPEN;
            int slotIndex = 0;

            while (!t.isAfter(MARKET_CLOSE)) {
                double progress = totalSlots > 0 ? (double) slotIndex / totalSlots : 1.0;
                // Easing curve with subtle realistic intraday wave so 09:40, 10:00, 11:00 differ
                double baseEased = progress * progress * (3.0 - 2.0 * progress);
                double wave = Math.sin(progress * Math.PI * 2.0) * 0.08 * (closingWealth - baselineWealth);
                
                double wealthAtSlot;
                if (slotIndex == 0) {
                    wealthAtSlot = baselineWealth;
                } else if (slotIndex == totalSlots || t.equals(MARKET_CLOSE)) {
                    wealthAtSlot = closingWealth;
                } else {
                    wealthAtSlot = baselineWealth + (closingWealth - baselineWealth) * baseEased + wave;
                }

                double chgFromOpen = wealthAtSlot - baselineWealth;
                double chgFromOpenPct = baselineWealth > 0 ? (chgFromOpen / baselineWealth) * 100.0 : 0.0;

                points.add(new PortfolioIntradaySessionDocument.SessionDataPoint(
                    t.format(TIME_FORMATTER),
                    wealthAtSlot,
                    chgFromOpen,
                    chgFromOpenPct
                ));

                t = t.plusMinutes(5);
                slotIndex++;
            }

            String pId = portfolioId != null ? portfolioId : "";
            PortfolioIntradaySessionDocument doc = PortfolioIntradaySessionDocument.builder()
                .userId(userId)
                .portfolioId(pId)
                .sessionDate(today)
                .baselineWealth(baselineWealth)
                .dataPoints(points)
                .createdAt(LocalDateTime.now())
                .build();

            // Upsert: update if today's record exists
            intradaySessionRepository
                .findFirstByUserIdAndPortfolioIdOrderBySessionDateDesc(userId, pId)
                .filter(existing -> today.equals(existing.getSessionDate()))
                .ifPresent(existing -> doc.setId(existing.getId()));

            intradaySessionRepository.save(doc);
            log.info("[DevAdmin] Seeded 75-point intraday session for userId={} portfolioId={} date={} " +
                     "baseline={} closing={} change={}%",
                     userId, pId, today, baselineWealth, closingWealth,
                     points.get(points.size() - 1).getChangeFromOpenPct());

            result.put("status", "seeded");
            result.put("userId", userId);
            result.put("portfolioId", pId.isEmpty() ? "aggregate" : pId);
            result.put("date", today.toString());
            result.put("pointsCount", points.size());
            result.put("baseline_09:15", baselineWealth);
            result.put("closing_15:30", closingWealth);
            result.put("changeFromOpen", closingWealth - baselineWealth);
            result.put("changeFromOpenPct", String.format("%.2f%%", points.get(points.size() - 1).getChangeFromOpenPct()));
            result.put("samplePoints", List.of(
                points.get(0),
                points.get(5),   // 09:40
                points.get(9),   // 10:00
                points.get(21),  // 11:00
                points.get(points.size() - 1) // 15:30
            ));
            result.put("message", "Seeded 75-point curve. Call GET /v1/portfolios/intraday now.");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[DevAdmin] Seed failed for userId={}", userId, e);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
