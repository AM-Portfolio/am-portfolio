package com.portfolio.model.analytics;

import lombok.Data;
import java.util.List;

@Data
public class IndexAnalytics {
    private List<Heatmap> heatmaps;
    private List<GainerLoser> gainerLosers;
    private List<PercentageAllocation> percentageAllocations;
}
