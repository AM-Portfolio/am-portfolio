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

        java.util.concurrent.CompletableFuture<Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch>> marketCapFuture = null;
        if (!missingMarketCap.isEmpty()) {
            marketCapFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> marketDataService.getMarketCapData(missingMarketCap), taskExecutor)
                    .completeOnTimeout(Map.of(), 4, java.util.concurrent.TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log.error("Market cap fetch failed: {}", ex.getMessage());
                        return Map.of();
                    });
            
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
        }


        // 2. Data Lookup (Redis -> Mongo -> API handled automatically by MarketDataService)
        Map<String, MarketData> apiData = Map.of();
        try {
            apiData = marketDataService.getMarketData(symbols);
        } catch (Exception e) {
            log.error("MarketDataService fetch failed: {}", e.getMessage());
        }
        
        final Map<String, MarketData> finalApiDataForEnrich = (apiData == null) ? Map.of() : apiData;

        // Market Cap bounded wait (1.5 seconds) to ensure UI gets data on cold starts
        Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> finalMarketCapMapTemp = Map.of();
        if (marketCapFuture != null) {
            try {
                finalMarketCapMapTemp = marketCapFuture.get(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.warn("Market cap fetch timed out at 1.5s wait. Proceeding without it.");
            } catch (Exception e) {
                log.error("Error waiting for market cap data", e);
            }
        }
        final Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> finalMarketCapMap = finalMarketCapMapTemp;

        return equityHoldings.stream()
                .map(holding -> enrichHolding(holding, finalApiDataForEnrich, finalMarketCapMap, cachedMarketCap))
                .collect(Collectors.toList());
    }

    private EquityHoldings enrichHolding(EquityHoldings holding, 
            Map<String, MarketData> marketDataMap,
            Map<String, com.portfolio.marketdata.model.BatchSearchResponse.SecurityMatch> marketCapMap,
            Map<String, MarketCapDocument> cachedMarketCap) {
            
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
        if (marketDataMap != null) {
            MarketData apiItem = marketDataMap.get(symbol);
            if (apiItem == null) {
                // Try looking up with cleaned symbol (strips prefix and suffix)
                String cleaned = cleanSymbol(symbol);
                apiItem = marketDataMap.get(cleaned);
            }
            
            if (apiItem != null) {
                Double lastPrice = apiItem.getLastPrice();
                if (lastPrice != null && lastPrice > 0) {
                    currentPrice = lastPrice;
                } else if (apiItem.getOhlc() != null && apiItem.getOhlc().getClose() > 0) {
                    currentPrice = apiItem.getOhlc().getClose();
                }
                
                Double prevClose = apiItem.getPreviousClose();
                if (prevClose != null && prevClose > 0) {
                    previousClosePrice = prevClose;
                } else if (apiItem.getOhlc() != null && apiItem.getOhlc().getClose() > 0) {
                    log.debug("previousClose missing for {}. Falling back to OHLC close.", symbol);
                    previousClosePrice = apiItem.getOhlc().getClose();
                } else {
                    log.warn("Missing previousClose for symbol {}. Daily P&L will not be calculated.", symbol);
                }
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

    private String cleanSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return symbol;
        }
        int colonIndex = symbol.indexOf(':');
        String cleaned = symbol;
        if (colonIndex > 0 && colonIndex < symbol.length() - 1) {
            cleaned = symbol.substring(colonIndex + 1);
        }
        return com.portfolio.model.util.SymbolResolver.normalize(cleaned);
    }
}
