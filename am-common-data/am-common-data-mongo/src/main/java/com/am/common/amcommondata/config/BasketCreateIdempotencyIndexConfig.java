package com.am.common.amcommondata.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${basket.create.idempotency-ttl-seconds:86400}")
    private long idempotencyTtlSeconds;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBasketCreateIdempotencyIndexes() {
        try {
            long ttlSeconds = Math.max(3600, idempotencyTtlSeconds);
            IndexOperations ops = mongoTemplate.indexOps("basket_create_idempotency");
            ops.ensureIndex(new Index()
                    .on("createdAt", Sort.Direction.ASC)
                    .expire(ttlSeconds, TimeUnit.SECONDS)
                    .named("createdAt_ttl"));
            log.info("[MongoIndex] basket_create_idempotency TTL index ensured ({}s).", ttlSeconds);
        } catch (Exception e) {
            log.error("[MongoIndex] Failed to ensure basket_create_idempotency indexes.", e);
        }
    }
}
