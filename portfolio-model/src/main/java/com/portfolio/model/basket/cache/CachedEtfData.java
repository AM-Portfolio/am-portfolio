package com.portfolio.model.basket.cache;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CachedEtfData {
    private String symbol;
    private String name;
    private List<CachedEtfHolding> holdings = new ArrayList<>();
}
