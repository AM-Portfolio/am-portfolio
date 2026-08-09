package com.portfolio.basket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.basket.model.BasketCatalogResponse;
import com.portfolio.model.basket.cache.CachedBasketCatalog;
import com.portfolio.redis.service.BasketCatalogRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BasketCatalogServiceTest {

    @Mock
    private BasketCatalogRedisService catalogRedisService;

    @Mock
    private BasketCatalogMongoService catalogMongoService;

    private BasketCatalogService service;

    @BeforeEach
    void setUp() {
        service = new BasketCatalogService(catalogRedisService, catalogMongoService);
        ReflectionTestUtils.setField(service, "catalogL1TtlSeconds", 3600L);
        service.initL1();
    }

    @Test
    void l1Hit_skipsRedisAndMongo() {
        CachedBasketCatalog catalog = sampleCatalog();
        when(catalogRedisService.getCatalog()).thenReturn(Optional.of(catalog));

        BasketCatalogResponse first = service.getCatalog();
        BasketCatalogResponse second = service.getCatalog();

        assertEquals("NIFTYBEES,BANKBEES", first.getDefaultQuery());
        assertEquals(2, first.getThemes().size());
        assertEquals(first.getDefaultQuery(), second.getDefaultQuery());
        verify(catalogRedisService, times(1)).getCatalog();
        verify(catalogMongoService, never()).getCatalog();
    }

    @Test
    void redisThrows_failOpenToMongo() {
        when(catalogRedisService.getCatalog()).thenThrow(new RuntimeException("redis down"));
        when(catalogMongoService.getCatalog()).thenReturn(Optional.of(sampleCatalog()));

        BasketCatalogResponse response = service.getCatalog();

        assertEquals(2, response.getThemes().size());
        assertEquals("nifty-50", response.getThemes().get(0).getId());
        verify(catalogRedisService).cacheCatalogAsync(any());
    }

    @Test
    void redisNull_usesSeedWhenMongoEmpty() {
        BasketCatalogService noRedis = new BasketCatalogService(null, catalogMongoService);
        ReflectionTestUtils.setField(noRedis, "catalogL1TtlSeconds", 3600L);
        noRedis.initL1();

        when(catalogMongoService.getCatalog()).thenReturn(Optional.empty());
        when(catalogMongoService.loadClasspathSeed()).thenReturn(Optional.of(sampleCatalog()));

        BasketCatalogResponse response = noRedis.getCatalog();

        assertFalse(response.getThemes().isEmpty());
        verify(catalogMongoService).upsert(any());
    }

    @Test
    void allMiss_returnsEmptyThemes() {
        when(catalogRedisService.getCatalog()).thenReturn(Optional.empty());
        when(catalogMongoService.getCatalog()).thenReturn(Optional.empty());
        when(catalogMongoService.loadClasspathSeed()).thenReturn(Optional.empty());

        BasketCatalogResponse response = service.getCatalog();

        assertTrue(response.getThemes().isEmpty());
        assertEquals("", response.getDefaultQuery());
    }

    @Test
    void preferredSymbolByAlias_fromCatalog() {
        when(catalogRedisService.getCatalog()).thenReturn(Optional.of(sampleCatalog()));

        assertEquals("NIFTYBEES", service.preferredSymbolByAlias().get("nifty 50"));
        assertEquals("BANKBEES", service.preferredSymbolByAlias().get("nifty bank"));
    }

    private static CachedBasketCatalog sampleCatalog() {
        CachedBasketCatalog.Theme t1 = new CachedBasketCatalog.Theme();
        t1.setId("nifty-50");
        t1.setLabel("Nifty 50");
        t1.setQuery("NIFTYBEES");
        t1.setFeatured(true);
        t1.setIndexAliases(List.of("nifty 50", "NIFTY 50"));

        CachedBasketCatalog.Theme t2 = new CachedBasketCatalog.Theme();
        t2.setId("bank");
        t2.setLabel("Bank");
        t2.setQuery("BANKBEES");
        t2.setFeatured(true);
        t2.setIndexAliases(List.of("nifty bank"));

        CachedBasketCatalog catalog = new CachedBasketCatalog();
        catalog.setDefaultThemeIds(List.of("nifty-50", "bank"));
        catalog.setThemes(List.of(t1, t2));
        return catalog;
    }
}
