package com.am.common.amcommondata.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BasketCreateIdempotencyIndexConfig {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBasketCreateIdempotencyIndexes() {
        try {
            IndexOperations ops = mongoTemplate.indexOps("basket_create_idempotency");
            ops.ensureIndex(new Index()
                    .on("createdAt", Sort.Direction.ASC)
                    .expire(24, TimeUnit.HOURS)
                    .named("createdAt_ttl_24h"));
            log.info("[MongoIndex] basket_create_idempotency TTL index ensured.");
        } catch (Exception e) {
            log.error("[MongoIndex] Failed to ensure basket_create_idempotency indexes.", e);
        }
    }
}
