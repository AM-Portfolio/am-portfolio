package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.StressRequest;
import com.portfolio.model.analytics.intelligence.StressResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scenario stress estimates. Index/event rows: {@code equityWeight × shock × β}
 * vs NIFTY 50 (cash/MF/bonds excluded). Sector rows ignore β.
 * No Mongo writes. Label always "Scenario estimate".
 *
 * Configurable dynamically via {@link StressProperties} or YAML.
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
    /** Uniform NIFTY-style catalog events (locked MVP market-fall %). */
    public static final String PRESET_GFC_2008 = "GFC_2008";
    public static final String PRESET_COVID_2020 = "COVID_2020";
    public static final String PRESET_CHINA_2015 = "CHINA_2015";
    public static final String PRESET_INFLATION_2022 = "INFLATION_2022";
    public static final String PRESET_ELECTION_2024 = "ELECTION_2024";

    public static final double SHOCK_GFC_2008 = -56.0;
    public static final double SHOCK_COVID_2020 = -38.0;
    public static final double SHOCK_CHINA_2015 = -28.0;
    public static final double SHOCK_INFLATION_2022 = -26.0;
    public static final double SHOCK_ELECTION_2024 = -18.0;

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
            PRESET_CRASH_2008,
            PRESET_GFC_2008,
            PRESET_COVID_2020,
            PRESET_CHINA_2015,
            PRESET_INFLATION_2022,
            PRESET_ELECTION_2024);

    /** CRASH_2008 pack (API-CONTRACTS). */
    public static final double CRASH_BANKING_SHOCK = -35.0;
    public static final double CRASH_IT_SHOCK = -30.0;
    public static final double CRASH_RESIDUAL_SHOCK = -25.0;

    private static final Map<String, StressProperties.ScenarioConfig> DEFAULT_SCENARIOS = new LinkedHashMap<>();

    static {
        DEFAULT_SCENARIOS.put(PRESET_NIFTY_DOWN_10, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.UNIFORM)
                .shockPct(-10.0)
                .description("Uniform -10% * betaProxy")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_NIFTY_DOWN_20, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.UNIFORM)
                .shockPct(-20.0)
                .description("Uniform -20% * betaProxy")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_SENSEX_DOWN_10, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.UNIFORM)
                .shockPct(-10.0)
                .description("Uniform -10% * betaProxy (Sensex label)")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_SENSEX_DOWN_20, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.UNIFORM)
                .shockPct(-20.0)
                .description("Uniform -20% * betaProxy (Sensex label)")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_BANKING_DOWN_20, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.SECTOR)
                .targetSector("Banking")
                .shockPct(-20.0)
                .description("Banking/Financial -20%")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_IT_DOWN_15, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.SECTOR)
                .targetSector("IT")
                .shockPct(-15.0)
                .description("IT -15%")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_AUTO_DOWN_20, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.SECTOR)
                .targetSector("Auto")
                .shockPct(-20.0)
                .description("Auto -20%")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_PHARMA_DOWN_15, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.SECTOR)
                .targetSector("Pharma")
                .shockPct(-15.0)
                .description("Pharma -15%")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_ENERGY_DOWN_20, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.SECTOR)
                .targetSector("Energy")
                .shockPct(-20.0)
                .description("Energy -20%")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_CRASH_2008, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.MULTI_SECTOR)
                .description("Banking -35%, IT -30%, residual -25%")
                .residualShock(CRASH_RESIDUAL_SHOCK)
                .sectorShocks(Map.of("Banking", CRASH_BANKING_SHOCK, "IT", CRASH_IT_SHOCK))
                .build());
        DEFAULT_SCENARIOS.put(PRESET_GFC_2008, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.UNIFORM)
                .shockPct(SHOCK_GFC_2008)
                .description("Uniform -56% * betaProxy (GFC Oct 2008)")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_COVID_2020, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.UNIFORM)
                .shockPct(SHOCK_COVID_2020)
                .description("Uniform -38% * betaProxy (COVID Mar 2020)")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_CHINA_2015, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.UNIFORM)
                .shockPct(SHOCK_CHINA_2015)
                .description("Uniform -28% * betaProxy (China Aug 2015)")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_INFLATION_2022, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.UNIFORM)
                .shockPct(SHOCK_INFLATION_2022)
                .description("Uniform -26% * betaProxy (Inflation Jan 2022)")
                .build());
        DEFAULT_SCENARIOS.put(PRESET_ELECTION_2024, StressProperties.ScenarioConfig.builder()
                .type(StressProperties.ScenarioType.UNIFORM)
                .shockPct(SHOCK_ELECTION_2024)
                .description("Uniform -18% * betaProxy (Election Jun 2024)")
                .build());
    }

    private final Map<String, StressProperties.ScenarioConfig> scenarios = new ConcurrentHashMap<>();

    public StressEngine() {
        this(null);
    }

    @Autowired(required = false)
    public StressEngine(StressProperties properties) {
        initScenarios(properties);
    }

    private void initScenarios(StressProperties properties) {
        this.scenarios.putAll(DEFAULT_SCENARIOS);
        if (properties != null) {
            if (properties.getScenarios() != null && !properties.getScenarios().isEmpty()) {
                properties.getScenarios().forEach((k, v) -> {
                    if (k != null && v != null) {
                        this.scenarios.put(k.trim().toUpperCase(Locale.ROOT), v);
                    }
                });
            }
            if (properties.getRawIndices() != null && !properties.getRawIndices().isEmpty()) {
                IndexEtfSleeves.registerRawIndex(properties.getRawIndices());
            }
            if (properties.getBroadNiftyEtfs() != null && !properties.getBroadNiftyEtfs().isEmpty()) {
                IndexEtfSleeves.registerBroadNiftyEtf(properties.getBroadNiftyEtfs());
            }
        }
    }

    public Map<String, StressProperties.ScenarioConfig> getScenarios() {
        return Collections.unmodifiableMap(scenarios);
    }

    public StressResponse run(PortfolioIntelligenceSnapshot snapshot, StressRequest request) {
        List<StressResponse.ScenarioImpactDto> scenarioImpacts = new ArrayList<>();

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
                    if (takesSectorShock(h, sector)) {
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
            scenarioImpacts.add(StressResponse.ScenarioImpactDto.builder()
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
                scenarioImpacts.add(impact(preset, applyPreset(snapshot, preset), catalogShockPct(preset)));
            }
        } else {
            String preset = request != null && request.getPreset() != null && !request.getPreset().isBlank()
                    ? request.getPreset().trim().toUpperCase(Locale.ROOT)
                    : PRESET_NIFTY_DOWN_10;
            requireKnownPreset(preset);
            scenarioImpacts.add(impact(preset, applyPreset(snapshot, preset), catalogShockPct(preset)));
        }

        BetaMeta meta = resolveBetaMeta(snapshot);
        return StressResponse.builder()
                .portfolioId(snapshot.getPortfolioId())
                .estimateLabel(ESTIMATE_LABEL)
                .scenarios(scenarioImpacts)
                .method(meta.method)
                .betaUsed(meta.betaUsed)
                .benchmark(meta.benchmark)
                .historyDays(meta.historyDays)
                .betaAssumed(meta.betaAssumed)
                .equityWeightPct(equityWeightPct(snapshot))
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

    private void requireKnownPreset(String preset) {
        if (!scenarios.containsKey(preset) && !KNOWN_PRESETS.contains(preset)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown stress preset: " + preset);
        }
    }

    private double applyPreset(PortfolioIntelligenceSnapshot snapshot, String preset) {
        StressProperties.ScenarioConfig config = scenarios.get(preset);
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown stress preset: " + preset);
        }
        double betaProxy = betaProxy(snapshot);
        StressProperties.ScenarioType type = config.getType() != null
                ? config.getType()
                : StressProperties.ScenarioType.UNIFORM;
        return switch (type) {
            case UNIFORM -> {
                double shock = config.getShockPct() != null ? config.getShockPct() : 0.0;
                yield applyUniformShock(snapshot, shock, betaProxy);
            }
            case SECTOR -> {
                String sector = config.getTargetSector() != null ? config.getTargetSector() : "";
                double shock = config.getShockPct() != null ? config.getShockPct() : 0.0;
                yield applySectorShock(snapshot, sector, shock);
            }
            case MULTI_SECTOR -> applyMultiSectorShock(snapshot, config);
        };
    }

    /** Uniform/sector catalog shock for {@code appliedShockPct}. Null for multi-sector packs like {@link #PRESET_CRASH_2008}. */
    Double catalogShockPct(String preset) {
        StressProperties.ScenarioConfig config = scenarios.get(preset);
        if (config != null) {
            return config.getShockPct();
        }
        return defaultCatalogShockPct(preset);
    }

    public static Double defaultCatalogShockPct(String preset) {
        StressProperties.ScenarioConfig config = DEFAULT_SCENARIOS.get(preset);
        return config != null ? config.getShockPct() : null;
    }

    private double applyMultiSectorShock(
            PortfolioIntelligenceSnapshot snapshot,
            StressProperties.ScenarioConfig config) {
        double pct = 0;
        if (snapshot.getHoldings() == null) {
            return 0;
        }
        double residualShock = config.getResidualShock() != null ? config.getResidualShock() : CRASH_RESIDUAL_SHOCK;
        Map<String, Double> sectorShocks = config.getSectorShocks();
        for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
            if (!isEquitySleeve(h)) {
                continue;
            }
            double shock = residualShock;
            if (IndexEtfSleeves.isBroadNiftyEtf(h.getSymbol())) {
                shock = residualShock;
            } else if (sectorShocks != null && !sectorShocks.isEmpty()) {
                boolean matched = false;
                for (Map.Entry<String, Double> entry : sectorShocks.entrySet()) {
                    if (SectorMatcher.matches(h.getSector(), entry.getKey())) {
                        shock = entry.getValue();
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    shock = residualShock;
                }
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
            if (!isEquitySleeve(h)) {
                continue;
            }
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
            if (takesSectorShock(h, sector)) {
                pct += (h.getWeightPct() / 100.0) * shockPct * 1.0;
            }
        }
        return PortfolioIntelligenceSnapshotFactory.round2(pct);
    }

    static boolean isEquitySleeve(PortfolioIntelligenceSnapshot.Holding h) {
        if (h == null) {
            return false;
        }
        return HealthScoreEngine.ASSET_EQUITY.equals(
                HealthScoreEngine.normalizeAssetClass(h.getAssetClass()));
    }

    static double equityWeightPct(PortfolioIntelligenceSnapshot snapshot) {
        if (snapshot == null || snapshot.getHoldings() == null) {
            return 0.0;
        }
        double w = 0.0;
        for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
            if (isEquitySleeve(h)) {
                w += h.getWeightPct();
            }
        }
        return PortfolioIntelligenceSnapshotFactory.round2(w);
    }

    static boolean takesSectorShock(PortfolioIntelligenceSnapshot.Holding h, String sector) {
        if (h == null || !isEquitySleeve(h)) {
            return false;
        }
        if (IndexEtfSleeves.isBroadNiftyEtf(h.getSymbol())) {
            return false;
        }
        if (SectorMatcher.matches(h.getSector(), sector)) {
            return true;
        }
        // Book often has sector=Unknown while industry is "IT Services".
        return SectorMatcher.isUnusable(h.getSector())
                && SectorMatcher.matches(h.getIndustry(), sector);
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

    private StressResponse.ScenarioImpactDto impact(String id, double pctImpact, Double appliedShockPct) {
        return StressResponse.ScenarioImpactDto.builder()
                .id(id)
                .pctImpact(pctImpact)
                .absImpact(0)
                .appliedShockPct(appliedShockPct)
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

    public Map<String, String> getPresetCatalog() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, StressProperties.ScenarioConfig> e : scenarios.entrySet()) {
            m.put(e.getKey(), e.getValue().getDescription() != null ? e.getValue().getDescription() : "");
        }
        return m;
    }

    public static Map<String, String> presetCatalog() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, StressProperties.ScenarioConfig> e : DEFAULT_SCENARIOS.entrySet()) {
            m.put(e.getKey(), e.getValue().getDescription() != null ? e.getValue().getDescription() : "");
        }
        return m;
    }
}
