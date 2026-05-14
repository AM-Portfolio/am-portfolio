package com.portfolio.analytics.service;

import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.providers.index.IndexAnalyticsProvider;
import com.portfolio.analytics.service.providers.portfolio.PortfolioAnalyticsProvider;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsFactoryTest {

    @SuppressWarnings("unchecked")
    private IndexAnalyticsProvider<Object> mockIndexProvider(AnalyticsType type) {
        var p = mock(IndexAnalyticsProvider.class);
        when(p.getType()).thenReturn(type);
        return p;
    }

    @SuppressWarnings("unchecked")
    private PortfolioAnalyticsProvider<Object> mockPortfolioProvider(AnalyticsType type) {
        var p = mock(PortfolioAnalyticsProvider.class);
        when(p.getType()).thenReturn(type);
        return p;
    }

    @Test void getIndexProvider_found() {
        var provider = mockIndexProvider(AnalyticsType.SECTOR_HEATMAP);
        var factory = new AnalyticsFactory(List.of(provider), List.of());
        assertNotNull(factory.getIndexProvider(AnalyticsType.SECTOR_HEATMAP));
    }

    @Test void getIndexProvider_notFound_throws() {
        var factory = new AnalyticsFactory(List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> factory.getIndexProvider(AnalyticsType.SECTOR_HEATMAP));
    }

    @Test void getPortfolioProvider_found() {
        var provider = mockPortfolioProvider(AnalyticsType.TOP_MOVERS);
        var factory = new AnalyticsFactory(List.of(), List.of(provider));
        assertNotNull(factory.getPortfolioProvider(AnalyticsType.TOP_MOVERS));
    }

    @Test void getPortfolioProvider_notFound_throws() {
        var factory = new AnalyticsFactory(List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> factory.getPortfolioProvider(AnalyticsType.TOP_MOVERS));
    }
}
