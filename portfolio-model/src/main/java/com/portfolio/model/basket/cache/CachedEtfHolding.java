package com.portfolio.model.basket.cache;

import lombok.Data;

@Data
public class CachedEtfHolding {
    private String isin;
    private String symbol;
    private String sector;
    private double weight;
    private String marketCapCategory;
    private Double marketCapValue;
}
