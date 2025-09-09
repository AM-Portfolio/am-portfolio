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
public class EquityHoldings {
   private String isin;
   private String symbol;
   private String name;
   private String sector;
   private String industry;
   private String marketCap;
   private Double quantity;
   private Double investmentCost;
   private Double currentValue;
   
   // Portfolio weight
   private Double weightInPortfolio; // Percentage of total portfolio value
   
   // Overall performance metrics
   private Double gainLoss;
   private Double gainLossPercentage;
   
   // Today's performance metrics
   private Double todayGainLoss;
   private Double todayGainLossPercentage;
   
   // Stock price metrics
   private Double averageBuyingPrice;
   private Double currentPrice;
   private Double percentageChange; // Stock price percentage change

   @Builder.Default
   private List<EquityBrokerHolding> brokerPortfolios = new ArrayList<>();
}