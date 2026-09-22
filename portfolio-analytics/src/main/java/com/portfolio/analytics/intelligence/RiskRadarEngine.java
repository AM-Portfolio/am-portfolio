package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.RiskDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.portfolio.analytics.intelligence.HealthScoreConstants.*;

/**
 * Risk radar axes (0–100, higher = more risk) and findings chips per E2E thresholds.
 */
@Component
public class RiskRadarEngine {

    public static final double TOP1_HIGH_PCT = 25.0;
    public static final double SECTOR_HIGH_PCT = 30.0;
    public static final double SECTOR_MEDIUM_PCT = 20.0;
    public static final double BETA_HIGH = 1.3;
    public static final double BETA_MEDIUM = 1.1;

    @Value("${portfolio.intelligence.risk.top1-high-pct:25.0}")
    private double top1HighPct;

    @Value("${portfolio.intelligence.risk.sector-high-pct:30.0}")
    private double sectorHighPct;

    @Value("${portfolio.intelligence.risk.sector-medium-pct:20.0}")
    private double sectorMediumPct;

    @Value("${portfolio.intelligence.risk.beta-high:1.3}")
    private double betaHigh;

    @Value("${portfolio.intelligence.risk.beta-medium:1.1}")
    private double betaMedium;

    @Value("${portfolio.intelligence.health-v2:false}")
    private boolean healthV2;

    /** Test / programmatic override. */
    public void setHealthV2(boolean healthV2) {
        this.healthV2 = healthV2;
    }

    public RiskDto compute(PortfolioIntelligenceSnapshot snapshot) {
        int concHealth = roundInt(HealthScoreEngine.concentration(snapshot));
        int divHealth = roundInt(healthV2
                ? HealthScoreEngine.diversificationV2(snapshot)
                : HealthScoreEngine.diversification(snapshot));
        int liqHealth = roundInt(clamp(snapshot.getLiquidSharePct(), 0, 100));

        List<RiskDto.RiskAxisDto> axes = new ArrayList<>();
        axes.add(axis("CONCENTRATION", 100 - concHealth));
        axes.add(axis("SECTOR", sectorRisk(snapshot.getMaxSectorPct())));
        axes.add(axis("DIVERSIFICATION", 100 - divHealth));
        axes.add(axis("LIQUIDITY", 100 - liqHealth));

        if (healthV2) {
            if (snapshot.getHistoryPoints() >= MIN_HISTORY_POINTS && snapshot.getDailyVolPct() != null) {
                double ann = HealthScoreEngine.annualizedVolPct(snapshot.getDailyVolPct());
                int volHealth = roundInt(HealthScoreEngine.volatilityFromAnnPct(ann));
                axes.add(axis("VOLATILITY", 100 - volHealth));
            } else {
                axes.add(axis("VOLATILITY", 100 - 55));
            }
        } else if (snapshot.getHistoryPoints() >= MIN_HISTORY_POINTS) {
            if (snapshot.getDailyVolPct() != null) {
                int volHealth = roundInt(HealthScoreEngine.volatility(snapshot.getDailyVolPct()));
                axes.add(axis("VOLATILITY", 100 - volHealth));
            }
        }

        if (snapshot.getHistoryPoints() >= MIN_HISTORY_POINTS && snapshot.getBeta() != null) {
            int betaHealth = roundInt(HealthScoreEngine.betaScore(snapshot.getBeta()));
            axes.add(axis("BETA", 100 - betaHealth));
        }

        List<RiskDto.RiskFindingDto> findings = new ArrayList<>();
        if (snapshot.getTop1Pct() >= top1HighPct) {
            findings.add(RiskDto.RiskFindingDto.builder()
                    .code("TOP1_HIGH")
                    .label(String.format(Locale.ROOT, "Top holding %.1f%%", snapshot.getTop1Pct()))
                    .severity("HIGH")
                    .build());
        }
        if (snapshot.getMaxSectorPct() >= sectorHighPct) {
            String name = snapshot.getMaxSectorName() != null ? snapshot.getMaxSectorName() : "Sector";
            findings.add(RiskDto.RiskFindingDto.builder()
                    .code("SECTOR_HIGH")
                    .label(String.format(Locale.ROOT, "%s %.1f%%", name, snapshot.getMaxSectorPct()))
                    .severity("HIGH")
                    .build());
        } else if (snapshot.getMaxSectorPct() >= sectorMediumPct) {
            String name = snapshot.getMaxSectorName() != null ? snapshot.getMaxSectorName() : "Sector";
            findings.add(RiskDto.RiskFindingDto.builder()
                    .code("SECTOR_MEDIUM")
                    .label(String.format(Locale.ROOT, "%s %.1f%%", name, snapshot.getMaxSectorPct()))
                    .severity("MEDIUM")
                    .build());
        }
        if (snapshot.getHistoryPoints() >= MIN_HISTORY_POINTS && snapshot.getBeta() != null) {
            if (snapshot.getBeta() > betaHigh) {
                findings.add(RiskDto.RiskFindingDto.builder()
                        .code("BETA_HIGH")
                        .label(String.format(Locale.ROOT, "Beta %.2f", snapshot.getBeta()))
                        .severity("HIGH")
                        .build());
            } else if (snapshot.getBeta() > betaMedium) {
                findings.add(RiskDto.RiskFindingDto.builder()
                        .code("BETA_MEDIUM")
                        .label(String.format(Locale.ROOT, "Beta %.2f", snapshot.getBeta()))
                        .severity("MEDIUM")
                        .build());
            }
        }

        return RiskDto.builder().axes(axes).findings(findings).build();
    }

    private static int sectorRisk(double maxSectorPct) {
        return roundInt(clamp(maxSectorPct * 1.5, 0, 100));
    }

    private static RiskDto.RiskAxisDto axis(String id, int riskScore) {
        return RiskDto.RiskAxisDto.builder()
                .id(id)
                .riskScore(clampInt(riskScore))
                .build();
    }

    private static int clampInt(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
