package com.portfolio.model.portfolio;

import java.util.ArrayList;
import java.util.List;

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
public class MutualFundHoldings {
   private String isin;
   private String symbol;
   private String name;
   private String category;
   private String subCategory;
   private String fundHouse;

   // Portfolio context
   private String portfolioId;
   private String portfolioName;

   private Double quantity;
   private Double investmentCost;
   private Double currentValue;

   // Portfolio weight
   private Double weightInPortfolio;

   // Overall performance metrics
   private Double gainLoss;
   private Double gainLossPercentage;

   // Today's performance metrics
   private Double todayGainLoss;
   private Double todayGainLossPercentage;

   // NAV metrics
   private Double averageBuyingPrice;
   private Double currentNav;
   private Double percentageChange;

   @Builder.Default
   private List<EquityBrokerHolding> brokerPortfolios = new ArrayList<>();
}
