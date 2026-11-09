package com.portfolio.analytics.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for portfolio stress testing scenarios and index sleeves.
 * Bound from {@code portfolio.intelligence.stress} in application.yml.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "portfolio.intelligence.stress")
public class StressProperties {

    /**
     * Cache refresh interval from MongoDB in milliseconds (default 5 minutes).
     */
    private long refreshRateMs = 300000L;

    /**
     * Additional or override raw index tickers (e.g. NIFTY, SENSEX, BANKNIFTY).
     */
    private List<String> rawIndices = new ArrayList<>();

    /**
     * Additional or override broad market ETF tickers (e.g. NIFTYBEES, SETFNIF50).
     */
    private List<String> broadNiftyEtfs = new ArrayList<>();

    /**
     * Configurable stress presets / scenarios keyed by scenario id (e.g. NIFTY_DOWN_10, COVID_2020).
     */
    private Map<String, ScenarioConfig> scenarios = new LinkedHashMap<>();

    public enum ScenarioType {
        UNIFORM,
        SECTOR,
        MULTI_SECTOR
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioConfig {
        /** Type of scenario: UNIFORM, SECTOR, or MULTI_SECTOR. */
        private ScenarioType type;
        /** Shock percentage (e.g. -10.0, -20.0, -56.0). */
        private Double shockPct;
        /** Target sector for SECTOR type (e.g. "Banking", "IT", "Energy"). */
        private String targetSector;
        /** Catalog description / formula hint. */
        private String description;
        /** Sector shocks map for MULTI_SECTOR (e.g. Banking: -35.0, IT: -30.0). */
        private Map<String, Double> sectorShocks;
        /** Residual shock for holdings not matching any specific sector in MULTI_SECTOR. */
        private Double residualShock;
    }

    @SuppressWarnings("unchecked")
    public static StressProperties loadClasspathDefaults() {
        StressProperties props = new StressProperties();
        try (InputStream is = StressProperties.class.getResourceAsStream("/stress-defaults.yml")) {
            if (is != null) {
                Yaml yaml = new Yaml();
                Map<String, Object> root = yaml.load(is);
                if (root != null && root.containsKey("portfolio")) {
                    Map<String, Object> p = (Map<String, Object>) root.get("portfolio");
                    Map<String, Object> intel = (Map<String, Object>) p.get("intelligence");
                    if (intel != null) {
                        Map<String, Object> stress = (Map<String, Object>) intel.get("stress");
                        if (stress != null) {
                            if (stress.containsKey("refresh-rate-ms")) {
                                props.setRefreshRateMs(((Number) stress.get("refresh-rate-ms")).longValue());
                            }
                            if (stress.containsKey("raw-indices")) {
                                props.setRawIndices(new ArrayList<>((List<String>) stress.get("raw-indices")));
                            }
                            if (stress.containsKey("broad-nifty-etfs")) {
                                props.setBroadNiftyEtfs(new ArrayList<>((List<String>) stress.get("broad-nifty-etfs")));
                            }
                            if (stress.containsKey("scenarios")) {
                                Map<String, Map<String, Object>> scMap = (Map<String, Map<String, Object>>) stress.get("scenarios");
                                scMap.forEach((k, v) -> {
                                    ScenarioConfig sc = new ScenarioConfig();
                                    if (v.containsKey("type")) {
                                        sc.setType(ScenarioType.valueOf((String) v.get("type")));
                                    }
                                    if (v.containsKey("shock-pct")) {
                                        sc.setShockPct(((Number) v.get("shock-pct")).doubleValue());
                                    }
                                    if (v.containsKey("target-sector")) {
                                        sc.setTargetSector((String) v.get("target-sector"));
                                    }
                                    if (v.containsKey("description")) {
                                        sc.setDescription((String) v.get("description"));
                                    }
                                    if (v.containsKey("residual-shock")) {
                                        sc.setResidualShock(((Number) v.get("residual-shock")).doubleValue());
                                    }
                                    if (v.containsKey("sector-shocks")) {
                                        Map<String, Number> ss = (Map<String, Number>) v.get("sector-shocks");
                                        Map<String, Double> shocks = new LinkedHashMap<>();
                                        ss.forEach((sKey, sVal) -> shocks.put(sKey, sVal.doubleValue()));
                                        sc.setSectorShocks(shocks);
                                    }
                                    props.getScenarios().put(k, sc);
                                });
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return props;
    }
}
