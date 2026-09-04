package com.am.common.amcommondata.document.basket;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nested basket metadata on {@code portfolios} documents (PR56 dual-write target).
 * Composition holdings remain in the root {@code equities} array.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketPortfolioDocument {

    @Field("sourcePortfolioId")
    private String sourcePortfolioId;

    @Field("etfIsin")
    private String etfIsin;

    @Field("etfName")
    private String etfName;

    @Field("createdFromBasketAt")
    private LocalDateTime createdFromBasketAt;

    @Field("gapMissingCount")
    private Integer gapMissingCount;

    @Field("investmentAmount")
    private Double investmentAmount;

    @Field("replicaScore")
    private Double replicaScore;

    @Field("coverageAfterCreation")
    private Double coverageAfterCreation;
}
