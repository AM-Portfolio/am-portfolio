package com.portfolio.redis.model;

import java.time.Instant;

import com.am.common.investment.model.equity.EquityPrice;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class StockPriceCache {
    private String symbol;
    private String isin;
    private Double price;
    private Double change;
    private Double changePercent;
    private Long volume;
    private EquityPrice equityPrice;
    private Instant timestamp;
}
