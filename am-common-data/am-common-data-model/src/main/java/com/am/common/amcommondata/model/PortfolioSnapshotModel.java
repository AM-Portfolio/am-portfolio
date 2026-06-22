package com.am.common.amcommondata.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class PortfolioSnapshotModel {
    private String portfolioId;
    private String userId;
    private LocalDate snapshotDate;
    private Double open;   // = previous day's close (for chart continuity)
    private Double high;   // = close (EOD snapshot, single point per day)
    private Double low;    // = close (EOD snapshot)
    private Double close;  // = total portfolio value at 4 PM
    private Double totalInvestment;
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
    private LocalDateTime createdAt;
}
