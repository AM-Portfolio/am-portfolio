package com.portfolio.service.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.redis.service.StockIndicesRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioCalculator {

    private final MarketDataService marketDataService;
    private final StockIndicesRedisService stockPriceRedisService;

    /**
     * Enriches equity holdings with real-time market data (price, value, P&L).
     */
    /**
     * Enriches equity holdings with real-time market data (price, value, P&L) and
     * market cap info.
     */
    public List<EquityHoldings> enrichHoldings(List<EquityHoldings> equityHoldings) {
        log.debug("Enriching {} equity holdings with price, performance, and market cap data",
                equityHoldings != null ? equityHoldings.size() : 0);

        if (equityHoldings == null || equityHoldings.isEmpty()) {
            return equityHoldings;
        }

        // Extract all symbols
        List<String> symbols = equityHoldings.stream()
                .map(EquityHoldings::getSymbol)
                .filter(symbol -> symbol != null)
                .collect(Collectors.toList());

        // Fetch market data for all symbols
        Map<String, MarketData> marketDataMap = marketDataService.getMarketData(symbols);
        log.debug("Fetched market data for {} out of {} symbols", marketDataMap.size(), symbols.size());

        // Fetch market cap data for all symbols
        List<String> cleanedSymbols = symbols.stream()
                .map(marketDataService::cleanSymbol)
                .collect(Collectors.toList());
        
        Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> marketCapMap = marketDataService
                .getMarketCapData(cleanedSymbols);
        log.debug("Fetched market cap data for {} out of {} symbols", marketCapMap.size(), cleanedSymbols.size());

        // Enrich each holding
        return equityHoldings.stream()
                .map(holding -> enrichHolding(holding, marketDataMap, marketCapMap))
                .collect(Collectors.toList());
    }

    private EquityHoldings enrichHolding(EquityHoldings holding, Map<String, MarketData> marketDataMap,
            Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> marketCapMap) {
        String symbol = holding.getSymbol();
        if (symbol == null)
            return holding;

        // Enrich with market cap data
        String cleanedSymbol = marketDataService.cleanSymbol(symbol);
        if (marketCapMap != null && marketCapMap.containsKey(cleanedSymbol)) {
            var match = marketCapMap.get(cleanedSymbol);
            if (match.getMarketCapValue() != null) {
                // Convert Long to Double as EquityHoldings expects Double for marketCapValue
                holding.setMarketCapValue(match.getMarketCapValue().doubleValue());
            }
            if (match.getMarketCapType() != null) {
                holding.setMarketCapCategory(match.getMarketCapType());
                // Fallback for older existing field if needed
                if (holding.getMarketCap() == null) {
                    holding.setMarketCap(match.getMarketCapType());
                }
            }
        }

        // Use cleaned symbol for lookup as MarketDataService returns cleaned keys
        MarketData marketData = marketDataMap.get(cleanedSymbol);

        Double currentPrice = null;
        Double previousClosePrice = null;

        if (marketData != null) {
            // Determine current price
            if (marketData.getLastPrice() != null) {
                currentPrice = marketData.getLastPrice();
            } else if (marketData.getOhlc() != null) {
                currentPrice = marketData.getOhlc().getClose();
            }

            // Determine previous close (for day's gain/loss)
            if (marketData.getOhlc() != null) {
                previousClosePrice = marketData.getOhlc().getOpen(); // Using Open as proxy for prev close for today's
                                                                     // change within day
            }
        } else {
            // Fallback to Redis
            var latestPrice = stockPriceRedisService.getLatestPrice(symbol);
            if (latestPrice.isPresent()) {
                currentPrice = latestPrice.get().getClosePrice();
            }
        }

        if (currentPrice != null) {
            holding.setCurrentPrice(round(currentPrice));

            if (holding.getQuantity() != null) {
                double currentValue = currentPrice * holding.getQuantity();
                holding.setCurrentValue(round(currentValue));

                // Calculate Overall Gain/Loss
                if (holding.getInvestmentCost() != null && holding.getInvestmentCost() > 0) {
                    double gainLoss = currentValue - holding.getInvestmentCost();
                    double gainLossPct = (gainLoss / holding.getInvestmentCost()) * 100;
                    holding.setGainLoss(round(gainLoss));
                    holding.setGainLossPercentage(round(gainLossPct));
                }

                // Calculate Day's Gain/Loss (if previous close available)
                if (previousClosePrice != null && previousClosePrice > 0) {
                    double previousValue = previousClosePrice * holding.getQuantity();
                    double dayGainLoss = currentValue - previousValue;
                    double dayGainLossPct = (dayGainLoss / previousValue) * 100;
                    holding.setTodayGainLoss(round(dayGainLoss));
                    holding.setTodayGainLossPercentage(round(dayGainLossPct));

                    // Price change pct
                    double priceChange = currentPrice - previousClosePrice;
                    double priceChangePct = (priceChange / previousClosePrice) * 100;
                    holding.setPercentageChange(round(priceChangePct));
                }

                // Calculate weight (requires total value, done separately if needed,
                // but usually done after all holdings enriched.
                // We'll skip weight here as it requires aggregation of all holdings first)
            }
        }

        return holding;
    }

    /**
     * Calculates the portfolio summary based on enriched holdings.
     */
    public PortfolioSummaryV1 calculateSummary(List<EquityHoldings> enrichedHoldings, double totalInvestmentValue) {
        double currentValue = enrichedHoldings.stream()
                .filter(h -> h.getCurrentValue() != null)
                .mapToDouble(EquityHoldings::getCurrentValue)
                .sum();

        double totalGainLoss = currentValue - totalInvestmentValue;
        double totalGainLossPct = totalInvestmentValue > 0 ? (totalGainLoss / totalInvestmentValue) * 100 : 0.0;

        double todayGainLoss = enrichedHoldings.stream()
                .filter(h -> h.getTodayGainLoss() != null)
                .mapToDouble(EquityHoldings::getTodayGainLoss)
                .sum();

        // For today's gain %, we interpret it against the current value (as per
        // original logic logic usually)
        // OR against yesterday's value.
        // Original logic: currentValue > 0 ? (todayGainLoss / currentValue) * 100 :
        // 0.0;
        double todayGainLossPct = currentValue > 0 ? (todayGainLoss / currentValue) * 100 : 0.0;

        int gainers = count(enrichedHoldings, false, true);
        int losers = count(enrichedHoldings, false, false);
        int todayGainers = count(enrichedHoldings, true, true);
        int todayLosers = count(enrichedHoldings, true, false);

        return PortfolioSummaryV1.builder()
                .investmentValue(round(totalInvestmentValue))
                .currentValue(round(currentValue))
                .totalGainLoss(round(totalGainLoss))
                .totalGainLossPercentage(round(totalGainLossPct))
                .todayGainLoss(round(todayGainLoss))
                .todayGainLossPercentage(round(todayGainLossPct))
                .totalAssets(enrichedHoldings.size())
                .gainersCount(gainers)
                .losersCount(losers)
                .todayGainersCount(todayGainers)
                .todayLosersCount(todayLosers)
                .lastUpdated(LocalDateTime.now())
                .marketCapHoldings(groupMarketCap(enrichedHoldings))
                .sectorialHoldings(groupSector(enrichedHoldings))
                .build();
    }

    // Also add the weight calculation logic that was in PortfolioHoldingsService
    public void calculateWeights(List<EquityHoldings> holdings) {
        double totalValue = holdings.stream()
                .filter(h -> h.getCurrentValue() != null)
                .mapToDouble(EquityHoldings::getCurrentValue)
                .sum();

        if (totalValue > 0) {
            holdings.forEach(h -> {
                if (h.getCurrentValue() != null) {
                    h.setWeightInPortfolio(round((h.getCurrentValue() / totalValue) * 100));
                }
            });
        }
    }

    private int count(List<EquityHoldings> holdings, boolean today, boolean gainers) {
        return (int) holdings.stream().filter(h -> {
            Double val = today ? h.getTodayGainLoss() : h.getGainLoss();
            if (val == null)
                return false;
            return gainers ? val > 0 : val < 0;
        }).count();
    }

    private Map<String, List<EquityHoldings>> groupSector(List<EquityHoldings> holdings) {
        return holdings.stream()
                .filter(e -> e.getSector() != null)
                .collect(Collectors.groupingBy(EquityHoldings::getSector));
    }

    private Map<String, List<EquityHoldings>> groupMarketCap(List<EquityHoldings> holdings) {
        return holdings.stream()
                .filter(e -> e.getMarketCap() != null)
                .collect(Collectors.groupingBy(EquityHoldings::getMarketCap));
    }

    private Double round(Double value) {
        if (value == null)
            return null;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
