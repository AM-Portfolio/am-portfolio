package com.portfolio.model.analytics;

import lombok.Data;
import java.util.List;

@Data
public class IndexAnalytics {
    private List<Heatmap> heatmaps;
    private List<GainerLoser> gainerLosers;
    private List<PercentageAllocation> percentageAllocations;
}

@Data
class Heatmap {
    private String sector;
    private double performance;
}

@Data
class GainerLoser {
    private String stock;
    private double change;
    private boolean isGainer;
}

@Data
class PercentageAllocation {
    private String industry;
    private double allocation;
}
