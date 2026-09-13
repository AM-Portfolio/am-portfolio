package com.portfolio.model.portfolio;

import java.time.LocalDateTime;
import java.util.List;

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
public class PortfolioHoldings {
    private String userId;
    private String portfolioId;
    private List<EquityHoldings> equityHoldings;
    private LocalDateTime lastUpdated;
    /** Newest price timestamp used when serving (overlay or cold enrich). */
    private LocalDateTime asOf;
    /** LIVE when asOf is within cash-hours freshness policy; else AS_OF. */
    private String priceFreshness;
    /** TICK | OHLC | CACHE — how prices were primarily resolved. */
    private String priceSource;

    public static PortfolioHoldings empty() {
        return PortfolioHoldings.builder()
            .equityHoldings(java.util.Collections.emptyList())
            .lastUpdated(LocalDateTime.now())
            .asOf(LocalDateTime.now())
            .priceFreshness("AS_OF")
            .build();
    }
}
