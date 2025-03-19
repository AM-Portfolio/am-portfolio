package com.portfolio.model.portfolio.v1;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class BasePortfolioSummay {
    private double totalValue;
    private double totalCost;
    private double totalGainLoss;
    private double totalGainLossPercentage;
    private int totalAssets;
    private int gainersCount;
    private int losersCount;
    private Instant lastUpdated;
}
