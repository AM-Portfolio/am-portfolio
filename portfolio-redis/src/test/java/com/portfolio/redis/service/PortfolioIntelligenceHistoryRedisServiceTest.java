package com.portfolio.redis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.portfolio.model.analytics.intelligence.CachedIntelligenceHistory;

@ExtendWith(MockitoExtension.class)
class PortfolioIntelligenceHistoryRedisServiceTest {

    @Mock
    private RedisTemplate<String, CachedIntelligenceHistory> template;
    @Mock
    private ValueOperations<String, CachedIntelligenceHistory> valueOps;

    private PortfolioIntelligenceHistoryRedisService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioIntelligenceHistoryRedisService(template);
        ReflectionTestUtils.setField(service, "isRedisEnabled", true);
        ReflectionTestUtils.setField(service, "keyPrefix", "portfolio:intel-hist:v1:");
        ReflectionTestUtils.setField(service, "ttlSeconds", 300);
        when(template.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void put_and_get_l1_skips_redis_on_second_read() {
        CachedIntelligenceHistory hist = CachedIntelligenceHistory.builder()
                .historyPoints(60)
                .beta(0.85)
                .build();

        service.put("p1", hist);

        verify(valueOps).set(eq("portfolio:intel-hist:v1:p1"), any(CachedIntelligenceHistory.class), eq(Duration.ofSeconds(300)));

        Optional<CachedIntelligenceHistory> got = service.get("p1");
        assertThat(got).isPresent();
        assertThat(got.get().getBeta()).isEqualTo(0.85);
        // L1 hit — no second Redis get
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void put_ignores_unusable_beta() {
        service.put("p1", CachedIntelligenceHistory.builder().historyPoints(5).beta(0.9).build());
        verify(valueOps, never()).set(anyString(), any(), any());
        assertThat(service.get("p1")).isEmpty();
    }

    @Test
    void put_accepts_non_positive_finite_beta() {
        CachedIntelligenceHistory hist = CachedIntelligenceHistory.builder()
                .historyPoints(40)
                .beta(-0.4)
                .build();
        service.put("p-neg", hist);
        verify(valueOps).set(eq("portfolio:intel-hist:v1:p-neg"), any(CachedIntelligenceHistory.class), eq(Duration.ofSeconds(300)));
        assertThat(service.get("p-neg")).isPresent();
        assertThat(service.get("p-neg").get().getBeta()).isEqualTo(-0.4);
    }

    @Test
    void put_ignores_null_beta_even_with_enough_points() {
        service.put("p-null", CachedIntelligenceHistory.builder().historyPoints(40).beta(null).build());
        verify(valueOps, never()).set(anyString(), any(), any());
        assertThat(service.get("p-null")).isEmpty();
    }

    @Test
    void get_reads_l2_when_l1_empty() {
        CachedIntelligenceHistory hist = CachedIntelligenceHistory.builder()
                .historyPoints(40)
                .beta(1.2)
                .build();
        when(valueOps.get("portfolio:intel-hist:v1:p2")).thenReturn(hist);

        Optional<CachedIntelligenceHistory> got = service.get("p2");
        assertThat(got).isPresent();
        assertThat(got.get().getBeta()).isEqualTo(1.2);
    }
}
