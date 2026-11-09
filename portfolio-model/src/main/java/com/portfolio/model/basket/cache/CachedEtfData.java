package com.portfolio.model.basket.cache;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CachedEtfData {
    private String symbol;
    private String name;
    private String categoryLabel;
    private Double return1Y;
    private Double return3Y;
    private Double return5Y;
    private String returnsAsOf;
    private List<Double> sparklineCloses;
    private List<CachedEtfHolding> holdings = new ArrayList<>();
}
