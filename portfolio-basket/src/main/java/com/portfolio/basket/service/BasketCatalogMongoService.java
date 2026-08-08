package com.portfolio.basket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.portfolio.model.basket.cache.CachedBasketCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

/**
 * Mongo source of truth for basket catalog. Fail-open on Mongo errors.
 * Seeds from classpath {@code basket-catalog.yml} when the collection is empty.
 */
@Service
@Slf4j
public class BasketCatalogMongoService {

    public static final String COLLECTION = "basket_catalog";
    public static final String DOC_ID = "default";
    private static final String SEED_RESOURCE = "basket-catalog.yml";

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper yamlMapper;

    public BasketCatalogMongoService(@Nullable MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public Optional<CachedBasketCatalog> getCatalog() {
        if (mongoTemplate == null) {
            return Optional.empty();
        }
        try {
            CatalogDoc doc = mongoTemplate.findOne(
                    Query.query(Criteria.where("_id").is(DOC_ID)),
                    CatalogDoc.class,
                    COLLECTION);
            if (doc != null && doc.getCatalog() != null
                    && doc.getCatalog().getThemes() != null
                    && !doc.getCatalog().getThemes().isEmpty()) {
                return Optional.of(doc.getCatalog());
            }
        } catch (Exception e) {
            log.warn("Basket catalog Mongo get failed — fail-open: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public void upsert(CachedBasketCatalog catalog) {
        if (mongoTemplate == null || catalog == null) {
            return;
        }
        try {
            Query query = Query.query(Criteria.where("_id").is(DOC_ID));
            Update update = new Update()
                    .set("catalog", catalog)
                    .set("updatedAt", Instant.now().toString());
            mongoTemplate.upsert(query, update, COLLECTION);
            log.info("Basket catalog Mongo upsert themes={}",
                    catalog.getThemes() != null ? catalog.getThemes().size() : 0);
        } catch (Exception e) {
            log.warn("Basket catalog Mongo upsert failed — fail-open: {}", e.getMessage());
        }
    }

    /**
     * Load seed YAML from classpath. Does not throw.
     */
    public Optional<CachedBasketCatalog> loadClasspathSeed() {
        try {
            ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
            if (!resource.exists()) {
                log.warn("{} missing on classpath", SEED_RESOURCE);
                return Optional.empty();
            }
            try (InputStream in = resource.getInputStream()) {
                CachedBasketCatalog catalog = yamlMapper.readValue(in, CachedBasketCatalog.class);
                if (catalog == null || catalog.getThemes() == null || catalog.getThemes().isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(catalog);
            }
        } catch (Exception e) {
            log.warn("Failed to load classpath {}: {}", SEED_RESOURCE, e.getMessage());
            return Optional.empty();
        }
    }

    @lombok.Data
    public static class CatalogDoc {
        private String id;
        private CachedBasketCatalog catalog;
        private String updatedAt;
    }
}
