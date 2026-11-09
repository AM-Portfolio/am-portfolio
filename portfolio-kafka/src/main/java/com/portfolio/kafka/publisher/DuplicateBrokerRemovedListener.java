package com.portfolio.kafka.publisher;

import com.am.common.amcommondata.service.DuplicateBrokerRemovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fan-out Kafka DELETE when upsert cleans a duplicate BROKER Mongo row (GROW/GROWW twins).
 * Uses Spring events so common-data does not depend on kafka.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DuplicateBrokerRemovedListener {

    private final PortfolioEventPublisher portfolioEventPublisher;

    @EventListener
    public void onDuplicateBrokerRemoved(DuplicateBrokerRemovedEvent event) {
        if (event == null || event.portfolioId() == null || event.portfolioId().isBlank()) {
            return;
        }
        log.info("Fan-out DELETE for cleaned duplicate broker owner={} id={} name={}",
                event.ownerId(), event.portfolioId(), event.name());
        portfolioEventPublisher.publishPortfolioDelete(
                event.ownerId(), event.portfolioId(), event.name(), "BROKER_DUPLICATE_HEAL");
    }
}
