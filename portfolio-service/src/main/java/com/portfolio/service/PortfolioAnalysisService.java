package com.portfolio.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.portfolio.model.PortfolioAnalysis;
import com.portfolio.model.StockPerformance;
import com.portfolio.model.StockPriceCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalysisService {
    
    private final AMPortfolioService portfolioService;
    private final StockPriceRedisService stockPriceRedisService;
    private static final int TOP_N = 5; // Number of top gainers/losers to return

    public PortfolioAnalysis analyzePortfolio(String portfolioId, String userId) {
        try {
            PortfolioModel portfolio = portfolioService.getPortfolioById(UUID.fromString(portfolioId));
            if (portfolio == null) {
                log.error("Portfolio not found for ID: {}", portfolioId);
                return null;
            }

            List<AssetModel> equityHoldings = portfolio.getAssets().stream()
                .filter(asset -> AssetType.EQUITY.equals(asset.getAssetType()))
                .toList();

            List<StockPerformance> performances = calculateStockPerformances(equityHoldings);
            
            // Calculate total portfolio value and gain/loss
            double totalValue = performances.stream()
                .mapToDouble(p -> p.getCurrentPrice() * p.getQuantity())
                .sum();
            
            double totalCost = performances.stream()
                .mapToDouble(p -> p.getAveragePrice() * p.getQuantity())
                .sum();
            
            double totalGainLoss = totalValue - totalCost;
            double totalGainLossPercentage = (totalGainLoss / totalCost) * 100;

            // Sort performances by gain/loss percentage
            performances.sort(Comparator.comparing(StockPerformance::getGainLossPercentage));
            
            List<StockPerformance> topLosers = performances.subList(0, Math.min(TOP_N, performances.size()));
            List<StockPerformance> topGainers = new ArrayList<>(
                performances.subList(Math.max(0, performances.size() - TOP_N), performances.size()));
            java.util.Collections.reverse(topGainers);

            return PortfolioAnalysis.builder()
                .portfolioId(portfolioId)
                .userId(userId)
                .totalValue(totalValue)
                .totalGainLoss(totalGainLoss)
                .totalGainLossPercentage(totalGainLossPercentage)
                .topGainers(topGainers)
                .topLosers(topLosers)
                .lastUpdated(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Error analyzing portfolio {}: {}", portfolioId, e.getMessage(), e);
            return null;
        }
    }

    private List<StockPerformance> calculateStockPerformances(List<AssetModel> equityHoldings) {
        return equityHoldings.stream()
            .map(asset -> {
                Optional<StockPriceCache> latestPrice = stockPriceRedisService.getLatestPrice(asset.getSymbol());
                if (latestPrice.isEmpty()) {
                    log.warn("No price data found for symbol: {}", asset.getSymbol());
                    return null;
                }

                double currentPrice = latestPrice.get().getClosePrice();
                double averagePrice = asset.getAvgBuyingPrice();
                double quantity = asset.getQuantity();
                double gainLoss = (currentPrice - averagePrice) * quantity;
                double gainLossPercentage = ((currentPrice - averagePrice) / averagePrice) * 100;

                return StockPerformance.builder()
                    .symbol(asset.getSymbol())
                    .quantity(quantity)
                    .currentPrice(currentPrice)
                    .averagePrice(averagePrice)
                    .gainLoss(gainLoss)
                    .gainLossPercentage(gainLossPercentage)
                    .build();
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }
}
