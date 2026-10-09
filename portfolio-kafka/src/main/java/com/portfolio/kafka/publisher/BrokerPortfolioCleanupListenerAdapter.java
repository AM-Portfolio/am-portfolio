package com.portfolio.kafka.publisher;

import com.am.common.amcommondata.service.BrokerPortfolioCleanupListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fan-out DELETE on am-portfolio-update when list/upsert heal removes a duplicate BROKER doc
 * (e.g. legacy GROW beside GROWW).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BrokerPortfolioCleanupListenerAdapter implements BrokerPortfolioCleanupListener {

    private final PortfolioEventPublisher portfolioEventPublisher;

    @Override
    public void onDuplicateBrokerRemoved(String ownerId, String portfolioId, String name) {
        log.info("Publishing DELETE for healed duplicate broker portfolio owner={} id={} name={}",
                ownerId, portfolioId, name);
        portfolioEventPublisher.publishPortfolioDelete(
                ownerId, portfolioId, name, "BROKER_DUPLICATE_HEAL");
    }
}
