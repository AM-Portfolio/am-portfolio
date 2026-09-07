package com.portfolio.basket.model;

import lombok.Data;
import java.util.List;

@Data
public class EtfData {
    private String symbol;
    private String name;
    private String categoryLabel;
    private Double return1Y;
    private Double return3Y;
    private Double return5Y;
    private String returnsAsOf;
    private List<Double> sparklineCloses;
    private List<EtfHolding> holdings;
}
