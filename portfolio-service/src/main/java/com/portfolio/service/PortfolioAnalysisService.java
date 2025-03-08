package com.portfolio.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModel;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.portfolio.model.PaginatedStockPerformance;
import com.portfolio.model.PortfolioAnalysis;
import com.portfolio.model.StockPerformance;
import com.portfolio.model.StockPriceCache;
import com.portfolio.model.TimeInterval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalysisService {
    
    private final AMPortfolioService portfolioService;
    private final StockPriceRedisService stockPriceRedisService;
    private static final int DEFAULT_TOP_N = 5;
    private static final int DEFAULT_PAGE_SIZE = 5;

    @Cacheable(value = "portfolioAnalysis", 
               key = "#portfolioId + '_' + #userId + '_' + #interval.code + '_' + #pageNumber + '_' + #pageSize",
               unless = "#interval == null || (#interval.duration != null && #interval.duration.toMinutes() < 15)")
    public PortfolioAnalysis analyzePortfolio(
            String portfolioId, 
            String userId, 
            Integer pageNumber, 
            Integer pageSize,
            TimeInterval interval) {
        try {
            Instant startProcessing = Instant.now();
            log.info("Starting portfolio analysis - Portfolio: {}, User: {}, Interval: {}, Page: {}, Size: {}", 
                    portfolioId, userId, interval != null ? interval.getCode() : "null", pageNumber, pageSize);
            
            PortfolioModel portfolio = portfolioService.getPortfolioById(UUID.fromString(portfolioId));
            if (portfolio == null) {
                log.error("Portfolio not found for ID: {}", portfolioId);
                return null;
            }

            List<AssetModel> equityHoldings = portfolio.getAssets().stream()
                .filter(asset -> AssetType.EQUITY.equals(asset.getAssetType()))
                .toList();

            List<StockPerformance> performances = calculateStockPerformances(equityHoldings, interval);
            
            // Calculate total portfolio metrics
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
            
            // Get top 5 gainers and losers
            List<StockPerformance> topFiveLosers = performances.subList(0, Math.min(DEFAULT_TOP_N, performances.size()));
            List<StockPerformance> topFiveGainers = new ArrayList<>(
                performances.subList(Math.max(0, performances.size() - DEFAULT_TOP_N), performances.size()));
            java.util.Collections.reverse(topFiveGainers);

            // Create paginated results
            PaginatedStockPerformance paginatedLosers = getPaginatedPerformances(performances, pageNumber, pageSize, false);
            PaginatedStockPerformance paginatedGainers = getPaginatedPerformances(performances, pageNumber, pageSize, true);

            Instant lastUpdated = Instant.now();
            PortfolioAnalysis analysis = PortfolioAnalysis.builder()
                .portfolioId(portfolioId)
                .userId(userId)
                .totalValue(totalValue)
                .totalGainLoss(totalGainLoss)
                .totalGainLossPercentage(totalGainLossPercentage)
                .topFiveGainers(topFiveGainers)
                .topFiveLosers(topFiveLosers)
                .gainers(paginatedGainers)
                .losers(paginatedLosers)
                .lastUpdated(lastUpdated)
                .build();

            Duration processingTime = Duration.between(startProcessing, lastUpdated);
            LocalDateTime processedTime = LocalDateTime.ofInstant(lastUpdated, ZoneId.systemDefault());
            
            log.info("Completed portfolio analysis - Fresh calculation - Portfolio: {}, Time: {}, Processing Duration: {}ms, " + 
                    "Total Assets: {}, Top Gainer: {}, Top Loser: {}", 
                portfolioId,
                processedTime,
                processingTime.toMillis(),
                performances.size(),
                !topFiveGainers.isEmpty() ? String.format("%s (%.2f%%)", 
                    topFiveGainers.get(0).getSymbol(), 
                    topFiveGainers.get(0).getGainLossPercentage()) : "None",
                !topFiveLosers.isEmpty() ? String.format("%s (%.2f%%)", 
                    topFiveLosers.get(0).getSymbol(), 
                    topFiveLosers.get(0).getGainLossPercentage()) : "None"
            );
            
            return analysis;

        } catch (Exception e) {
            log.error("Error analyzing portfolio {}: {}", portfolioId, e.getMessage(), e);
            return null;
        }
    }

    private List<StockPerformance> calculateStockPerformances(List<AssetModel> equityHoldings, TimeInterval interval) {
        Instant startTime = interval != null && interval.getDuration() != null ? 
            Instant.now().minus(interval.getDuration()) : null;

        return equityHoldings.stream()
            .map(asset -> getGainLossPercentage(asset, startTime))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }

    private StockPerformance getGainLossPercentage(AssetModel asset, Instant startTime) {
        Optional<StockPriceCache> latestPrice;
        if (startTime != null) {
            List<StockPriceCache> historicalPrices = stockPriceRedisService.getHistoricalPrices(
                asset.getSymbol(),
                startTime.atZone(java.time.ZoneOffset.UTC).toLocalDateTime(),
                Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDateTime()
            );
            if (historicalPrices.isEmpty()) {
                log.warn("No historical price data found for symbol: {} since {}", asset.getSymbol(), startTime);
                return null;
            }
            latestPrice = Optional.of(historicalPrices.get(historicalPrices.size() - 1));
        } else {
            latestPrice = stockPriceRedisService.getLatestPrice(asset.getSymbol());
        }

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
    }

    private PaginatedStockPerformance getPaginatedPerformances(
            List<StockPerformance> performances, 
            Integer pageNumber, 
            Integer pageSize,
            boolean isGainers) {
        
        int actualPageNumber = pageNumber != null ? pageNumber : 0;
        int actualPageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
        
        // If looking for gainers, reverse the list
        List<StockPerformance> sortedPerformances = new ArrayList<>(performances);
        if (isGainers) {
            java.util.Collections.reverse(sortedPerformances);
        }

        int totalElements = sortedPerformances.size();
        int totalPages = (int) Math.ceil((double) totalElements / actualPageSize);
        
        int start = actualPageNumber * actualPageSize;
        int end = Math.min(start + actualPageSize, totalElements);
        
        // Handle invalid page number
        if (start >= totalElements) {
            return PaginatedStockPerformance.builder()
                .content(List.of())
                .pageNumber(actualPageNumber)
                .pageSize(actualPageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .isLastPage(true)
                .build();
        }

        List<StockPerformance> pageContent = sortedPerformances.subList(start, end);
        
        return PaginatedStockPerformance.builder()
            .content(pageContent)
            .pageNumber(actualPageNumber)
            .pageSize(actualPageSize)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .isLastPage(end >= totalElements)
            .build();
    }
}
