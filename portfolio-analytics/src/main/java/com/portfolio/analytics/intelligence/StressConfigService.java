package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.document.stress.IndexSleeveDocument;
import com.am.common.amcommondata.document.stress.StressScenarioDocument;
import com.am.common.amcommondata.repository.stress.IndexSleeveRepository;
import com.am.common.amcommondata.repository.stress.StressScenarioRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live cache for stress scenarios and ETF sleeves. Runtime source of truth is Mongo;
 * classpath YAML ({@link StressProperties#loadClasspathDefaults()}) seeds / fail-opens.
 */
@Slf4j
@Service
public class StressConfigService {

    private final Optional<StressScenarioRepository> scenarioRepo;
    private final Optional<IndexSleeveRepository> sleeveRepo;
    private volatile StressProperties properties;

    private final Map<String, StressProperties.ScenarioConfig> scenarioCache = new ConcurrentHashMap<>();
    private final Set<String> rawIndexCache = ConcurrentHashMap.newKeySet();
    private final Set<String> broadNiftyEtfCache = ConcurrentHashMap.newKeySet();

    @Autowired
    public StressConfigService(
            @Autowired(required = false) StressScenarioRepository scenarioRepo,
            @Autowired(required = false) IndexSleeveRepository sleeveRepo,
            @Autowired(required = false) StressProperties properties) {
        this.scenarioRepo = Optional.ofNullable(scenarioRepo);
        this.sleeveRepo = Optional.ofNullable(sleeveRepo);
        this.properties = ensureSeedProperties(properties);
    }

    /** Test / manual construction. */
    public StressConfigService(StressProperties properties) {
        this(null, null, properties);
    }

    private static StressProperties ensureSeedProperties(StressProperties properties) {
        if (properties != null
                && properties.getScenarios() != null
                && !properties.getScenarios().isEmpty()) {
            return properties;
        }
        StressProperties fromClasspath = StressProperties.loadClasspathDefaults();
        if (properties == null) {
            return fromClasspath;
        }
        if (properties.getScenarios() == null || properties.getScenarios().isEmpty()) {
            properties.setScenarios(fromClasspath.getScenarios());
        }
        if (properties.getRawIndices() == null || properties.getRawIndices().isEmpty()) {
            properties.setRawIndices(fromClasspath.getRawIndices());
        }
        if (properties.getBroadNiftyEtfs() == null || properties.getBroadNiftyEtfs().isEmpty()) {
            properties.setBroadNiftyEtfs(fromClasspath.getBroadNiftyEtfs());
        }
        return properties;
    }

    @PostConstruct
    public void initialize() {
        this.properties = ensureSeedProperties(properties);
        seedMongoIdempotent();
        reloadCache();
    }

    @Scheduled(fixedDelayString = "${portfolio.intelligence.stress.refresh-rate-ms:300000}")
    public void reloadCache() {
        boolean loadedFromDb = false;
        try {
            if (scenarioRepo.isPresent() && sleeveRepo.isPresent()) {
                List<StressScenarioDocument> scenarios = scenarioRepo.get().findByActiveTrueOrderBySortOrderAsc();
                if (!scenarios.isEmpty()) {
                    Map<String, StressProperties.ScenarioConfig> next = new LinkedHashMap<>();
                    for (StressScenarioDocument doc : scenarios) {
                        if (doc.getScenarioCode() != null) {
                            next.put(doc.getScenarioCode().trim().toUpperCase(Locale.ROOT), toScenarioConfig(doc));
                        }
                    }
                    scenarioCache.clear();
                    scenarioCache.putAll(next);
                    loadedFromDb = true;
                }

                List<IndexSleeveDocument> sleeves = sleeveRepo.get().findByActiveTrue();
                if (!sleeves.isEmpty()) {
                    Set<String> raw = ConcurrentHashMap.newKeySet();
                    Set<String> broad = ConcurrentHashMap.newKeySet();
                    for (IndexSleeveDocument doc : sleeves) {
                        String compact = IndexEtfSleeves.compact(doc.getSymbol());
                        if (compact == null) {
                            continue;
                        }
                        if ("RAW_INDEX".equalsIgnoreCase(doc.getSleeveType())) {
                            raw.add(compact);
                        } else if ("BROAD_NIFTY_ETF".equalsIgnoreCase(doc.getSleeveType())) {
                            broad.add(compact);
                        } else {
                            log.warn("Unknown index sleeve_type={} symbol={}", doc.getSleeveType(), doc.getSymbol());
                        }
                    }
                    rawIndexCache.clear();
                    rawIndexCache.addAll(raw);
                    broadNiftyEtfCache.clear();
                    broadNiftyEtfCache.addAll(broad);
                    loadedFromDb = true;
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to load stress configuration from MongoDB: {}. Falling back to properties.",
                    ex.getMessage());
        }

        if (!loadedFromDb) {
            loadFromProperties();
        }

        IndexEtfSleeves.replaceRawIndex(new ArrayList<>(rawIndexCache));
        IndexEtfSleeves.replaceBroadNiftyEtf(new ArrayList<>(broadNiftyEtfCache));
        log.info("Stress config cache ready scenarios={} rawIndices={} broadEtfs={} source={}",
                scenarioCache.size(), rawIndexCache.size(), broadNiftyEtfCache.size(),
                loadedFromDb ? "mongo" : "yaml");
    }

    private void seedMongoIdempotent() {
        try {
            if (properties == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (scenarioRepo.isPresent() && properties.getScenarios() != null) {
                int order = 1;
                int upserted = 0;
                for (Map.Entry<String, StressProperties.ScenarioConfig> entry : properties.getScenarios().entrySet()) {
                    String code = entry.getKey().trim().toUpperCase(Locale.ROOT);
                    StressProperties.ScenarioConfig cfg = entry.getValue();
                    StressScenarioDocument doc = scenarioRepo.get().findByScenarioCode(code)
                            .orElseGet(StressScenarioDocument::new);
                    boolean isNew = doc.getId() == null;
                    doc.setScenarioCode(code);
                    doc.setScenarioName(entry.getKey());
                    doc.setScenarioType(cfg.getType() != null ? cfg.getType().name() : "UNIFORM");
                    doc.setShockPct(cfg.getShockPct());
                    doc.setTargetSector(cfg.getTargetSector());
                    doc.setSectorShocks(cfg.getSectorShocks());
                    doc.setResidualShock(cfg.getResidualShock());
                    doc.setDescription(cfg.getDescription());
                    doc.setActive(true);
                    doc.setSortOrder(order++);
                    doc.setUpdatedAt(now);
                    if (isNew) {
                        doc.setCreatedAt(now);
                    }
                    scenarioRepo.get().save(doc);
                    upserted++;
                }
                if (upserted > 0) {
                    log.info("Upserted {} stress scenarios into MongoDB collection stress_scenarios", upserted);
                }
            }

            if (sleeveRepo.isPresent()) {
                int upserted = 0;
                if (properties.getRawIndices() != null) {
                    for (String sym : properties.getRawIndices()) {
                        upserted += upsertSleeve(sym, "RAW_INDEX", now);
                    }
                }
                if (properties.getBroadNiftyEtfs() != null) {
                    for (String sym : properties.getBroadNiftyEtfs()) {
                        upserted += upsertSleeve(sym, "BROAD_NIFTY_ETF", now);
                    }
                }
                if (upserted > 0) {
                    log.info("Upserted {} index sleeves into MongoDB collection index_sleeves", upserted);
                }
            }
        } catch (Exception ex) {
            log.warn("MongoDB stress configuration seed skipped: {}", ex.getMessage());
        }
    }

    private int upsertSleeve(String symbol, String sleeveType, LocalDateTime now) {
        if (symbol == null || symbol.isBlank() || sleeveRepo.isEmpty()) {
            return 0;
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        IndexSleeveDocument doc = sleeveRepo.get().findBySymbol(normalized).orElseGet(IndexSleeveDocument::new);
        boolean isNew = doc.getId() == null;
        doc.setSymbol(normalized);
        doc.setSleeveType(sleeveType);
        doc.setActive(true);
        doc.setUpdatedAt(now);
        if (isNew) {
            doc.setCreatedAt(now);
        }
        sleeveRepo.get().save(doc);
        return 1;
    }

    private void loadFromProperties() {
        if (properties == null) {
            return;
        }
        scenarioCache.clear();
        if (properties.getScenarios() != null) {
            properties.getScenarios().forEach((k, v) -> {
                if (k != null && v != null) {
                    scenarioCache.put(k.trim().toUpperCase(Locale.ROOT), v);
                }
            });
        }
        rawIndexCache.clear();
        if (properties.getRawIndices() != null) {
            for (String s : properties.getRawIndices()) {
                String c = IndexEtfSleeves.compact(s);
                if (c != null) {
                    rawIndexCache.add(c);
                }
            }
        }
        broadNiftyEtfCache.clear();
        if (properties.getBroadNiftyEtfs() != null) {
            for (String s : properties.getBroadNiftyEtfs()) {
                String c = IndexEtfSleeves.compact(s);
                if (c != null) {
                    broadNiftyEtfCache.add(c);
                }
            }
        }
    }

    private StressProperties.ScenarioConfig toScenarioConfig(StressScenarioDocument doc) {
        StressProperties.ScenarioType type = StressProperties.ScenarioType.UNIFORM;
        if (doc.getScenarioType() != null) {
            try {
                type = StressProperties.ScenarioType.valueOf(doc.getScenarioType());
            } catch (Exception ignored) {
            }
        }
        return StressProperties.ScenarioConfig.builder()
                .type(type)
                .shockPct(doc.getShockPct())
                .targetSector(doc.getTargetSector())
                .description(doc.getDescription())
                .sectorShocks(doc.getSectorShocks())
                .residualShock(doc.getResidualShock())
                .build();
    }

    public Map<String, StressProperties.ScenarioConfig> getScenarios() {
        return Collections.unmodifiableMap(scenarioCache);
    }

    public StressProperties.ScenarioConfig getScenario(String code) {
        if (code == null) {
            return null;
        }
        return scenarioCache.get(code.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isRawIndex(String compactSymbol) {
        return compactSymbol != null && rawIndexCache.contains(compactSymbol);
    }

    public boolean isBroadNiftyEtf(String compactSymbol) {
        return compactSymbol != null && broadNiftyEtfCache.contains(compactSymbol);
    }

    public void registerScenario(String code, StressProperties.ScenarioConfig config) {
        if (code != null && config != null) {
            scenarioCache.put(code.trim().toUpperCase(Locale.ROOT), config);
        }
    }

    public void registerRawIndex(Collection<String> symbols) {
        if (symbols == null) {
            return;
        }
        for (String s : symbols) {
            String c = IndexEtfSleeves.compact(s);
            if (c != null) {
                rawIndexCache.add(c);
            }
        }
        IndexEtfSleeves.registerRawIndex(symbols);
    }

    public void registerBroadNiftyEtf(Collection<String> symbols) {
        if (symbols == null) {
            return;
        }
        for (String s : symbols) {
            String c = IndexEtfSleeves.compact(s);
            if (c != null) {
                broadNiftyEtfCache.add(c);
            }
        }
        IndexEtfSleeves.registerBroadNiftyEtf(symbols);
    }

    public void resetForTesting() {
        scenarioCache.clear();
        rawIndexCache.clear();
        broadNiftyEtfCache.clear();
        IndexEtfSleeves.resetConfiguredForTesting();
        if (properties != null) {
            loadFromProperties();
            IndexEtfSleeves.replaceRawIndex(new ArrayList<>(rawIndexCache));
            IndexEtfSleeves.replaceBroadNiftyEtf(new ArrayList<>(broadNiftyEtfCache));
        }
    }
}
