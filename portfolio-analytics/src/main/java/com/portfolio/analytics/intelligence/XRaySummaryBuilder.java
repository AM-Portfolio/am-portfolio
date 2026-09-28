package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.XRayDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds X-Ray sector / industry / market-cap / asset-class weight slices from snapshot money weights.
 */
@Component
public class XRaySummaryBuilder {

    private static final java.util.Set<String> NON_EQUITY_CLASSES = java.util.Set.of(
            HealthScoreEngine.ASSET_FIXED_INCOME, 
            HealthScoreEngine.ASSET_COMMODITY, 
            HealthScoreEngine.ASSET_CASH
    );

    public XRayDto build(PortfolioIntelligenceSnapshot snapshot) {
        return XRayDto.builder()
                .sectorWeights(groupWeights(snapshot, PortfolioIntelligenceSnapshot.Holding::getSector, h -> !isNonEquity(h)))
                .industryWeights(groupWeights(snapshot, PortfolioIntelligenceSnapshot.Holding::getIndustry, h -> !isNonEquity(h)))
                .marketCapWeights(groupWeights(snapshot, PortfolioIntelligenceSnapshot.Holding::getMarketCap, h -> !isNonEquity(h)))
                .assetClassWeights(groupWeights(snapshot, h -> HealthScoreEngine.normalizeAssetClass(h.getAssetClass()), this::isNonEquity))
                .totalValue(PortfolioIntelligenceSnapshotFactory.round2(snapshot.getTotalValue()))
                .build();
    }

    private boolean isNonEquity(PortfolioIntelligenceSnapshot.Holding h) {
        String ac = HealthScoreEngine.normalizeAssetClass(h.getAssetClass());
        return NON_EQUITY_CLASSES.contains(ac);
    }

    private List<XRayDto.WeightSliceDto> groupWeights(
            PortfolioIntelligenceSnapshot snapshot,
            java.util.function.Function<PortfolioIntelligenceSnapshot.Holding, String> keyFn,
            java.util.function.Predicate<PortfolioIntelligenceSnapshot.Holding> filter) {

        Map<String, Double> weightSums = new HashMap<>();
        Map<String, Double> valueSums = new HashMap<>();
        
        // Sum weights and values for all matching holdings
        double totalFilteredWeight = 0.0;
        if (snapshot.getHoldings() != null) {
            for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
                if (filter != null && !filter.test(h)) {
                    continue;
                }
                String key = keyFn.apply(h);
                if (key == null || key.isBlank()) {
                    key = "Unknown";
                }
                weightSums.merge(key, h.getWeightPct(), Double::sum);
                valueSums.merge(key, h.getValue(), Double::sum);
                totalFilteredWeight += h.getWeightPct();
            }
        }
        
        // Re-normalize weights so they sum to 100% of the visible pie
        List<XRayDto.WeightSliceDto> slices = new ArrayList<>();
        if (totalFilteredWeight > 0) {
            for (Map.Entry<String, Double> e : weightSums.entrySet()) {
                double rawWeight = e.getValue();
                double normalizedWeight = (rawWeight / totalFilteredWeight) * 100.0;
                
                slices.add(XRayDto.WeightSliceDto.builder()
                        .name(e.getKey())
                        .weightPct(PortfolioIntelligenceSnapshotFactory.round2(normalizedWeight))
                        .value(PortfolioIntelligenceSnapshotFactory.round2(
                                valueSums.getOrDefault(e.getKey(), 0.0)))
                        .build());
            }
        }
        slices.sort(Comparator.comparingDouble(XRayDto.WeightSliceDto::getWeightPct).reversed());
        return slices;
    }
}
