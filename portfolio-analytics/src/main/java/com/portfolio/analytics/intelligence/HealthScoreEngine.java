package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.HealthDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.portfolio.analytics.intelligence.HealthScoreConstants.*;

/**
 * Deterministic Health score from {@link PortfolioIntelligenceSnapshot}.
 * Legacy count-based diversification when {@code health-v2=false}; industry A/B/C + ann. vol when true.
 */
@Component
public class HealthScoreEngine {

    static final String ASSET_EQUITY = "EQUITY";
    static final String ASSET_FIXED_INCOME = "FIXED_INCOME";
    static final String ASSET_COMMODITY = "COMMODITY";
    static final String ASSET_CASH = "CASH";
    static final String ASSET_MUTUAL_FUND = "MUTUAL_FUND";

    @Value("${portfolio.intelligence.health-v2:false}")
    private boolean healthV2;

    /** Test / programmatic override. */
    public void setHealthV2(boolean healthV2) {
        this.healthV2 = healthV2;
    }

    public boolean isHealthV2() {
        return healthV2;
    }

    public HealthDto compute(PortfolioIntelligenceSnapshot snapshot) {
        Map<String, ScoredComponent> scored = new LinkedHashMap<>();

        DivResult divResult = scoreDiversification(snapshot);
        scored.put(ID_DIVERSIFICATION, new ScoredComponent(divResult.score, MIX_DIVERSIFICATION, divResult.reason));

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
        VolResult volResult = scoreVolatility(snapshot, historyOk);
        if (volResult != null) {
            volScore = volResult.score;
            scored.put(ID_VOLATILITY, new ScoredComponent(volResult.score, MIX_VOLATILITY, volResult.reason));
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
        String resReason = healthV2 && volResult != null && volResult.insufficient
                ? "Blend of concentration / vol (insufficient hist) / beta"
                : "Blend of concentration / vol / beta";
        scored.put(ID_RISK_RESILIENCE, new ScoredComponent(res, MIX_RISK_RESILIENCE, resReason));

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

    private DivResult scoreDiversification(PortfolioIntelligenceSnapshot snapshot) {
        if (healthV2) {
            double score = diversificationV2(snapshot);
            double neff = effectiveN(snapshot);
            int funded = fundedClassCount(classWeights(snapshot));
            return new DivResult(roundInt(score),
                    String.format(Locale.ROOT, "Classes %d • effN %.1f • top1 %.1f%%",
                            Math.max(funded, 1), neff, snapshot.getTop1Pct()));
        }
        double legacy = diversificationLegacy(snapshot);
        return new DivResult(roundInt(legacy),
                "Names " + snapshot.getHoldingsCount() + ", sectors " + snapshot.getDistinctSectors());
    }

    private VolResult scoreVolatility(PortfolioIntelligenceSnapshot snapshot, boolean historyOk) {
        if (healthV2) {
            if (historyOk && snapshot.getDailyVolPct() != null) {
                double ann = annualizedVolPct(snapshot.getDailyVolPct());
                int score = roundInt(volatilityFromAnnPct(ann));
                return new VolResult(score,
                        String.format(Locale.ROOT, "Ann. vol %.1f%% (measured)", ann),
                        false);
            }
            return new VolResult(55, "Insufficient history (need ≥20 days)", true);
        }
        if (historyOk && snapshot.getDailyVolPct() != null) {
            double dailyVol = snapshot.getDailyVolPct();
            return new VolResult(roundInt(volatility(dailyVol)), "Daily vol " + dailyVol + "%", false);
        }
        return null;
    }

    /** Legacy count formula (health-v2=false). Also used by RiskRadar when flag off. */
    static double diversification(PortfolioIntelligenceSnapshot s) {
        return diversificationLegacy(s);
    }

    static double diversificationLegacy(PortfolioIntelligenceSnapshot s) {
        double nameScore = Math.min(100, s.getHoldingsCount() * (double) NAME_KNOB);
        double sectorScore = Math.min(100, s.getDistinctSectors() * (double) SECTOR_KNOB);
        return clamp(0.5 * nameScore + 0.5 * sectorScore, 0, 100);
    }

    /** Industry A/B/C diversification (health-v2=true). */
    static double diversificationV2(PortfolioIntelligenceSnapshot s) {
        Map<String, Double> classes = classWeights(s);
        double a = classMixScore(classes);
        double b = withinEquityScore(s);
        double c = concentration(s);
        return clamp(0.40 * a + 0.35 * b + 0.25 * c, 0, 100);
    }

    static Map<String, Double> classWeights(PortfolioIntelligenceSnapshot s) {
        Map<String, Double> raw = new HashMap<>();
        List<PortfolioIntelligenceSnapshot.Holding> holdings = s.getHoldings();
        if (holdings == null || holdings.isEmpty()) {
            raw.put(ASSET_EQUITY, 1.0);
            return raw;
        }
        double sum = 0;
        for (PortfolioIntelligenceSnapshot.Holding h : holdings) {
            String cls = normalizeAssetClass(h.getAssetClass());
            double w = Math.max(0, h.getWeightPct()) / 100.0;
            raw.merge(cls, w, Double::sum);
            sum += w;
        }
        if (sum <= 0) {
            raw.clear();
            raw.put(ASSET_EQUITY, 1.0);
            return raw;
        }
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> e : raw.entrySet()) {
            double w = e.getValue() / sum;
            if (w >= 0.02) {
                normalized.put(e.getKey(), w);
            }
        }
        if (normalized.isEmpty()) {
            normalized.put(ASSET_EQUITY, 1.0);
        }
        return normalized;
    }

    static String normalizeAssetClass(String assetClass) {
        if (assetClass == null || assetClass.isBlank()) {
            return ASSET_EQUITY;
        }
        String u = assetClass.trim().toUpperCase(Locale.ROOT);
        if (u.contains("BOND") || u.contains("DEBT") || u.equals(ASSET_FIXED_INCOME) || u.equals("FIXED_INCOME")) {
            return ASSET_FIXED_INCOME;
        }
        if (u.contains("GOLD") || u.contains("SILVER") || u.contains("METAL") || u.equals(ASSET_COMMODITY)) {
            return ASSET_COMMODITY;
        }
        if (u.contains("CASH")) {
            return ASSET_CASH;
        }
        if (u.contains("MF") || u.contains("MUTUAL") || u.contains("ETF") || u.equals(ASSET_MUTUAL_FUND)) {
            return ASSET_MUTUAL_FUND;
        }
        return ASSET_EQUITY;
    }

    static double classMixScore(Map<String, Double> classes) {
        int funded = fundedClassCount(classes);
        if (funded <= 1) {
            return 50.0;
        }
        double hhi = 0;
        for (double w : classes.values()) {
            hhi += w * w;
        }
        int kCap = Math.min(funded, 5);
        double denom = 1.0 - (1.0 / kCap);
        if (denom <= 0) {
            return 50.0;
        }
        return clamp(100.0 * (1.0 - hhi) / denom, 0, 100);
    }

    static int fundedClassCount(Map<String, Double> classes) {
        int n = 0;
        for (double w : classes.values()) {
            if (w >= 0.05) {
                n++;
            }
        }
        return n;
    }

    static double withinEquityScore(PortfolioIntelligenceSnapshot s) {
        Map<String, Double> classes = classWeights(s);
        double equityW = classes.getOrDefault(ASSET_EQUITY, 0.0)
                + classes.getOrDefault(ASSET_MUTUAL_FUND, 0.0);
        if (equityW <= 0) {
            return 70.0;
        }
        List<PortfolioIntelligenceSnapshot.Holding> equityHoldings = new ArrayList<>();
        for (PortfolioIntelligenceSnapshot.Holding h : s.getHoldings()) {
            String cls = normalizeAssetClass(h.getAssetClass());
            if (ASSET_EQUITY.equals(cls) || ASSET_MUTUAL_FUND.equals(cls)) {
                equityHoldings.add(h);
            }
        }
        if (equityHoldings.isEmpty()) {
            return 70.0;
        }
        double sum = 0;
        for (PortfolioIntelligenceSnapshot.Holding h : equityHoldings) {
            sum += Math.max(0, h.getWeightPct());
        }
        if (sum <= 0) {
            return 70.0;
        }
        double hhiNames = 0;
        Map<String, Double> sectorW = new HashMap<>();
        for (PortfolioIntelligenceSnapshot.Holding h : equityHoldings) {
            double w = Math.max(0, h.getWeightPct()) / sum;
            hhiNames += w * w;
            String sector = h.getSector() == null || h.getSector().isBlank() ? "Unknown" : h.getSector();
            sectorW.merge(sector, w, Double::sum);
        }
        double nEff = hhiNames > 0 ? 1.0 / hhiNames : 1.0;
        double nScore = clamp(20 + 70 * Math.log(Math.max(nEff, 1.0)) / Math.log(20), 20, 90);
        double sectorHhi = 0;
        for (double w : sectorW.values()) {
            sectorHhi += w * w;
        }
        double secScore = clamp(100.0 * (1.0 - sectorHhi) / 0.9, 20, 95);
        return 0.55 * nScore + 0.45 * secScore;
    }

    static double effectiveN(PortfolioIntelligenceSnapshot s) {
        List<PortfolioIntelligenceSnapshot.Holding> holdings = s.getHoldings();
        if (holdings == null || holdings.isEmpty()) {
            return 1;
        }
        double sum = 0;
        for (PortfolioIntelligenceSnapshot.Holding h : holdings) {
            sum += Math.max(0, h.getWeightPct());
        }
        if (sum <= 0) {
            return 1;
        }
        double hhi = 0;
        for (PortfolioIntelligenceSnapshot.Holding h : holdings) {
            double w = Math.max(0, h.getWeightPct()) / sum;
            hhi += w * w;
        }
        return hhi > 0 ? 1.0 / hhi : 1;
    }

    /**
     * {@code dailyVolPct} from hist metrics = sampleStdev(daily returns) × 100.
     */
    static double annualizedVolPct(double dailyVolPct) {
        double sigmaDaily = dailyVolPct / 100.0;
        return 100.0 * sigmaDaily * Math.sqrt(252.0);
    }

    static double volatilityFromAnnPct(double annVolPct) {
        return clamp(100.0 - 1.5 * Math.max(0, annVolPct - 8), 15, 95);
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

    private static final class DivResult {
        final int score;
        final String reason;

        DivResult(int score, String reason) {
            this.score = score;
            this.reason = reason;
        }
    }

    private static final class VolResult {
        final int score;
        final String reason;
        final boolean insufficient;

        VolResult(int score, String reason, boolean insufficient) {
            this.score = score;
            this.reason = reason;
            this.insufficient = insufficient;
        }
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
