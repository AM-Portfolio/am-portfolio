package com.portfolio.model.portfolio;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class  EquityHoldings {
   private String isin;
   private String symbol;
   private String name;
   private String sector;
   private String industry;
   private String marketCap;
   private Double quantity;
   private Double investmentCost;
   private Double currentValue;
   private Double gainLoss;
   private Double gainLossPercentage;
   private Double averageBuyingPrice;
   private double currentPrice;

   @Builder.Default
   private List<EquityBrokerHolding> brokerPortfolios = new ArrayList<>();
}