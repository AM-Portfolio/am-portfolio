package com.portfolio.redis.service;

import com.portfolio.model.analytics.intelligence.PortfolioIntelligenceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioIntelligenceRedisServiceTest {

    @Mock
    private RedisTemplate<String, PortfolioIntelligenceResponse> template;

    @Mock
    private ValueOperations<String, PortfolioIntelligenceResponse> valueOps;

    @Test
    void putThenGet_hitsL1WithoutRedisRead() {
        when(template.opsForValue()).thenReturn(valueOps);
        PortfolioIntelligenceRedisService svc = new PortfolioIntelligenceRedisService(template);
        ReflectionTestUtils.setField(svc, "isRedisEnabled", true);
        ReflectionTestUtils.setField(svc, "keyPrefix", "portfolio:intel:v1:");
        ReflectionTestUtils.setField(svc, "ttlSeconds", 90);

        PortfolioIntelligenceResponse body = PortfolioIntelligenceResponse.builder()
                .portfolioId("ALL")
                .confidence(0.9)
                .build();
        svc.put("user:u1:all", body);

        assertThat(svc.get("user:u1:all")).contains(body);
        // L1 serves second get — Redis get must not be called again after put's set
        verify(valueOps).set(eq("portfolio:intel:v1:user:u1:all"), eq(body), any(Duration.class));
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void get_whenRedisNull_stillUsesL1AfterPut() {
        PortfolioIntelligenceRedisService svc = new PortfolioIntelligenceRedisService(null);
        ReflectionTestUtils.setField(svc, "isRedisEnabled", true);
        ReflectionTestUtils.setField(svc, "keyPrefix", "portfolio:intel:v1:");
        ReflectionTestUtils.setField(svc, "ttlSeconds", 90);

        PortfolioIntelligenceResponse body = PortfolioIntelligenceResponse.builder()
                .portfolioId("ALL")
                .build();
        svc.put("user:u1:all", body);

        assertThat(svc.get("user:u1:all")).contains(body);
    }
}
