package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.HealthDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.portfolio.analytics.intelligence.HealthScoreConstants.*;

/**
 * Deterministic Health score from {@link PortfolioIntelligenceSnapshot} (E2E-BACKEND-PLAN §3–§5).
 */
@Component
public class HealthScoreEngine {

    public HealthDto compute(PortfolioIntelligenceSnapshot snapshot) {
        Map<String, ScoredComponent> scored = new LinkedHashMap<>();

        int div = roundInt(diversification(snapshot));
        scored.put(ID_DIVERSIFICATION, new ScoredComponent(div, MIX_DIVERSIFICATION,
                "Names " + snapshot.getHoldingsCount() + ", sectors " + snapshot.getDistinctSectors()));

        int conc = roundInt(concentration(snapshot));
        scored.put(ID_CONCENTRATION, new ScoredComponent(conc, MIX_CONCENTRATION,
                "Top1 " + snapshot.getTop1Pct() + "%, max sector " + snapshot.getMaxSectorPct() + "%"));

        boolean historyOk = snapshot.getHistoryPoints() >= MIN_HISTORY_POINTS;
        boolean includePerf = historyOk
                && snapshot.getPortRetPct() != null
                && snapshot.getNiftyRetPct() != null;
        if (includePerf) {
            double portRet = snapshot.getPortRetPct();
            double niftyRet = snapshot.getNiftyRetPct();
            int perf = roundInt(performance(portRet, niftyRet));
            scored.put(ID_PERFORMANCE, new ScoredComponent(perf, MIX_PERFORMANCE,
                    "Port " + portRet + "% vs NIFTY " + niftyRet + "%"));
        }

        Integer volScore = null;
        Integer betaScore = null;
        if (historyOk && snapshot.getDailyVolPct() != null) {
            double dailyVol = snapshot.getDailyVolPct();
            volScore = roundInt(volatility(dailyVol));
            scored.put(ID_VOLATILITY, new ScoredComponent(volScore, MIX_VOLATILITY,
                    "Daily vol " + dailyVol + "%"));
        }
        if (historyOk && snapshot.getBeta() != null) {
            double beta = snapshot.getBeta();
            betaScore = roundInt(betaScore(beta));
            scored.put(ID_BETA, new ScoredComponent(betaScore, MIX_BETA, "Beta " + beta));
        }

        int liq = roundInt(clamp(snapshot.getLiquidSharePct(), 0, 100));
        scored.put(ID_LIQUIDITY, new ScoredComponent(liq, MIX_LIQUIDITY,
                "Liquid share " + snapshot.getLiquidSharePct() + "%"));

        int alloc = roundInt(allocation(snapshot.getMaxSectorPct()));
        scored.put(ID_ALLOCATION, new ScoredComponent(alloc, MIX_ALLOCATION,
                "Max sector " + snapshot.getMaxSectorPct() + "%"));

        double volPart = volScore != null ? volScore : 70.0;
        double betaPart = betaScore != null ? betaScore : 70.0;
        int res = roundInt(clamp(0.5 * conc + 0.25 * volPart + 0.25 * betaPart, 0, 100));
        scored.put(ID_RISK_RESILIENCE, new ScoredComponent(res, MIX_RISK_RESILIENCE,
                "Blend of concentration / vol / beta"));

        double mixSum = scored.values().stream().mapToDouble(c -> c.mixWeight).sum();
        double healthRaw = 0;
        for (ScoredComponent c : scored.values()) {
            double w = mixSum > 0 ? c.mixWeight / mixSum : 0;
            healthRaw += c.score * w;
        }
        int health = roundInt(healthRaw);

        List<HealthDto.HealthComponentDto> components = new ArrayList<>();
        for (Map.Entry<String, ScoredComponent> e : scored.entrySet()) {
            components.add(HealthDto.HealthComponentDto.builder()
                    .id(e.getKey())
                    .score(e.getValue().score)
                    .severity(severityFor(e.getValue().score))
                    .reason(e.getValue().reason)
                    .build());
        }

        return HealthDto.builder()
                .score(health)
                .band(bandFor(health))
                .components(components)
                .build();
    }

    static double diversification(PortfolioIntelligenceSnapshot s) {
        double nameScore = Math.min(100, s.getHoldingsCount() * (double) NAME_KNOB);
        double sectorScore = Math.min(100, s.getDistinctSectors() * (double) SECTOR_KNOB);
        return clamp(0.5 * nameScore + 0.5 * sectorScore, 0, 100);
    }

    static double concentration(PortfolioIntelligenceSnapshot s) {
        return clamp(100 - 2 * s.getTop1Pct() - 1.5 * Math.max(0, s.getMaxSectorPct() - 20), 0, 100);
    }

    static double performance(double portRetPct, double niftyRetPct) {
        return clamp(50 + 5 * (portRetPct - niftyRetPct) + 2 * portRetPct, 0, 100);
    }

    static double volatility(double dailyVolPct) {
        return clamp(100 - dailyVolPct * VOL_MULT, 0, 100);
    }

    static double betaScore(double beta) {
        return clamp(100 - Math.abs(beta - 1) * 50 - Math.max(0, beta - 1.3) * 40, 0, 100);
    }

    static double allocation(double maxSectorPct) {
        return clamp(100 - 1.2 * Math.max(0, maxSectorPct - 25), 0, 100);
    }

    private static final class ScoredComponent {
        final int score;
        final double mixWeight;
        final String reason;

        ScoredComponent(int score, double mixWeight, String reason) {
            this.score = score;
            this.mixWeight = mixWeight;
            this.reason = reason;
        }
    }
}
