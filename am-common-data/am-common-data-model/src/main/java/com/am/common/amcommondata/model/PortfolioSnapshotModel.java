package com.am.common.amcommondata.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class PortfolioSnapshotModel {
    private String snapshotId;
    private String userId;
    private LocalDate snapshotDate;
    
    // User-Level Totals
    private Double totalUserWealth;       // Matches "close" at the user level
    private Double totalUserWealthOpen;   // Matches "open" at the user level
    private Double totalUserWealthHigh;   
    private Double totalUserWealthLow;    
    
    private Double totalUserInvestment;
    private Double totalUserGainLoss;
    private Double totalUserGainLossPercentage;
    
    // Nested Portfolio Details
    private List<PortfolioSnapshotEntryModel> portfolios;
    private LocalDateTime createdAt;
}
