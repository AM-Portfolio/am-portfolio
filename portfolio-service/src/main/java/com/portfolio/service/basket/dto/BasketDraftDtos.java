package com.portfolio.service.basket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class BasketDraftDtos {

    private BasketDraftDtos() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasketDraftSummaryDto {
        private String id;
        private String sourcePortfolioId;
        private String etfIsin;
        private String etfName;
        private String basketName;
        private Double investmentAmount;
        private Double replicaScore;
        private Boolean hasCalculated;
        private LocalDateTime updatedAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasketDraftListResponse {
        private List<BasketDraftSummaryDto> drafts;
        private int draftCount;
        private int draftLimit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasketDraftDetailDto {
        private String id;
        private String userId;
        private String sourcePortfolioId;
        private String etfIsin;
        private String etfName;
        private String basketName;
        private Double investmentAmount;
        private Double replicaScore;
        private Boolean hasCalculated;
        private List<String> excludedSymbols;
        private Map<String, Integer> manualQtyOverrides;
        private Map<String, Object> opportunity;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsertBasketDraftRequest {
        private String userId;
        private String sourcePortfolioId;
        private String etfIsin;
        private String etfName;
        private String basketName;
        private Double investmentAmount;
        private Double replicaScore;
        private Boolean hasCalculated;
        private List<String> excludedSymbols;
        private Map<String, Integer> manualQtyOverrides;
        private Map<String, Object> opportunity;
        /** Optional: when set, update this draft if owned (must match upsert key). */
        private String draftId;
    }
}
