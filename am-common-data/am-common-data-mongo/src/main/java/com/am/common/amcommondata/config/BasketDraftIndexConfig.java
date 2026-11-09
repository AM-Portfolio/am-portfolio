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

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BasketDraftIndexConfig {

    private final MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBasketDraftIndexes() {
        try {
            IndexOperations ops = mongoTemplate.indexOps("basket_drafts");
            ops.ensureIndex(new Index()
                    .on("userId", Sort.Direction.ASC)
                    .on("sourcePortfolioId", Sort.Direction.ASC)
                    .on("etfIsin", Sort.Direction.ASC)
                    .unique()
                    .named("user_portfolio_etf_unique"));
            ops.ensureIndex(new Index()
                    .on("userId", Sort.Direction.ASC)
                    .on("updatedAt", Sort.Direction.DESC)
                    .named("user_updatedAt_desc"));
            log.info("[MongoIndex] basket_drafts indexes ensured.");
        } catch (Exception e) {
            log.error("[MongoIndex] Failed to ensure basket_drafts indexes.", e);
        }
    }
}
