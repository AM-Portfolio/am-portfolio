package com.portfolio.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.PaginatedStockPerformance;
import com.portfolio.model.StockPerformance;
import com.portfolio.model.StockPerformanceGroup;
import com.portfolio.model.cache.StockPriceCache;
import com.portfolio.model.TimeInterval;
import com.portfolio.redis.service.StockIndicesRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPerformanceService {
    private final StockIndicesRedisService stockPriceRedisService;
    private static final int DEFAULT_TOP_N = 5;
    private static final int DEFAULT_PAGE_SIZE = 5;

    public List<StockPerformance> calculateStockPerformances(List<EquityModel> equityHoldings, TimeInterval interval) {
        Instant startTime = interval != null && interval.getDuration() != null ? 
            Instant.now().minus(interval.getDuration()) : null;

        var performances = equityHoldings.stream()
            .map(asset -> getGainLossPercentage(asset, startTime))
            .filter(java.util.Objects::nonNull)
            .toList();

        return performances;
    }

    public StockPerformanceGroup calculatePerformanceGroup(
            List<StockPerformance> performances, 
            Integer pageNumber, 
            Integer pageSize,
            boolean isGainers) {
        
        // Sort by gain/loss percentage
        List<StockPerformance> sortedPerformances = new ArrayList<>(performances);
        sortedPerformances.sort(Comparator.comparing(StockPerformance::getGainLossPercentage));
        
        if (isGainers) {
            java.util.Collections.reverse(sortedPerformances);
        }

        // Get top performers
        List<StockPerformance> topPerformers = sortedPerformances.subList(
            0, Math.min(DEFAULT_TOP_N, sortedPerformances.size()));

        // Create paginated results
        PaginatedStockPerformance paginatedPerformers = getPaginatedPerformances(
            sortedPerformances, pageNumber, pageSize, isGainers);

        // Calculate statistics
        double[] percentages = sortedPerformances.stream()
            .mapToDouble(StockPerformance::getGainLossPercentage)
            .toArray();
        
        return StockPerformanceGroup.builder()
            .topPerformers(topPerformers)
            .allPerformers(paginatedPerformers)
            .averagePerformance(calculateAverage(percentages))
            .medianPerformance(calculateMedian(percentages))
            .bestPerformance(isGainers ? percentages[0] : percentages[percentages.length - 1])
            .worstPerformance(isGainers ? percentages[percentages.length - 1] : percentages[0])
            .totalCount(sortedPerformances.size())
            .build();
    }

    public double calculateCurrentValue(List<StockPerformance> performances) {
        return performances.stream()
            .mapToDouble(p -> p.getCurrentPrice() * p.getQuantity())
            .sum();
    }

    public double calculateHistoricalValue(List<StockPerformance> performances, Instant startTime) {
        return performances.stream()
            .mapToDouble(performance -> {
                List<StockPriceCache> historicalPrices = stockPriceRedisService.getHistoricalPrices(
                    performance.getSymbol(),
                    startTime.atZone(java.time.ZoneOffset.UTC).toLocalDateTime(),
                    Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDateTime()
                );
                
                if (!historicalPrices.isEmpty()) {
                    double historicalPrice = historicalPrices.get(0).getClosePrice();
                    return historicalPrice * performance.getQuantity();
                }
                return 0.0;
            })
            .sum();
    }

    private StockPerformance getGainLossPercentage(AssetModel asset, Instant startTime) {
        List<StockPriceCache> prices;
        if (startTime != null) {
            prices = stockPriceRedisService.getHistoricalPrices(
                asset.getSymbol(),
                startTime.atZone(java.time.ZoneOffset.UTC).toLocalDateTime(),
                LocalDateTime.now()
            );
            if (prices.isEmpty()) {
                log.warn("No historical price data found for symbol: {} since {}", asset.getSymbol(), startTime);
                return null;
            }
        } else {
            var latestPrice = stockPriceRedisService.getLatestPrice(asset.getSymbol());
            if (latestPrice.isEmpty()) {
                log.warn("No price data found for symbol: {}", asset.getSymbol());
                return null;
            }
            prices = List.of(latestPrice.get());
        }

        double currentPrice = prices.get(prices.size() - 1).getClosePrice();
        double averagePrice = asset.getAvgBuyingPrice();
        double quantity = asset.getQuantity();
        double gainLoss = (currentPrice - averagePrice) * quantity;
        double gainLossPercentage = ((currentPrice - averagePrice) / averagePrice) * 100;

        var stockPerformance = StockPerformance.builder()
            .symbol(asset.getSymbol())
            .quantity(quantity)
            .currentPrice(currentPrice)
            .averagePrice(averagePrice)
            .gainLoss(gainLoss)
            .gainLossPercentage(gainLossPercentage)
            .build();

        return stockPerformance;
    }

    private PaginatedStockPerformance getPaginatedPerformances(
            List<StockPerformance> performances, 
            Integer pageNumber, 
            Integer pageSize,
            boolean isGainers) {
        
        int actualPageNumber = pageNumber != null ? pageNumber : 0;
        int actualPageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
        
        List<StockPerformance> sortedPerformances = new ArrayList<>(performances);
        if (isGainers) {
            java.util.Collections.reverse(sortedPerformances);
        }

        int totalElements = sortedPerformances.size();
        int totalPages = (int) Math.ceil((double) totalElements / actualPageSize);
        
        int start = actualPageNumber * actualPageSize;
        int end = Math.min(start + actualPageSize, totalElements);
        
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

    private double calculateAverage(double[] values) {
        if (values.length == 0) return 0.0;
        return java.util.Arrays.stream(values).average().orElse(0.0);
    }

    private double calculateMedian(double[] values) {
        if (values.length == 0) return 0.0;
        java.util.Arrays.sort(values);
        int middle = values.length / 2;
        if (values.length % 2 == 0) {
            return (values[middle - 1] + values[middle]) / 2.0;
        }
        return values[middle];
    }
}
