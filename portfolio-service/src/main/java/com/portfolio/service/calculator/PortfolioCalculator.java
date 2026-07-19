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
import com.am.common.amcommondata.service.price.StockPriceMongoService;
import com.am.common.amcommondata.document.price.StockPriceDocument;
import com.am.common.amcommondata.service.marketcap.MarketCapMongoService;
import com.am.common.amcommondata.document.marketcap.MarketCapDocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import io.micrometer.observation.annotation.Observed;

@Component
@Slf4j
public class PortfolioCalculator {

    private final MarketDataService marketDataService;
    private final MarketCapMongoService marketCapMongoService;
    private final StockPriceMongoService stockPriceMongoService;
    private final java.util.concurrent.Executor taskExecutor;

    public PortfolioCalculator(
            MarketDataService marketDataService,
            MarketCapMongoService marketCapMongoService,
            StockPriceMongoService stockPriceMongoService,
            @org.springframework.beans.factory.annotation.Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor) {
        this.marketDataService = marketDataService;
        this.marketCapMongoService = marketCapMongoService;
        this.stockPriceMongoService = stockPriceMongoService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Enriches equity holdings with real-time market data (price, value, P&L) and
     * market cap info using a 3-Tier Waterfall (Redis -> Mongo -> API).
     */
    @Observed(name = "portfolio.enrich.holdings", contextualName = "enrich-equity-holdings")
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

        // 1. Fetch market cap data (MongoDB Cache first, then API)
        Map<String, MarketCapDocument> cachedMarketCap = marketCapMongoService.getBySymbols(symbols);
        List<String> missingMarketCap = symbols.stream()
                .filter(s -> !cachedMarketCap.containsKey(s))
                .collect(Collectors.toList());

        java.util.concurrent.CompletableFuture<Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch>> marketCapFuture = missingMarketCap.isEmpty()
                ? java.util.concurrent.CompletableFuture.completedFuture(Map.of())
                : java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> marketDataService.getMarketCapData(missingMarketCap), taskExecutor)
                        .completeOnTimeout(Map.of(), 4, java.util.concurrent.TimeUnit.SECONDS);

        // Self-heal: Cache API results to MongoDB
        marketCapFuture.thenAcceptAsync(apiResults -> {
            if (apiResults != null && !apiResults.isEmpty()) {
                List<MarketCapDocument> docs = apiResults.values().stream()
                        .map(match -> MarketCapDocument.builder()
                                .symbol(match.getSymbol())
                                .sector(match.getSector())
                                .industry(match.getIndustry())
                                .marketCapType(match.getMarketCapType())
                                .marketCapValue(match.getMarketCapValue() != null ? match.getMarketCapValue().doubleValue() : null)
                                .companyName(match.getCompanyName())
                                .updatedAt(LocalDateTime.now())
                                .build())
                        .collect(Collectors.toList());
                marketCapMongoService.saveAll(docs);
            }
        }, taskExecutor);

        // 2. 2-Tier Price Lookup Waterfall

        // Tier 1: MongoDB (Live prices updated by Kafka)
        final Map<String, StockPriceDocument> mongoData = stockPriceMongoService.getPrices(symbols);
        log.info("MongoDB price hit: {}/{} symbols", mongoData.size(), symbols.size());

        // Find missing for Tier 2
        List<String> missingFromMongo = symbols.stream()
                .filter(s -> !mongoData.containsKey(s))
                .collect(Collectors.toList());

        // Tier 2: API
        Map<String, MarketData> apiData = Map.of();
        if (!missingFromMongo.isEmpty()) {
            log.info("Cache miss for {} symbols in Mongo. Calling am-market API.", missingFromMongo.size());
            try {
                apiData = marketDataService.getMarketData(missingFromMongo);

                // Self-heal: Cache API results to MongoDB so next time it hits Tier 2
                if (apiData != null && !apiData.isEmpty()) {
                    final Map<String, MarketData> finalApiData = apiData;
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        List<StockPriceDocument> docs = finalApiData.values().stream()
                                .filter(md -> md != null && md.getLastPrice() != null)
                                .map(md -> StockPriceDocument.builder()
                                        .symbol(md.getSymbol())
                                        .lastPrice(md.getLastPrice())
                                        .previousClose(md.getPreviousClose())
                                        .openPrice(md.getOhlc() != null ? md.getOhlc().getOpen() : null)
                                        .highPrice(md.getOhlc() != null ? md.getOhlc().getHigh() : null)
                                        .lowPrice(md.getOhlc() != null ? md.getOhlc().getLow() : null)
                                        .timestamp(md.getTimestamp() != null ? md.getTimestamp().toEpochMilli() : System.currentTimeMillis())
                                        .updatedAt(LocalDateTime.now())
                                        .build())
                                .collect(Collectors.toList());
                        stockPriceMongoService.saveAll(docs);
                    }, taskExecutor);
                }
            } catch (Exception e) {
                log.error("API fetch failed for missing symbols: {}", e.getMessage());
            }
        }
        final Map<String, MarketData> finalApiDataForEnrich = (apiData == null) ? Map.of() : apiData;

        // Wait for Market Cap
        Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> marketCapMap;
        try {
            marketCapMap = marketCapFuture.join();
        } catch (Exception e) {
            marketCapMap = Map.of();
        }

        // 3. Enrich
        final Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> finalMarketCapMap = marketCapMap;

        return equityHoldings.stream()
                .map(holding -> enrichHolding(holding, finalApiDataForEnrich, finalMarketCapMap, cachedMarketCap, mongoData))
                .collect(Collectors.toList());
    }

    private EquityHoldings enrichHolding(EquityHoldings holding, 
            Map<String, MarketData> marketDataMap,
            Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> marketCapMap,
            Map<String, MarketCapDocument> cachedMarketCap,
            Map<String, StockPriceDocument> mongoPricesMap) {
            
        String symbol = holding.getSymbol();
        if (symbol == null)
            return holding;

        // Enrich with market cap data
        if (cachedMarketCap != null && cachedMarketCap.containsKey(symbol)) {
            var cache = cachedMarketCap.get(symbol);
            if (cache.getMarketCapValue() != null) {
                holding.setMarketCapValue(cache.getMarketCapValue());
            }
            if (cache.getMarketCapType() != null) {
                holding.setMarketCapCategory(cache.getMarketCapType());
                if (holding.getMarketCap() == null) {
                    holding.setMarketCap(cache.getMarketCapType());
                }
            }
            if (cache.getCompanyName() != null) {
                holding.setName(cache.getCompanyName());
            }
            if (cache.getSector() != null) {
                holding.setSector(cache.getSector());
            }
            if (cache.getIndustry() != null) {
                holding.setIndustry(cache.getIndustry());
            }
        } else if (marketCapMap != null && marketCapMap.containsKey(symbol)) {
            var match = marketCapMap.get(symbol);
            if (match.getMarketCapValue() != null) {
                holding.setMarketCapValue(match.getMarketCapValue().doubleValue());
            }
            if (match.getMarketCapType() != null) {
                holding.setMarketCapCategory(match.getMarketCapType());
                if (holding.getMarketCap() == null) {
                    holding.setMarketCap(match.getMarketCapType());
                }
            }
            if (match.getCompanyName() != null) {
                holding.setName(match.getCompanyName());
            }
            if (match.getSector() != null) {
                holding.setSector(match.getSector());
            }
            if (match.getIndustry() != null) {
                holding.setIndustry(match.getIndustry());
            }
        }

        Double currentPrice = null;
        Double previousClosePrice = null;

        // Waterfall Price Assignment
        if (mongoPricesMap != null && mongoPricesMap.containsKey(symbol)) {
            var mongoItem = mongoPricesMap.get(symbol);
            currentPrice = mongoItem.getLastPrice();
            previousClosePrice = mongoItem.getPreviousClose();
        } else if (marketDataMap != null && marketDataMap.containsKey(symbol)) {
            var apiItem = marketDataMap.get(symbol);
            if (apiItem.getLastPrice() != null) {
                currentPrice = apiItem.getLastPrice();
            } else if (apiItem.getOhlc() != null) {
                currentPrice = apiItem.getOhlc().getClose();
            }
            if (apiItem.getPreviousClose() != null && apiItem.getPreviousClose() > 0) {
                previousClosePrice = apiItem.getPreviousClose();
            } else if (apiItem.getOhlc() != null && apiItem.getOhlc().getClose() > 0) {
                log.debug("previousClose missing for {}. Falling back to OHLC close.", symbol);
                previousClosePrice = apiItem.getOhlc().getClose();
            } else {
                log.warn("Missing previousClose for symbol {}. Daily P&L will not be calculated.", symbol);
            }
        }

        // Local development fallback to prevent UI from showing null values, but avoid fabricating Daily P&L
        if (currentPrice == null || currentPrice == 0.0) {
            log.debug("No market data for {}. Using investment cost as price fallback.", symbol);
            if (holding.getAverageBuyingPrice() != null && holding.getAverageBuyingPrice() > 0) {
                currentPrice = holding.getAverageBuyingPrice();
            } else if (holding.getInvestmentCost() != null && holding.getQuantity() != null && holding.getQuantity() > 0) {
                double impliedAvgPrice = holding.getInvestmentCost() / holding.getQuantity();
                currentPrice = impliedAvgPrice;
            } else {
                currentPrice = 0.0;
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
            }
        }

        return holding;
    }

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

        double previousValue = currentValue - todayGainLoss;
        double todayGainLossPct = previousValue > 0 ? (todayGainLoss / previousValue) * 100 : 0.0;

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
