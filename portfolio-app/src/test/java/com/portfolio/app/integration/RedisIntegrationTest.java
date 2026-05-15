package com.portfolio.app.integration;

import com.am.common.investment.model.equity.EquityPrice;
import com.portfolio.model.cache.StockPriceCache;
import com.portfolio.redis.service.StockIndicesRedisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RedisIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private StockIndicesRedisService stockIndicesRedisService;

    @Test
    void testCacheAndRetrieveLatestPrice() throws Exception {
        // Prepare mock data
        EquityPrice price = new EquityPrice();
        price.setSymbol("RELIANCE");
        price.setLastPrice(2500.0);
        price.setTime(Instant.now());

        // Cache the price update
        // Note: cacheEquityPriceUpdateBatch is @Async and returns CompletableFuture
        stockIndicesRedisService.cacheEquityPriceUpdateBatch(List.of(price)).get();

        // Retrieve from Redis
        Optional<StockPriceCache> cachedPrice = stockIndicesRedisService.getLatestPrice("RELIANCE");
        
        // Verify
        assertTrue(cachedPrice.isPresent(), "Price should be present in cache");
        assertEquals(2500.0, cachedPrice.get().getClosePrice(), "Close price mismatch");
        assertEquals("RELIANCE", cachedPrice.get().getSymbol(), "Symbol mismatch");
    }

    @Test
    void testHistoricalPriceRetrieval() throws Exception {
        Instant now = Instant.now();
        EquityPrice p1 = new EquityPrice();
        p1.setSymbol("INFY");
        p1.setLastPrice(1500.0);
        p1.setTime(now.minusSeconds(3600));

        EquityPrice p2 = new EquityPrice();
        p2.setSymbol("INFY");
        p2.setLastPrice(1550.0);
        p2.setTime(now);

        stockIndicesRedisService.cacheEquityPriceUpdateBatch(List.of(p1, p2)).get();

        // Use Awaitility to ensure historical prices are indexed correctly
        org.awaitility.Awaitility.await()
                .atMost(5, java.util.concurrent.TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<StockPriceCache> historical = stockIndicesRedisService.getHistoricalPrices("INFY", null, null);
                    assertFalse(historical.isEmpty(), "Historical prices should not be empty");
                    assertEquals(1550.0, historical.get(0).getClosePrice(), "Should return the most recent price");
                });
    }
}
