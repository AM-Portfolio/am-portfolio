package com.am.common.amcommondata.model.accounts;

import java.util.List;

import com.am.common.amcommondata.model.cas.CasDocumentMeta;
import com.am.common.amcommondata.model.demat.DematAccountModel;
import com.am.common.amcommondata.model.nps.NpsDocumentMeta;
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
public class AccountsHubModel {
    private AccountsIdentity identity;
    private AccountsStats stats;
    private List<AccountsPortfolioRow> portfolios;
    private List<DematAccountModel> dematAccounts;
    private NpsDocumentMeta nps;
    /** All NPS statements for owner personas (null familyMemberId = primary). */
    private List<NpsDocumentMeta> npsAccounts;
    private CasDocumentMeta cas;
    private List<FamilyMemberSummary> familyMembers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class AccountsIdentity {
        private String name;
        private String email;
        private String mobile;
        private String pran;
        private String userId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class AccountsStats {
        private Integer activeDematCount;
        private Integer mfFolioCount;
        private Integer dematAccountCount;
        private Boolean npsLinked;
        private Integer brokerPortfolioCount;
        /** Annualized overall XIRR % across primary non-paper broker books; null when insufficient cashflows. */
        private Double overallXirrPct;
        /** Annualized XIRR % across all household (primary + family) non-paper broker books. */
        private Double householdXirrPct;
        /** Sum of primary-persona demat/MF/NPS/broker values. */
        private Double primaryTotalValue;
        /** Sum of all family-persona demat/MF/NPS/broker values. */
        private Double familyTotalValue;
        /** primaryTotalValue + familyTotalValue. */
        private Double householdTotalValue;
        /** familyTotalValue / householdTotalValue * 100; null when household is 0. */
        private Double familySharePct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class AccountsPortfolioRow {
        private String portfolioId;
        private String displayName;
        private String brokerType;
        private String portfolioKind;
        private Double invested;
        private Double current;
        private Double returnAbs;
        private Double returnPct;
        private Integer assetCount;
        private Boolean dummy;
        private String familyMemberId;
        private String familyMemberName;
        private String dematBoid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static class FamilyMemberSummary {
        private String id;
        private String name;
        private String email;
        private Integer portfolioCount;
        private String source;
        /** Sum of this member's demat/MF/NPS/broker values. */
        private Double totalValue;
        /** Annualized XIRR % for this member's non-paper books; null when insufficient cashflows. */
        private Double xirrPct;
    }
}
