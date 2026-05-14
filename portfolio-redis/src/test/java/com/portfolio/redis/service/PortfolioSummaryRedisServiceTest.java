package com.portfolio.redis.service;

import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioSummaryRedisServiceTest {

    @Mock private RedisTemplate<String, PortfolioSummaryV1> redisTemplate;
    @Mock private ValueOperations<String, PortfolioSummaryV1> valueOps;
    @InjectMocks private PortfolioSummaryRedisService service;

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "portfolioSummaryTtl", 600);
        ReflectionTestUtils.setField(service, "portfolioSummaryKeyPrefix", "ps:");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private TimeInterval interval(String code, Duration d) {
        TimeInterval ti = mock(TimeInterval.class);
        when(ti.getCode()).thenReturn(code);
        lenient().when(ti.getDuration()).thenReturn(d);
        return ti;
    }

    @Test void getLatestSummary_found_fresh() {
        TimeInterval ti = interval("1d", Duration.ofDays(1));
        PortfolioSummaryV1 summary = new PortfolioSummaryV1();
        summary.setLastUpdated(LocalDateTime.now()); // fresh
        when(valueOps.get("ps:user1:1d")).thenReturn(summary);

        Optional<PortfolioSummaryV1> result = service.getLatestSummary("user1", ti);
        assertTrue(result.isPresent());
    }

    @Test void getLatestSummary_found_stale_deletesAndReturnsEmpty() {
        TimeInterval ti = interval("1h", Duration.ofHours(1));
        PortfolioSummaryV1 summary = new PortfolioSummaryV1();
        summary.setLastUpdated(LocalDateTime.now().minusDays(2)); // stale
        when(valueOps.get("ps:user1:1h")).thenReturn(summary);
        when(redisTemplate.delete("ps:user1:1h")).thenReturn(true);

        Optional<PortfolioSummaryV1> result = service.getLatestSummary("user1", ti);
        assertTrue(result.isEmpty());
        verify(redisTemplate).delete("ps:user1:1h");
    }

    @Test void getLatestSummary_notFound() {
        TimeInterval ti = interval("1d", Duration.ofDays(1));
        when(valueOps.get(anyString())).thenReturn(null);
        assertTrue(service.getLatestSummary("user1", ti).isEmpty());
    }

    @Test void getLatestSummary_nullInterval_returnsFoundSummary() {
        PortfolioSummaryV1 summary = new PortfolioSummaryV1();
        summary.setLastUpdated(LocalDateTime.now());
        when(valueOps.get("ps:user1:all")).thenReturn(summary);
        Optional<PortfolioSummaryV1> result = service.getLatestSummary("user1", null);
        assertTrue(result.isPresent());
    }

    @Test void getLatestSummary_redisError_returnsEmpty() {
        TimeInterval ti = interval("1d", Duration.ofDays(1));
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("conn err"));
        assertTrue(service.getLatestSummary("user1", ti).isEmpty());
    }

    @Test void getLatestSummary_nullDuration_returnsWithoutFreshnessCheck() {
        TimeInterval ti = mock(TimeInterval.class);
        when(ti.getCode()).thenReturn("all");
        when(ti.getDuration()).thenReturn(null);
        PortfolioSummaryV1 summary = new PortfolioSummaryV1();
        summary.setLastUpdated(LocalDateTime.now());
        when(valueOps.get("ps:user1:all")).thenReturn(summary);
        assertTrue(service.getLatestSummary("user1", ti).isPresent());
    }
}
