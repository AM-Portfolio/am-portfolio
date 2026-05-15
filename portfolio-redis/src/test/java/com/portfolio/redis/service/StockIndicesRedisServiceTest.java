package com.portfolio.redis.service;

import com.portfolio.model.cache.StockPriceCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockIndicesRedisServiceTest {

    @Mock private RedisTemplate<String, StockPriceCache> redisTemplate;
    @Mock private ValueOperations<String, StockPriceCache> valueOps;
    @InjectMocks private StockIndicesRedisService service;

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "stockTtl", 300);
        ReflectionTestUtils.setField(service, "stockKeyPrefix", "stock:");
        ReflectionTestUtils.setField(service, "historicalKeyPrefix", "hist:");
        ReflectionTestUtils.setField(service, "historicalTtl", 3600);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test void getLatestPrice_found() {
        StockPriceCache cache = StockPriceCache.builder().symbol("AAPL").closePrice(150.0).build();
        when(valueOps.get("stock:AAPL")).thenReturn(cache);
        Optional<StockPriceCache> result = service.getLatestPrice("AAPL");
        assertTrue(result.isPresent());
        assertEquals(150.0, result.get().getClosePrice());
    }

    @Test void getLatestPrice_notFound() {
        when(valueOps.get("stock:MISSING")).thenReturn(null);
        assertTrue(service.getLatestPrice("MISSING").isEmpty());
    }

    @Test void getLatestPrice_redisError_returnsEmpty() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("connection error"));
        assertTrue(service.getLatestPrice("ERR").isEmpty());
    }

    @Test void getHistoricalPrices_findsLatest() {
        StockPriceCache cache = StockPriceCache.builder().symbol("AAPL")
                .timestamp(Instant.now()).closePrice(150.0).build();
        when(redisTemplate.keys("hist:AAPL:*")).thenReturn(Set.of("hist:AAPL:2024-01-01"));
        when(valueOps.get("hist:AAPL:2024-01-01")).thenReturn(cache);

        List<StockPriceCache> result = service.getHistoricalPrices("AAPL",
                LocalDateTime.of(2024, 1, 1, 0, 0), LocalDateTime.of(2024, 12, 31, 0, 0));
        assertEquals(1, result.size());
    }

    @Test void getHistoricalPrices_noKeys() {
        when(redisTemplate.keys("hist:SYM:*")).thenReturn(Collections.emptySet());
        assertTrue(service.getHistoricalPrices("SYM", LocalDateTime.now(), LocalDateTime.now()).isEmpty());
    }

    @Test void getHistoricalPrices_redisError() {
        when(redisTemplate.keys(anyString())).thenThrow(new RuntimeException("error"));
        assertTrue(service.getHistoricalPrices("ERR", LocalDateTime.now(), LocalDateTime.now()).isEmpty());
    }

    @Test void getHistoricalPrices_nullTimestampSkipped() {
        StockPriceCache cache = StockPriceCache.builder().symbol("A").timestamp(null).build();
        when(redisTemplate.keys("hist:A:*")).thenReturn(Set.of("hist:A:1"));
        when(valueOps.get("hist:A:1")).thenReturn(cache);
        assertTrue(service.getHistoricalPrices("A", LocalDateTime.now(), LocalDateTime.now()).isEmpty());
    }

    @Test void deleteOldPrices_deletesOldEntries() {
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        StockPriceCache cache = StockPriceCache.builder().symbol("A").timestamp(old).build();
        when(redisTemplate.keys("hist:A:*")).thenReturn(Set.of("hist:A:1"));
        when(valueOps.get("hist:A:1")).thenReturn(cache);
        when(redisTemplate.delete("hist:A:1")).thenReturn(true);

        service.deleteOldPrices("A", LocalDateTime.of(2025, 1, 1, 0, 0));
        verify(redisTemplate).delete("hist:A:1");
    }

    @Test void deleteOldPrices_redisError_doesNotThrow() {
        when(redisTemplate.keys(anyString())).thenThrow(new RuntimeException("err"));
        assertDoesNotThrow(() -> service.deleteOldPrices("A", LocalDateTime.now()));
    }

    @Test void cacheEquityPriceUpdateBatch_nullInput() {
        var future = service.cacheEquityPriceUpdateBatch(null);
        assertNotNull(future);
    }

    @Test void cacheEquityPriceUpdateBatch_emptyInput() {
        var future = service.cacheEquityPriceUpdateBatch(List.of());
        assertNotNull(future);
    }
}
