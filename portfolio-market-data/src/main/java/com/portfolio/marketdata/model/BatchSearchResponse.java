package com.portfolio.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for batch security search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSearchResponse {

    private List<QueryResult> results;
    private Integer totalQueries;
    private Integer totalMatches;
    private Integer queriesWithNoMatches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryResult {
        private String query;
        private List<SecurityMatch> matches;
        private Integer matchCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityMatch {
        private String symbol;
        private String isin;
        private String companyName;
        private String sector;
        private String industry;
        private Double matchScore;
        private String matchedField;
        private Long marketCapValue;
        private String marketCapType;
    }
}
