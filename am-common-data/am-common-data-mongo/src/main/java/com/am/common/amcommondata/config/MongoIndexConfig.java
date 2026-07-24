package com.am.common.amcommondata.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MongoIndexConfig {
    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureStockPriceHistoryIndexes() {
        try {
            IndexOperations ops = mongoTemplate.indexOps("stock_price_history");

            // 1. Unique compound index — prevents duplicate ticks per minute
            ops.ensureIndex(new Index()
                .on("symbol", Sort.Direction.ASC)
                .on("timestampMinute", Sort.Direction.ASC)
                .unique());

            // 2. TTL index — auto-deletes after 48h
            ops.ensureIndex(new Index()
                .on("createdAt", Sort.Direction.ASC)
                .expire(48, TimeUnit.HOURS));

            log.info("[MongoIndex] Indexes ensured on stock_price_history collection.");
        } catch (Exception e) {
            log.error("[MongoIndex] Failed to ensure indexes. Check MongoDB connectivity.", e);
        }
    }
}
