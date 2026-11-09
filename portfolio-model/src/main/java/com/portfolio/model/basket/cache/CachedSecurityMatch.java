package com.portfolio.model.basket.cache;

import lombok.Data;

@Data
public class CachedSecurityMatch {
    private String query;
    private String symbol;
    private String isin;
    private String sector;
    private String industry;
    private String marketCapType;
    private Double marketCapValue;
}
