package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.RiskDto;
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
    public static final double BETA_HIGH = 1.3;

    public RiskDto compute(PortfolioIntelligenceSnapshot snapshot) {
        int concHealth = roundInt(HealthScoreEngine.concentration(snapshot));
        int divHealth = roundInt(HealthScoreEngine.diversification(snapshot));
        int liqHealth = roundInt(clamp(snapshot.getLiquidSharePct(), 0, 100));

        List<RiskDto.RiskAxisDto> axes = new ArrayList<>();
        axes.add(axis("CONCENTRATION", 100 - concHealth));
        axes.add(axis("SECTOR", sectorRisk(snapshot.getMaxSectorPct())));
        axes.add(axis("DIVERSIFICATION", 100 - divHealth));
        axes.add(axis("LIQUIDITY", 100 - liqHealth));

        if (snapshot.getHistoryPoints() >= MIN_HISTORY_POINTS) {
            double dailyVol = snapshot.getDailyVolPct() != null ? snapshot.getDailyVolPct() : 0.0;
            int volHealth = roundInt(HealthScoreEngine.volatility(dailyVol));
            axes.add(axis("VOLATILITY", 100 - volHealth));

            double beta = snapshot.getBeta() != null ? snapshot.getBeta() : 1.0;
            int betaHealth = roundInt(HealthScoreEngine.betaScore(beta));
            axes.add(axis("BETA", 100 - betaHealth));
        }

        List<RiskDto.RiskFindingDto> findings = new ArrayList<>();
        if (snapshot.getTop1Pct() >= TOP1_HIGH_PCT) {
            findings.add(RiskDto.RiskFindingDto.builder()
                    .code("TOP1_HIGH")
                    .label(String.format(Locale.ROOT, "Top holding %.1f%%", snapshot.getTop1Pct()))
                    .severity("HIGH")
                    .build());
        }
        if (snapshot.getMaxSectorPct() >= SECTOR_HIGH_PCT) {
            String name = snapshot.getMaxSectorName() != null ? snapshot.getMaxSectorName() : "Sector";
            findings.add(RiskDto.RiskFindingDto.builder()
                    .code("SECTOR_HIGH")
                    .label(String.format(Locale.ROOT, "%s %.1f%%", name, snapshot.getMaxSectorPct()))
                    .severity("HIGH")
                    .build());
        }
        if (snapshot.getHistoryPoints() >= MIN_HISTORY_POINTS
                && snapshot.getBeta() != null
                && snapshot.getBeta() > BETA_HIGH) {
            findings.add(RiskDto.RiskFindingDto.builder()
                    .code("BETA_HIGH")
                    .label(String.format(Locale.ROOT, "Beta %.2f", snapshot.getBeta()))
                    .severity("HIGH")
                    .build());
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
