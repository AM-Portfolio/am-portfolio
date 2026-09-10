package com.portfolio.analytics.intelligence;

import com.portfolio.model.analytics.intelligence.XRayDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds X-Ray sector / industry / market-cap weight slices from snapshot money weights.
 */
@Component
public class XRaySummaryBuilder {

    public XRayDto build(PortfolioIntelligenceSnapshot snapshot) {
        return XRayDto.builder()
                .sectorWeights(groupWeights(snapshot, PortfolioIntelligenceSnapshot.Holding::getSector))
                .industryWeights(groupWeights(snapshot, PortfolioIntelligenceSnapshot.Holding::getIndustry))
                .marketCapWeights(groupWeights(snapshot, PortfolioIntelligenceSnapshot.Holding::getMarketCap))
                .build();
    }

    private List<XRayDto.WeightSliceDto> groupWeights(
            PortfolioIntelligenceSnapshot snapshot,
            java.util.function.Function<PortfolioIntelligenceSnapshot.Holding, String> keyFn) {

        Map<String, Double> sums = new HashMap<>();
        if (snapshot.getHoldings() != null) {
            for (PortfolioIntelligenceSnapshot.Holding h : snapshot.getHoldings()) {
                String key = keyFn.apply(h);
                if (key == null || key.isBlank()) {
                    key = "Unknown";
                }
                sums.merge(key, h.getWeightPct(), Double::sum);
            }
        }
        List<XRayDto.WeightSliceDto> slices = new ArrayList<>();
        for (Map.Entry<String, Double> e : sums.entrySet()) {
            slices.add(XRayDto.WeightSliceDto.builder()
                    .name(e.getKey())
                    .weightPct(PortfolioIntelligenceSnapshotFactory.round2(e.getValue()))
                    .build());
        }
        slices.sort(Comparator.comparingDouble(XRayDto.WeightSliceDto::getWeightPct).reversed());
        return slices;
    }
}
