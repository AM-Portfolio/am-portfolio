package com.portfolio.basket.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolio.basket.model.BasketCatalogResponse;
import com.portfolio.model.basket.cache.CachedBasketCatalog;
import com.portfolio.redis.service.BasketCatalogRedisService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Basket catalog: L1 Caffeine → L2 Redis (fail-open) → Mongo → classpath seed.
 * Redis/Mongo failures never break the API.
 */
@Service
@Slf4j
public class BasketCatalogService {

    private static final String L1_KEY = "catalog";

    private final BasketCatalogRedisService catalogRedisService;
    private final BasketCatalogMongoService catalogMongoService;

    @Value("${basket.cache.catalog-ttl-seconds:3600}")
    private long catalogL1TtlSeconds;

    private Cache<String, CachedBasketCatalog> l1Cache;

    public BasketCatalogService(
            @Nullable BasketCatalogRedisService catalogRedisService,
            BasketCatalogMongoService catalogMongoService) {
        this.catalogRedisService = catalogRedisService;
        this.catalogMongoService = catalogMongoService;
    }

    @PostConstruct
    void initL1() {
        l1Cache = Caffeine.newBuilder()
                .expireAfterWrite(Math.max(60, catalogL1TtlSeconds), TimeUnit.SECONDS)
                .maximumSize(4)
                .build();
    }

    public BasketCatalogResponse getCatalog() {
        CachedBasketCatalog cached = resolveCached();
        if (cached == null) {
            log.warn("Basket catalog empty (L1/L2/Mongo/seed miss)");
            return BasketCatalogResponse.builder()
                    .defaultQuery("")
                    .themes(new ArrayList<>())
                    .build();
        }
        return toResponse(cached);
    }

    /**
     * Replace catalog in Mongo + Redis + L1 (ops / admin). Fail-open on Redis.
     */
    public BasketCatalogResponse upsertCatalog(CachedBasketCatalog catalog) {
        if (catalog == null || catalog.getThemes() == null || catalog.getThemes().isEmpty()) {
            throw new IllegalArgumentException("catalog.themes must not be empty");
        }
        catalogMongoService.upsert(catalog);
        storeInCaches(catalog);
        return toResponse(catalog);
    }

    public String resolveDefaultQuery() {
        CachedBasketCatalog cached = resolveCached();
        if (cached == null) {
            return "";
        }
        return computeDefaultQuery(cached);
    }

    /** Index/name alias → preferred ETF symbol from catalog (case-insensitive). */
    public Map<String, String> preferredSymbolByAlias() {
        Map<String, String> map = new LinkedHashMap<>();
        CachedBasketCatalog cached = resolveCached();
        if (cached == null || cached.getThemes() == null) {
            return map;
        }
        for (CachedBasketCatalog.Theme theme : cached.getThemes()) {
            if (theme.getQuery() == null || theme.getQuery().isBlank()) {
                continue;
            }
            String symbol = theme.getQuery().trim();
            if (theme.getLabel() != null && !theme.getLabel().isBlank()) {
                map.put(theme.getLabel().trim().toLowerCase(Locale.ROOT), symbol);
            }
            if (theme.getIndexAliases() != null) {
                for (String alias : theme.getIndexAliases()) {
                    if (alias != null && !alias.isBlank()) {
                        map.put(alias.trim().toLowerCase(Locale.ROOT), symbol);
                    }
                }
            }
        }
        return map;
    }

    private CachedBasketCatalog resolveCached() {
        CachedBasketCatalog l1 = l1Cache.getIfPresent(L1_KEY);
        if (l1 != null) {
            log.info("catalog.cache=L1");
            return l1;
        }

        if (catalogRedisService != null) {
            try {
                var l2 = catalogRedisService.getCatalog();
                if (l2.isPresent()) {
                    CachedBasketCatalog fromL2 = l2.get();
                    l1Cache.put(L1_KEY, fromL2);
                    log.info("catalog.cache=L2");
                    return fromL2;
                }
            } catch (Exception e) {
                log.warn("catalog.cache=L2_FAIL — fail-open: {}", e.getMessage());
            }
        }

        var mongo = catalogMongoService.getCatalog();
        if (mongo.isPresent()) {
            storeInCaches(mongo.get());
            log.info("catalog.cache=MONGO");
            return mongo.get();
        }

        var seed = catalogMongoService.loadClasspathSeed();
        if (seed.isPresent()) {
            CachedBasketCatalog seeded = seed.get();
            catalogMongoService.upsert(seeded);
            storeInCaches(seeded);
            log.info("catalog.cache=SEED themes={}", seeded.getThemes().size());
            return seeded;
        }

        return null;
    }

    private void storeInCaches(CachedBasketCatalog catalog) {
        l1Cache.put(L1_KEY, catalog);
        if (catalogRedisService != null) {
            try {
                catalogRedisService.cacheCatalogAsync(catalog);
            } catch (Exception e) {
                log.warn("catalog.cache=L2_WRITE_FAIL — fail-open: {}", e.getMessage());
            }
        }
    }

    private BasketCatalogResponse toResponse(CachedBasketCatalog cached) {
        List<BasketCatalogResponse.Theme> themes = cached.getThemes().stream()
                .filter(t -> t.getId() != null && t.getQuery() != null && !t.getQuery().isBlank())
                .map(t -> BasketCatalogResponse.Theme.builder()
                        .id(t.getId())
                        .label(t.getLabel() != null ? t.getLabel() : t.getId())
                        .query(t.getQuery().trim())
                        .featured(t.isFeatured())
                        .build())
                .collect(Collectors.toList());

        return BasketCatalogResponse.builder()
                .defaultQuery(computeDefaultQuery(cached))
                .themes(themes)
                .build();
    }

    private String computeDefaultQuery(CachedBasketCatalog cached) {
        List<String> defaultIds = cached.getDefaultThemeIds();
        Map<String, CachedBasketCatalog.Theme> byId = cached.getThemes().stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(
                        CachedBasketCatalog.Theme::getId,
                        t -> t,
                        (a, b) -> a,
                        LinkedHashMap::new));

        if (defaultIds != null && !defaultIds.isEmpty()) {
            String joined = defaultIds.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .map(CachedBasketCatalog.Theme::getQuery)
                    .filter(q -> q != null && !q.isBlank())
                    .map(String::trim)
                    .collect(Collectors.joining(","));
            if (!joined.isEmpty()) {
                return joined;
            }
        }

        return cached.getThemes().stream()
                .filter(CachedBasketCatalog.Theme::isFeatured)
                .map(CachedBasketCatalog.Theme::getQuery)
                .filter(q -> q != null && !q.isBlank())
                .map(String::trim)
                .limit(3)
                .collect(Collectors.joining(","));
    }
}
