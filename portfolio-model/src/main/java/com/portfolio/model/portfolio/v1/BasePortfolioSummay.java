package com.portfolio.model.portfolio.v1;

import java.time.LocalDateTime;

import com.portfolio.model.portfolio.DataStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class BasePortfolioSummay {

    private Double investmentValue;
    private Double currentValue;

    /**
     * True only when invested/return are trustworthy for this summary scope.
     * All Portfolios: every non-empty broker book has known cost.
     * Single book: that book has known cost. False/null → UI must hide Invested/Return.
     */
    private Boolean costBasisComplete;
    
    // Overall performance metrics
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
    
    // Today's performance metrics
    private Double todayGainLoss;
    private Double todayGainLossPercentage;
    
    // Portfolio statistics
    private Integer totalAssets;
    private Integer gainersCount;
    private Integer losersCount;
    
    // Day trading statistics
    private Integer todayGainersCount;
    private Integer todayLosersCount;
    
    private LocalDateTime lastUpdated;
    private DataStatus dataStatus;

    /** Newest price timestamp used when serving (overlay or cold). */
    private LocalDateTime asOf;
    /** LIVE when open and tick age ≤60s; else AS_OF. */
    private String priceFreshness;
    /** TICK | OHLC | CACHE */
    private String priceSource;
    /** NSE session date for prices shown. */
    private java.time.LocalDate sessionDate;
}
