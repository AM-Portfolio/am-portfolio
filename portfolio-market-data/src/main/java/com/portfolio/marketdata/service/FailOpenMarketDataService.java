package com.portfolio.marketdata.service;

import com.am.common.amcommondata.document.price.StockPriceDocument;
import com.am.common.amcommondata.service.price.StockPriceHistoryMongoService;
import com.am.common.amcommondata.service.price.StockPriceMongoService;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.config.MarketDataApiConfig;
import com.portfolio.model.market.MarketData;
import com.portfolio.redis.service.PortfolioMarketDataRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Wraps {@link MarketDataService} OHLC with fail-open backfill from Redis/Mongo when waves time out.
 */
@Slf4j
@Service
@Primary
public class FailOpenMarketDataService extends MarketDataService {

    @Nullable
    private final PortfolioMarketDataRedisService redisService;

    @Nullable
    private final StockPriceMongoService mongoService;

    public FailOpenMarketDataService(
            MarketDataApiClient marketDataApiClient,
            @Nullable PortfolioMarketDataRedisService marketDataRedisService,
            @Nullable StockPriceMongoService stockPriceMongoService,
            @Nullable StockPriceHistoryMongoService stockPriceHistoryMongoService,
            Executor taskExecutor,
            Executor externalApiExecutor) {
        super(marketDataApiClient, marketDataRedisService, stockPriceMongoService,
                stockPriceHistoryMongoService, taskExecutor, externalApiExecutor);
        this.redisService = marketDataRedisService;
        this.mongoService = stockPriceMongoService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FailOpenMarketDataService(
            MarketDataApiClient marketDataApiClient,
            @Nullable PortfolioMarketDataRedisService marketDataRedisService,
            @Nullable StockPriceMongoService stockPriceMongoService,
            @Nullable StockPriceHistoryMongoService stockPriceHistoryMongoService,
            Executor taskExecutor,
            Executor externalApiExecutor,
            @Nullable MarketDataApiConfig config) {
        super(marketDataApiClient, marketDataRedisService, stockPriceMongoService,
                stockPriceHistoryMongoService, taskExecutor, externalApiExecutor, config);
        this.redisService = marketDataRedisService;
        this.mongoService = stockPriceMongoService;
    }

    @Override
    public Map<String, MarketData> getOhlcData(List<String> symbols, boolean refresh) {
        Map<String, MarketData> result = new HashMap<>(super.getOhlcData(symbols, refresh));
        backfillMissing(symbols, result);
        return result;
    }

    @Override
    public Map<String, MarketData> getOhlcData(List<String> symbols, String timeFrame, boolean refresh) {
        Map<String, MarketData> result = new HashMap<>(super.getOhlcData(symbols, timeFrame, refresh));
        backfillMissing(symbols, result);
        return result;
    }

    private void backfillMissing(List<String> symbols, Map<String, MarketData> merged) {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        List<String> misses = symbols.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(this::cleanSymbol)
                .filter(s -> {
                    MarketData md = merged.get(s);
                    return md == null || !hasUsablePrice(md);
                })
                .collect(Collectors.toList());
        if (misses.isEmpty()) {
            return;
        }
        Map<String, MarketData> stale = collectStaleTicksFromCaches(misses);
        stale.forEach((symbol, md) -> {
            if (!merged.containsKey(symbol) || !hasUsablePrice(merged.get(symbol))) {
                merged.put(symbol, md);
            }
        });
        if (!stale.isEmpty()) {
            log.info("[OHLC data] Fail-open: backfilled {}/{} symbols from Redis/Mongo stale ticks",
                    stale.size(), misses.size());
        }
    }

    private Map<String, MarketData> collectStaleTicksFromCaches(List<String> symbols) {
        Map<String, MarketData> result = new HashMap<>();
        if (redisService != null) {
            try {
                Map<String, MarketData> cached = redisService.getMarketData(symbols);
                if (cached != null) {
                    cached.forEach((symbol, md) -> {
                        if (hasUsablePrice(md)) {
                            result.put(symbol, md);
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("[OHLC fail-open] Redis read failed: {}", e.getMessage());
            }
        }

        List<String> missing = symbols.stream()
                .filter(s -> !result.containsKey(s))
                .collect(Collectors.toList());
        if (missing.isEmpty() || mongoService == null) {
            return result;
        }

        try {
            Map<String, StockPriceDocument> mongoPrices = mongoService.getPrices(missing);
            if (mongoPrices != null) {
                for (Map.Entry<String, StockPriceDocument> entry : mongoPrices.entrySet()) {
                    StockPriceDocument doc = entry.getValue();
                    Double usable = doc.getLastPrice() != null && doc.getLastPrice() > 0
                            ? doc.getLastPrice()
                            : (doc.getPreviousClose() != null && doc.getPreviousClose() > 0 ? doc.getPreviousClose() : null);
                    if (usable == null) {
                        continue;
                    }
                    result.put(doc.getSymbol(), MarketData.builder()
                            .symbol(doc.getSymbol())
                            .lastPrice(usable)
                            .previousClose(doc.getPreviousClose())
                            .timestamp(Instant.ofEpochMilli(
                                    doc.getTimestamp() != null ? doc.getTimestamp() : System.currentTimeMillis()))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("[OHLC fail-open] Mongo read failed: {}", e.getMessage());
        }
        return result;
    }

    private boolean hasUsablePrice(MarketData md) {
        if (md == null) {
            return false;
        }
        if (md.getLastPrice() != null && md.getLastPrice() > 0) {
            return true;
        }
        if (md.getPreviousClose() != null && md.getPreviousClose() > 0) {
            return true;
        }
        return md.getOhlc() != null && md.getOhlc().getClose() > 0;
    }

    private String cleanSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return symbol;
        }
        int colonIndex = symbol.indexOf(':');
        if (colonIndex > 0 && colonIndex < symbol.length() - 1) {
            return symbol.substring(colonIndex + 1);
        }
        return symbol;
    }
}
