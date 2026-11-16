package com.portfolio.service.family;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyOrphanReport {
    private int portfolioCount;
    private int casImportCount;
    private int npsImportCount;
    private double portfolioValueSum;
    @Builder.Default
    private List<OrphanPortfolioRow> portfolios = new ArrayList<>();
    @Builder.Default
    private List<String> casImportIds = new ArrayList<>();
    @Builder.Default
    private List<String> npsImportIds = new ArrayList<>();
    private Integer deletedPortfolios;
    private Integer deletedCasImports;
    private Integer deletedNpsImports;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrphanPortfolioRow {
        private String id;
        private String name;
        private String brokerType;
        private String familyMemberId;
        private String familyMemberName;
        private Double totalValue;
    }
}
