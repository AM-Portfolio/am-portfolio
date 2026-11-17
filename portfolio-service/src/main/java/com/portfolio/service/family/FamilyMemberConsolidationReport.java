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
public class FamilyMemberConsolidationReport {
    private String canonicalMemberId;
    private String nameNorm;
    @Builder.Default
    private List<String> duplicateMemberIds = new ArrayList<>();
    private int retaggedPortfolios;
    private int retaggedCasImports;
    private int retaggedNpsImports;
    private int mergedBrokerBooksDeleted;
    private int deletedDuplicateMembers;
    private Boolean applied;
}