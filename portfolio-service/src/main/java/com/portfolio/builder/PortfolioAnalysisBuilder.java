package com.portfolio.builder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.portfolio.model.StockPerformance;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.PerformanceMetrics;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioSummary;
import com.portfolio.service.StockPerformanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalysisBuilder {
    private final StockPerformanceService stockPerformanceService;

    public PortfolioSummary buildSummary(List<StockPerformance> performances, String portfolioId, String userId) {
        double totalValue = stockPerformanceService.calculateCurrentValue(performances);
        double totalCost = performances.stream()
            .mapToDouble(p -> p.getAveragePrice() * p.getQuantity())
            .sum();
        
        double totalGainLoss = totalValue - totalCost;
        double totalGainLossPercentage = (totalGainLoss / totalCost) * 100;

        long gainersCount = performances.stream()
            .filter(p -> p.getGainLossPercentage() > 0)
            .count();
            
        double averageGainLossPercentage = performances.stream()
            .mapToDouble(StockPerformance::getGainLossPercentage)
            .average()
            .orElse(0.0);

        return PortfolioSummary.builder()
            .portfolioId(portfolioId)
            .totalValue(totalValue)
            .totalCost(totalCost)
            .totalGainLoss(totalGainLoss)
            .totalGainLossPercentage(totalGainLossPercentage)
            .totalAssets(performances.size())
            .gainersCount((int) gainersCount)
            .losersCount(performances.size() - (int) gainersCount)
            .averageGainLossPercentage(averageGainLossPercentage)
            .lastUpdated(Instant.now())
            .build();
    }

    /**
     * Intervals that need hist lookbacks for analysis. Sub-day windows (5m–1H) are
     * skipped on the OVERALL path — they caused up to 11 sequential market-data hist
     * waves and Cloudflare 524s (~125s). When a specific interval is requested, only
     * that interval is computed.
     */
    static List<TimeInterval> intervalsForMetrics(TimeInterval currentInterval) {
        if (currentInterval != null && currentInterval.getDuration() != null) {
            return List.of(currentInterval);
        }
        return List.of(
                TimeInterval.ONE_DAY,
                TimeInterval.ONE_WEEK,
                TimeInterval.ONE_MONTH,
                TimeInterval.THREE_MONTHS,
                TimeInterval.SIX_MONTHS,
                TimeInterval.ONE_YEAR);
    }

    public Map<TimeInterval, PerformanceMetrics> buildTimeBasedMetrics(
            List<StockPerformance> performances, 
            TimeInterval currentInterval) {
        Map<TimeInterval, PerformanceMetrics> metrics = new HashMap<>();
        double endValue = stockPerformanceService.calculateCurrentValue(performances);
        Instant endTime = Instant.now();

        for (TimeInterval interval : intervalsForMetrics(currentInterval)) {
            Instant startTime = endTime.minus(interval.getDuration());
            double startValue = stockPerformanceService.calculateHistoricalValue(performances, startTime);

            double valueChange = endValue - startValue;
            double percentageChange = startValue != 0 ? (valueChange / startValue) * 100 : 0;

            metrics.put(interval, PerformanceMetrics.builder()
                .interval(interval)
                .startValue(startValue)
                .endValue(endValue)
                .valueChange(valueChange)
                .percentageChange(percentageChange)
                .startTime(startTime)
                .endTime(endTime)
                .build());
        }

        return metrics;
    }

    public PortfolioAnalysis buildAnalysis(
            String portfolioId,
            String userId,
            List<StockPerformance> performances,
            Integer pageNumber,
            Integer pageSize,
            TimeInterval interval,
            Instant startTime) {
        
        // Build core components
        PortfolioSummary summary = buildSummary(performances, portfolioId, userId);
        var gainersGroup = stockPerformanceService.calculatePerformanceGroup(performances, pageNumber, pageSize, true);
        var losersGroup = stockPerformanceService.calculatePerformanceGroup(performances, pageNumber, pageSize, false);
        var timeBasedMetrics = buildTimeBasedMetrics(performances, interval);

        Instant lastUpdated = Instant.now();
        Duration processingTime = Duration.between(startTime, lastUpdated);
        LocalDateTime processedTime = LocalDateTime.ofInstant(lastUpdated, ZoneId.systemDefault());
        
        log.info("Built portfolio analysis - Portfolio: {}, Time: {}, Processing Duration: {}ms", 
            portfolioId, processedTime, processingTime.toMillis());

        return PortfolioAnalysis.builder()
            .portfolioId(portfolioId)
            .summary(summary)
            .gainers(gainersGroup)
            .losers(losersGroup)
            .timeBasedMetrics(timeBasedMetrics)
            .currentInterval(interval)
            .lastUpdated(lastUpdated)
            .build();
    }
}
