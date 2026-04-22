package com.portfolio.basket.model;

import lombok.Data;

@Data
public class EtfHolding {
    private String isin;
    private String symbol;
    private String sector;
    private double weight;
    private String marketCapCategory;
    private Double marketCapValue;
}
