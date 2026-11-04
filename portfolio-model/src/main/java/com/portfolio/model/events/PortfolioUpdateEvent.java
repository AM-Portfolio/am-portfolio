package com.portfolio.model.events;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.am.common.amcommondata.model.asset.pension.NPSModel;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortfolioUpdateEvent {
    private UUID id;
    private BrokerType brokerType;
    private String source; // "TRADE" or "DOCUMENT"
    private String userId;
    private String portfolioId;
    private String name;
    private String action;

    // Core Data
    private List<EquityModel> equities;
    private List<MutualFundModel> mutualFunds;
    private List<NPSModel> npsHoldings;

    // Summary / Calculation Data
    private Double totalValue;
    private Double totalInvestment;
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
    private Double todayGainLoss;
    private Double todayGainLossPercentage;

    private LocalDateTime timestamp;

    /**
     * Statement / Gmail message calendar day (Asia/Kolkata). When set, holdings
     * {@code createdAt} and history backfill window start from this date.
     */
    private LocalDate asOfDate;

    /** e.g. CDSL_CAS when fan-out from consolidated account statement. */
    private String documentSource;

    /** Demat BOID / DP refs for CAS-mapped broker books. */
    private DematRef dematRef;

    /** SELF | FAMILY | UNKNOWN from email-extractor. */
    private String identityMatch;
    private String holderName;
    private String holderEmail;
    private String holderPan;
    private String familyMemberId;
    private String familyMemberName;
}
