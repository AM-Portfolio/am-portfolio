package com.portfolio.basket.model;

import lombok.Data;
import java.util.List;

@Data
public class EtfData {
    private String symbol;
    private String name;
    private List<EtfHolding> holdings;
}
