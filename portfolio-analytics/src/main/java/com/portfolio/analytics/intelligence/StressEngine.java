package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.StressRequest;
import com.portfolio.model.analytics.intelligence.StressResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scenario stress estimates: {@code Σ weightPct/100 * shockPct * betaProxy}.
 * No Mongo writes. Label always "Scenario estimate".
 */
@Component
public class StressEngine {

    public static final String ESTIMATE_LABEL = "Scenario estimate";

    public static final String PRESET_NIFTY_DOWN_10 = "NIFTY_DOWN_10";
    public static final String PRESET_NIFTY_DOWN_20 = "NIFTY_DOWN_20";
    public static final String PRESET_BANKING_DOWN_20 = "BANKING_DOWN_20";
    public static final String PRESET_IT_DOWN_15 = "IT_DOWN_15";
    public static final String PRESET_CRASH_2008 = "CRASH_2008";

    /** CRASH_2008 pack (API-CONTRACTS). */
    public static final double CRASH_BANKING_SHOCK = -35.0;
    public static final double CRASH_IT_SHOCK = -30.0;
    public static final double CRASH_RESIDUAL_SHOCK = -25.0;

    public StressResponse run(PortfolioIntelligenceSnapshot snapshot, StressRequest request) {
        List<StressResponse.ScenarioImpactDto> scenarios = new ArrayList<>();
        String id;
        if (request != null && request.getCustom() != null
                && request.getCustom().getSector() != null
                && !request.getCustom().getSector().isBlank()) {
            id = "CUSTOM_" + request.getCustom().getSector().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
            scenarios.add(impact(id, applySectorShock(snapshot, request.getCustom().getSector(),
                    request.getCustom().getShockPct())));
        } else {
            String preset = request != null && request.getPreset() != null
                    ? request.getPreset()
                    : PRESET_NIFTY_DOWN_10;
            id = preset;
            scenarios.add(impact(preset, applyPreset(snapshot, preset)));
        }

        return StressResponse.builder()
                .portfolioId(snapshot.getPortfolioId())
                .estimateLabel(ESTIMATE_LABEL)
                .scenarios(scenarios)
                .build();
    }

    private double applyPreset(PortfolioIntelligenceSnapshot snapshot, String preset) {
        double betaProxy = betaProxy(snapshot);
        return switch (preset) {
            case PRESET_NIFTY_DOWN_10 -> applyUniformShock(snapshot, -10.0, betaProxy);
            case PRESET_NIFTY_DOWN_20 -> applyUniformShock(snapshot, -20.0, betaProxy);
            case PRESET_BANKING_DOWN_20 -> applySectorShock(snapshot, "Banking", -20.0);
            case PRESET_IT_DOWN_15 -> applySectorShock(snapshot, "IT", -15.0);
            case PRESET_CRASH_2008 -> applyCrash2008(snapshot);
            default -> applyUniformShock(snapshot, -10.0, betaProxy);
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
            if (sectorMatches(h.getSector(), sector)) {
                pct += (h.getWeightPct() / 100.0) * shockPct * 1.0;
            }
        }
        return PortfolioIntelligenceSnapshotFactory.round2(pct);
    }

    private static double betaProxy(PortfolioIntelligenceSnapshot snapshot) {
        if (snapshot.getBeta() != null && snapshot.getBeta() > 0) {
            return snapshot.getBeta();
        }
        return 1.0;
    }

    private static boolean sectorMatches(String holdingSector, String target) {
        if (holdingSector == null || target == null) {
            return false;
        }
        String h = holdingSector.toLowerCase(Locale.ROOT);
        String t = target.toLowerCase(Locale.ROOT);
        if (matchesBanking(target) && matchesBanking(holdingSector)) {
            return true;
        }
        if (matchesIt(target) && matchesIt(holdingSector)) {
            return true;
        }
        return h.equals(t) || h.contains(t) || t.contains(h);
    }

    private static boolean matchesBanking(String sector) {
        if (sector == null) {
            return false;
        }
        String s = sector.toLowerCase(Locale.ROOT);
        return s.contains("bank") || s.contains("financial");
    }

    private static boolean matchesIt(String sector) {
        if (sector == null) {
            return false;
        }
        String s = sector.toLowerCase(Locale.ROOT);
        return s.equals("it") || s.contains("information technology") || s.contains(" technol")
                || s.startsWith("it ") || s.endsWith(" it") || s.contains("software");
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
                .build();
    }

    /** Fill absImpact using snapshot total value. */
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

    /** Available preset catalog for docs / debugging. */
    public static Map<String, String> presetCatalog() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(PRESET_NIFTY_DOWN_10, "Uniform -10% * betaProxy");
        m.put(PRESET_NIFTY_DOWN_20, "Uniform -20% * betaProxy");
        m.put(PRESET_BANKING_DOWN_20, "Banking/Financial -20%");
        m.put(PRESET_IT_DOWN_15, "IT -15%");
        m.put(PRESET_CRASH_2008, "Banking -35%, IT -30%, residual -25%");
        return m;
    }
}
