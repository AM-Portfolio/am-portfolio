package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.security.SecurityModel;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.model.analytics.intelligence.HealthDto;
import com.portfolio.model.analytics.intelligence.WhatIfRequest;
import com.portfolio.model.analytics.intelligence.WhatIfResponse;
import com.portfolio.model.util.SymbolResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stateless what-if on a cloned snapshot. No Mongo writes.
 */
@Component
@RequiredArgsConstructor
public class WhatIfEngine {

    public static final String MODE_ADD = "ADD_INVESTMENT";
    public static final String MODE_MODIFY = "MODIFY_HOLDING";
    public static final String MODE_SWITCH = "SWITCH_ALLOCATION";

    private final HealthScoreEngine healthScoreEngine;
    private final SecurityDetailsService securityDetailsService;

    public WhatIfResponse simulate(PortfolioIntelligenceSnapshot original, WhatIfRequest request) {
        if (request == null || request.getMode() == null || request.getMode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode is required");
        }
        String mode = request.getMode().trim().toUpperCase(Locale.ROOT);

        PortfolioIntelligenceSnapshot beforeSnap = original.deepCopy();
        PortfolioIntelligenceSnapshot afterSnap = original.deepCopy();

        switch (mode) {
            case MODE_ADD -> applyAdd(afterSnap, request);
            case MODE_MODIFY -> applyModify(afterSnap, request);
            case MODE_SWITCH -> applySwitch(afterSnap, request);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown what-if mode: " + mode);
        }

        afterSnap = PortfolioIntelligenceSnapshotFactory.finalizeSnapshot(
                afterSnap.getPortfolioId(),
                afterSnap.getHoldings(),
                afterSnap.getHistoryPoints(),
                afterSnap.getPortRetPct(),
                afterSnap.getNiftyRetPct(),
                afterSnap.getDailyVolPct(),
                afterSnap.getBeta());

        HealthDto beforeHealth = healthScoreEngine.compute(beforeSnap);
        HealthDto afterHealth = healthScoreEngine.compute(afterSnap);

        return WhatIfResponse.builder()
                .mode(mode)
                .before(compare(beforeSnap, beforeHealth))
                .after(compare(afterSnap, afterHealth))
                .build();
    }

    private void applyAdd(PortfolioIntelligenceSnapshot snap, WhatIfRequest request) {
        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required for ADD_INVESTMENT");
        }
        if (request.getAmountInr() == null || request.getAmountInr() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amountInr must be positive");
        }
        String symbol = request.getSymbol().trim().toUpperCase(Locale.ROOT);
        PortfolioIntelligenceSnapshot.Holding existing = findHolding(snap, symbol);
        if (existing != null) {
            existing.setValue(existing.getValue() + request.getAmountInr());
        } else {
            String sector = "Unknown";
            String industry = "Unknown";
            String marketCap = "UNKNOWN";
            try {
                Map<String, SecurityModel> details =
                        securityDetailsService.getSecurityDetails(List.of(SymbolResolver.normalize(symbol)));
                SecurityModel sec = details != null ? details.get(SymbolResolver.normalize(symbol)) : null;
                if (sec == null && details != null) {
                    for (Map.Entry<String, SecurityModel> e : details.entrySet()) {
                        if (e.getKey() != null && e.getKey().equalsIgnoreCase(symbol)) {
                            sec = e.getValue();
                            break;
                        }
                    }
                }
                if (sec != null && sec.getMetadata() != null) {
                    if (PortfolioIntelligenceSnapshotFactory.usableMeta(sec.getMetadata().getSector()) != null) {
                        sector = sec.getMetadata().getSector().trim();
                    }
                    if (PortfolioIntelligenceSnapshotFactory.usableMeta(sec.getMetadata().getIndustry()) != null) {
                        industry = sec.getMetadata().getIndustry().trim();
                    }
                    if (sec.getMetadata().getMarketCapType() != null) {
                        marketCap = sec.getMetadata().getMarketCapType().getName();
                    }
                }
            } catch (Exception ignored) {
                // fail-open: Unknown meta
            }
            snap.getHoldings().add(PortfolioIntelligenceSnapshot.Holding.builder()
                    .symbol(symbol)
                    .value(request.getAmountInr())
                    .weightPct(0)
                    .sector(sector)
                    .industry(industry)
                    .marketCap(marketCap)
                    .build());
        }
    }

    private void applyModify(PortfolioIntelligenceSnapshot snap, WhatIfRequest request) {
        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required for MODIFY_HOLDING");
        }
        if (request.getTargetWeightPct() == null || request.getTargetWeightPct() < 0 || request.getTargetWeightPct() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetWeightPct must be 0–100");
        }
        String symbol = request.getSymbol().trim().toUpperCase(Locale.ROOT);
        PortfolioIntelligenceSnapshot.Holding target = findHolding(snap, symbol);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Holding not found: " + symbol);
        }

        double total = snap.getHoldings().stream().mapToDouble(PortfolioIntelligenceSnapshot.Holding::getValue).sum();
        if (total <= 0) {
            return;
        }
        double targetValue = total * request.getTargetWeightPct() / 100.0;
        double othersTotal = total - target.getValue();
        target.setValue(targetValue);

        double remaining = total - targetValue;
        if (othersTotal > 0 && remaining >= 0) {
            for (PortfolioIntelligenceSnapshot.Holding h : snap.getHoldings()) {
                if (h == target) {
                    continue;
                }
                double share = h.getValue() / othersTotal;
                h.setValue(remaining * share);
            }
        }
    }

    private void applySwitch(PortfolioIntelligenceSnapshot snap, WhatIfRequest request) {
        if (request.getFromSector() == null || request.getToSector() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromSector and toSector are required");
        }
        if (request.getMoveWeightPct() == null || request.getMoveWeightPct() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "moveWeightPct must be positive");
        }
        double total = snap.getHoldings().stream().mapToDouble(PortfolioIntelligenceSnapshot.Holding::getValue).sum();
        if (total <= 0) {
            return;
        }
        double moveValue = total * request.getMoveWeightPct() / 100.0;

        List<PortfolioIntelligenceSnapshot.Holding> from = new ArrayList<>();
        List<PortfolioIntelligenceSnapshot.Holding> to = new ArrayList<>();
        for (PortfolioIntelligenceSnapshot.Holding h : snap.getHoldings()) {
            if (sectorEquals(h.getSector(), request.getFromSector())) {
                from.add(h);
            }
            if (sectorEquals(h.getSector(), request.getToSector())) {
                to.add(h);
            }
        }
        if (from.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No holdings in fromSector");
        }
        double fromTotal = from.stream().mapToDouble(PortfolioIntelligenceSnapshot.Holding::getValue).sum();
        if (fromTotal <= 0) {
            return;
        }
        moveValue = Math.min(moveValue, fromTotal);

        for (PortfolioIntelligenceSnapshot.Holding h : from) {
            double share = h.getValue() / fromTotal;
            h.setValue(h.getValue() - moveValue * share);
        }

        if (to.isEmpty()) {
            snap.getHoldings().add(PortfolioIntelligenceSnapshot.Holding.builder()
                    .symbol("SWITCH_" + request.getToSector().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT))
                    .value(moveValue)
                    .weightPct(0)
                    .sector(request.getToSector())
                    .industry(request.getToSector())
                    .marketCap("UNKNOWN")
                    .build());
        } else {
            double toTotal = to.stream().mapToDouble(PortfolioIntelligenceSnapshot.Holding::getValue).sum();
            if (toTotal <= 0) {
                double each = moveValue / to.size();
                for (PortfolioIntelligenceSnapshot.Holding h : to) {
                    h.setValue(h.getValue() + each);
                }
            } else {
                for (PortfolioIntelligenceSnapshot.Holding h : to) {
                    double share = h.getValue() / toTotal;
                    h.setValue(h.getValue() + moveValue * share);
                }
            }
        }
    }

    private static PortfolioIntelligenceSnapshot.Holding findHolding(PortfolioIntelligenceSnapshot snap, String symbol) {
        if (snap.getHoldings() == null) {
            return null;
        }
        for (PortfolioIntelligenceSnapshot.Holding h : snap.getHoldings()) {
            if (h.getSymbol() != null && h.getSymbol().equalsIgnoreCase(symbol)) {
                return h;
            }
        }
        return null;
    }

    private static boolean sectorEquals(String a, String b) {
        return SectorMatcher.matches(a, b);
    }

    private WhatIfResponse.SnapshotCompare compare(PortfolioIntelligenceSnapshot snap, HealthDto health) {
        Map<String, Double> weights = new LinkedHashMap<>();
        Map<String, Double> sectorWeights = new LinkedHashMap<>();
        if (snap.getHoldings() != null) {
            for (PortfolioIntelligenceSnapshot.Holding h : snap.getHoldings()) {
                weights.put(h.getSymbol(), h.getWeightPct());
                if (h.getSector() != null) {
                    sectorWeights.merge(h.getSector(), h.getWeightPct(), Double::sum);
                }
            }
        }
        for (Map.Entry<String, Double> e : sectorWeights.entrySet()) {
            e.setValue(PortfolioIntelligenceSnapshotFactory.round2(e.getValue()));
        }
        return WhatIfResponse.SnapshotCompare.builder()
                .healthScore(health.getScore())
                .weights(weights)
                .sectorWeights(sectorWeights)
                .build();
    }
}
