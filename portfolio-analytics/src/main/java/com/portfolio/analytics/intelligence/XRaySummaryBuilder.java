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

    public XRayDto build(PortfolioIntelligenceSnapshot snapshot) {
        return XRayDto.builder()
                .sectorWeights(groupWeights(snapshot, PortfolioIntelligenceSnapshot.Holding::getSector))
                .industryWeights(groupWeights(snapshot, PortfolioIntelligenceSnapshot.Holding::getIndustry))
                .marketCapWeights(groupWeights(snapshot, PortfolioIntelligenceSnapshot.Holding::getMarketCap))
                .assetClassWeights(groupWeights(snapshot, h -> HealthScoreEngine.normalizeAssetClass(h.getAssetClass())))
                .totalValue(PortfolioIntelligenceSnapshotFactory.round2(snapshot.getTotalValue()))
                .build();
    }

    private List<XRayDto.WeightSliceDto> groupWeights(
            PortfolioIntelligenceSnapshot snapshot,
            java.util.function.Function<PortfolioIntelligenceSnapshot.Holding, String> keyFn) {

        Map<String, Double> weightSums = new HashMap<>();
        Map<String, Double> valueSums = new HashMap<>();
        if (snapshot.getHoldings() != null) {
            for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
                String key = keyFn.apply(h);
                if (key == null || key.isBlank()) {
                    key = "Unknown";
                }
                weightSums.merge(key, h.getWeightPct(), Double::sum);
                valueSums.merge(key, h.getValue(), Double::sum);
            }
        }
        List<XRayDto.WeightSliceDto> slices = new ArrayList<>();
        for (Map.Entry<String, Double> e : weightSums.entrySet()) {
            slices.add(XRayDto.WeightSliceDto.builder()
                    .name(e.getKey())
                    .weightPct(PortfolioIntelligenceSnapshotFactory.round2(e.getValue()))
                    .value(PortfolioIntelligenceSnapshotFactory.round2(
                            valueSums.getOrDefault(e.getKey(), 0.0)))
                    .build());
        }
        slices.sort(Comparator.comparingDouble(XRayDto.WeightSliceDto::getWeightPct).reversed());
        return slices;
    }
}
