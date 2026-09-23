package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.StressRequest;
import com.portfolio.model.analytics.intelligence.StressResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scenario stress estimates: {@code Σ weightPct/100 * shockPct * betaProxy}.
 * No Mongo writes. Label always "Scenario estimate".
 */
@Component
public class StressEngine {

    public static final String ESTIMATE_LABEL = "Scenario estimate";

    public static final String PRESET_NIFTY_DOWN_10 = "NIFTY_DOWN_10";
    public static final String PRESET_NIFTY_DOWN_20 = "NIFTY_DOWN_20";
    public static final String PRESET_SENSEX_DOWN_10 = "SENSEX_DOWN_10";
    public static final String PRESET_SENSEX_DOWN_20 = "SENSEX_DOWN_20";
    public static final String PRESET_BANKING_DOWN_20 = "BANKING_DOWN_20";
    public static final String PRESET_IT_DOWN_15 = "IT_DOWN_15";
    public static final String PRESET_AUTO_DOWN_20 = "AUTO_DOWN_20";
    public static final String PRESET_PHARMA_DOWN_15 = "PHARMA_DOWN_15";
    public static final String PRESET_ENERGY_DOWN_20 = "ENERGY_DOWN_20";
    public static final String PRESET_CRASH_2008 = "CRASH_2008";

    public static final Set<String> KNOWN_PRESETS = Set.of(
            PRESET_NIFTY_DOWN_10,
            PRESET_NIFTY_DOWN_20,
            PRESET_SENSEX_DOWN_10,
            PRESET_SENSEX_DOWN_20,
            PRESET_BANKING_DOWN_20,
            PRESET_IT_DOWN_15,
            PRESET_AUTO_DOWN_20,
            PRESET_PHARMA_DOWN_15,
            PRESET_ENERGY_DOWN_20,
            PRESET_CRASH_2008);

    /** CRASH_2008 pack (API-CONTRACTS). */
    public static final double CRASH_BANKING_SHOCK = -35.0;
    public static final double CRASH_IT_SHOCK = -30.0;
    public static final double CRASH_RESIDUAL_SHOCK = -25.0;

    public StressResponse run(PortfolioIntelligenceSnapshot snapshot, StressRequest request) {
        List<StressResponse.ScenarioImpactDto> scenarios = new ArrayList<>();

        if (request != null && request.getCustom() != null
                && request.getCustom().getSector() != null
                && !request.getCustom().getSector().isBlank()) {
            Double shockPct = request.getCustom().getShockPct();
            if (shockPct == null || !Double.isFinite(shockPct) || shockPct == 0.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "custom.shockPct is required and must be non-zero");
            }
            String sector = request.getCustom().getSector().trim();
            String id = "CUSTOM_" + sector.replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
            double matchedWeight = 0;
            int matchedHoldings = 0;
            if (snapshot.getHoldings() != null) {
                for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
                    if (SectorMatcher.matches(h.getSector(), sector)) {
                        matchedWeight += h.getWeightPct();
                        matchedHoldings++;
                    }
                }
            }
            matchedWeight = PortfolioIntelligenceSnapshotFactory.round2(matchedWeight);
            double pct = applySectorShock(snapshot, sector, shockPct);
            String note = matchedHoldings == 0
                    ? "No holdings match this sector — impact 0%"
                    : String.format(Locale.ROOT,
                            "shock %+.0f%% on %d holdings (%.1f%% of book)",
                            shockPct, matchedHoldings, matchedWeight);
            scenarios.add(StressResponse.ScenarioImpactDto.builder()
                    .id(id)
                    .pctImpact(pct)
                    .absImpact(0) // filled in finalizeAbs
                    .matchedWeightPct(matchedWeight)
                    .matchedHoldings(matchedHoldings)
                    .appliedShockPct(shockPct)
                    .note(note)
                    .build());
        } else if (request != null && request.getPresets() != null && !request.getPresets().isEmpty()) {
            for (String raw : request.getPresets()) {
                if (raw == null || raw.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presets contains blank id");
                }
                String preset = raw.trim().toUpperCase(Locale.ROOT);
                requireKnownPreset(preset);
                scenarios.add(impact(preset, applyPreset(snapshot, preset)));
            }
        } else {
            String preset = request != null && request.getPreset() != null && !request.getPreset().isBlank()
                    ? request.getPreset().trim().toUpperCase(Locale.ROOT)
                    : PRESET_NIFTY_DOWN_10;
            requireKnownPreset(preset);
            scenarios.add(impact(preset, applyPreset(snapshot, preset)));
        }

        BetaMeta meta = resolveBetaMeta(snapshot);
        return StressResponse.builder()
                .portfolioId(snapshot.getPortfolioId())
                .estimateLabel(ESTIMATE_LABEL)
                .scenarios(scenarios)
                .method(meta.method)
                .betaUsed(meta.betaUsed)
                .benchmark(meta.benchmark)
                .historyDays(meta.historyDays)
                .betaAssumed(meta.betaAssumed)
                .build();
    }

    private record BetaMeta(
            String method,
            Double betaUsed,
            String benchmark,
            Integer historyDays,
            boolean betaAssumed) {
    }

    private static BetaMeta resolveBetaMeta(PortfolioIntelligenceSnapshot snapshot) {
        int historyDays = snapshot.getHistoryPoints();
        Double beta = snapshot.getBeta();
        boolean measured = beta != null && Double.isFinite(beta) && historyDays >= 20;
        if (measured) {
            return new BetaMeta(
                    "PORTFOLIO_BETA",
                    PortfolioIntelligenceSnapshotFactory.round2(beta),
                    "NIFTY50",
                    historyDays,
                    false);
        }
        return new BetaMeta("ASSUMED_ONE", 1.0, "NIFTY50", historyDays, true);
    }

    private static void requireKnownPreset(String preset) {
        if (!KNOWN_PRESETS.contains(preset)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown stress preset: " + preset);
        }
    }

    private double applyPreset(PortfolioIntelligenceSnapshot snapshot, String preset) {
        double betaProxy = betaProxy(snapshot);
        return switch (preset) {
            case PRESET_NIFTY_DOWN_10, PRESET_SENSEX_DOWN_10 -> applyUniformShock(snapshot, -10.0, betaProxy);
            case PRESET_NIFTY_DOWN_20, PRESET_SENSEX_DOWN_20 -> applyUniformShock(snapshot, -20.0, betaProxy);
            case PRESET_BANKING_DOWN_20 -> applySectorShock(snapshot, "Banking", -20.0);
            case PRESET_IT_DOWN_15 -> applySectorShock(snapshot, "IT", -15.0);
            case PRESET_AUTO_DOWN_20 -> applySectorShock(snapshot, "Auto", -20.0);
            case PRESET_PHARMA_DOWN_15 -> applySectorShock(snapshot, "Pharma", -15.0);
            case PRESET_ENERGY_DOWN_20 -> applySectorShock(snapshot, "Energy", -20.0);
            case PRESET_CRASH_2008 -> applyCrash2008(snapshot);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown stress preset: " + preset);
        };
    }

    private double applyCrash2008(PortfolioIntelligenceSnapshot snapshot) {
        double pct = 0;
        if (snapshot.getHoldings() == null) {
            return 0;
        }
        for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
            double shock;
            if (matchesBanking(h.getSector())) {
                shock = CRASH_BANKING_SHOCK;
            } else if (matchesIt(h.getSector())) {
                shock = CRASH_IT_SHOCK;
            } else {
                shock = CRASH_RESIDUAL_SHOCK;
            }
            pct += (h.getWeightPct() / 100.0) * shock * 1.0;
        }
        return PortfolioIntelligenceSnapshotFactory.round2(pct);
    }

    private double applyUniformShock(PortfolioIntelligenceSnapshot snapshot, double shockPct, double betaProxy) {
        double pct = 0;
        if (snapshot.getHoldings() == null) {
            return 0;
        }
        for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
            pct += (h.getWeightPct() / 100.0) * shockPct * betaProxy;
        }
        return PortfolioIntelligenceSnapshotFactory.round2(pct);
    }

    private double applySectorShock(PortfolioIntelligenceSnapshot snapshot, String sector, double shockPct) {
        double pct = 0;
        if (snapshot.getHoldings() == null) {
            return 0;
        }
        for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
            if (SectorMatcher.matches(h.getSector(), sector)) {
                pct += (h.getWeightPct() / 100.0) * shockPct * 1.0;
            }
        }
        return PortfolioIntelligenceSnapshotFactory.round2(pct);
    }

    /** Same gate as {@link #resolveBetaMeta}: index shocks use 1.0 unless β is measured. */
    private static double betaProxy(PortfolioIntelligenceSnapshot snapshot) {
        BetaMeta meta = resolveBetaMeta(snapshot);
        return meta.betaUsed != null ? meta.betaUsed : 1.0;
    }

    /** @deprecated use {@link SectorMatcher#matches} */
    static boolean sectorMatches(String holdingSector, String target) {
        return SectorMatcher.matches(holdingSector, target);
    }

    /** Banking / Financial Services / Finance aliases aligned with X-Ray labels. */
    static boolean matchesBanking(String sector) {
        return SectorMatcher.matchesBanking(sector);
    }

    /** IT / Information Technology / Software aliases. */
    static boolean matchesIt(String sector) {
        return SectorMatcher.matchesIt(sector);
    }

    static boolean matchesAuto(String sector) {
        return SectorMatcher.matchesAuto(sector);
    }

    static boolean matchesPharma(String sector) {
        return SectorMatcher.matchesPharma(sector);
    }

    static boolean matchesEnergy(String sector) {
        return SectorMatcher.matchesEnergy(sector);
    }

    private StressResponse.ScenarioImpactDto impact(String id, double pctImpact) {
        return StressResponse.ScenarioImpactDto.builder()
                .id(id)
                .pctImpact(pctImpact)
                .absImpact(0)
                .build();
    }

    public StressResponse.ScenarioImpactDto withAbs(StressResponse.ScenarioImpactDto dto, double totalValue) {
        double abs = PortfolioIntelligenceSnapshotFactory.round2(totalValue * dto.getPctImpact() / 100.0);
        return StressResponse.ScenarioImpactDto.builder()
                .id(dto.getId())
                .pctImpact(dto.getPctImpact())
                .absImpact(abs)
                .matchedWeightPct(dto.getMatchedWeightPct())
                .matchedHoldings(dto.getMatchedHoldings())
                .appliedShockPct(dto.getAppliedShockPct())
                .note(dto.getNote())
                .build();
    }

    public StressResponse finalizeAbs(StressResponse response, double totalValue) {
        if (response.getScenarios() == null) {
            return response;
        }
        List<StressResponse.ScenarioImpactDto> filled = new ArrayList<>();
        for (StressResponse.ScenarioImpactDto s : response.getScenarios()) {
            filled.add(withAbs(s, totalValue));
        }
        response.setScenarios(filled);
        return response;
    }

    public static Map<String, String> presetCatalog() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(PRESET_NIFTY_DOWN_10, "Uniform -10% * betaProxy");
        m.put(PRESET_NIFTY_DOWN_20, "Uniform -20% * betaProxy");
        m.put(PRESET_SENSEX_DOWN_10, "Uniform -10% * betaProxy (Sensex label)");
        m.put(PRESET_SENSEX_DOWN_20, "Uniform -20% * betaProxy (Sensex label)");
        m.put(PRESET_BANKING_DOWN_20, "Banking/Financial -20%");
        m.put(PRESET_IT_DOWN_15, "IT -15%");
        m.put(PRESET_AUTO_DOWN_20, "Auto -20%");
        m.put(PRESET_PHARMA_DOWN_15, "Pharma -15%");
        m.put(PRESET_ENERGY_DOWN_20, "Energy -20%");
        m.put(PRESET_CRASH_2008, "Banking -35%, IT -30%, residual -25%");
        return m;
    }
}
