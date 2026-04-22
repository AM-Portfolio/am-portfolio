package com.portfolio.redis.model;

import java.time.Instant;
import java.time.LocalDateTime;

import com.am.common.investment.model.equity.MarketIndexIndices;
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
public class MarketIndexIndicesCache {
    private String key;
    private String indexSymbol;
    private String index;
    private MarketIndexIndices indexIndices;
    private LocalDateTime timestamp;
}
