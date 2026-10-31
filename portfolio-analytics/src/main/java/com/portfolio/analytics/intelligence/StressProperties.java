package com.portfolio.analytics.intelligence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
}
