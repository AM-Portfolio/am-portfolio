package com.am.common.amcommondata.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties({"userId"})
public class PortfolioSnapshotModel {
    private String snapshotId;
    /** Kept for in-process/cache use; history JSON must not emit this. */
    @lombok.Getter(onMethod_ = @JsonIgnore)
    @lombok.Setter(onMethod_ = @JsonIgnore)
    private String userId;
    private LocalDate snapshotDate;
    
    // User-Level Totals
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalUserWealth;       // Matches "close" at the user level
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalUserWealthOpen;   // Matches "open" at the user level
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalUserWealthHigh;
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalUserWealthLow;
    
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalUserInvestment;
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalUserGainLoss;
    @JsonSerialize(using = TwoDecimalSerializer.class)
    private Double totalUserGainLossPercentage;
    
    // Nested Portfolio Details
    private List<PortfolioSnapshotEntryModel> portfolios;
    private LocalDateTime createdAt;
}
