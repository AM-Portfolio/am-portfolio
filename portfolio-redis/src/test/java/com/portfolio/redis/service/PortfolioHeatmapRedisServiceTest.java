package com.portfolio.redis.service;

import com.portfolio.redis.session.CashSessionClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioHeatmapRedisServiceTest {

    @Test
    void resolveTtl_cashHoursUses90Seconds() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, com.portfolio.model.analytics.Heatmap> template = mock(RedisTemplate.class);
        CashSessionClock clock = mock(CashSessionClock.class);
        when(clock.isCashOpen()).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<CashSessionClock> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(clock);

        PortfolioHeatmapRedisService service = new PortfolioHeatmapRedisService(template, provider);
        ReflectionTestUtils.setField(service, "portfolioHeatmapTtl", 900);
        ReflectionTestUtils.setField(service, "portfolioHeatmapCashHoursTtl", 90);

        assertEquals(Duration.ofSeconds(90), service.resolveTtl());
    }

    @Test
    void resolveTtl_offHoursUsesConfiguredTtl() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, com.portfolio.model.analytics.Heatmap> template = mock(RedisTemplate.class);
        CashSessionClock clock = mock(CashSessionClock.class);
        when(clock.isCashOpen()).thenReturn(false);
        @SuppressWarnings("unchecked")
        ObjectProvider<CashSessionClock> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(clock);

        PortfolioHeatmapRedisService service = new PortfolioHeatmapRedisService(template, provider);
        ReflectionTestUtils.setField(service, "portfolioHeatmapTtl", 900);
        ReflectionTestUtils.setField(service, "portfolioHeatmapCashHoursTtl", 90);

        assertEquals(Duration.ofSeconds(900), service.resolveTtl());
    }
}
