package com.portfolio.kafka.publisher;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.portfolio.kafka.producer.KafkaProducerService;
import com.portfolio.model.events.PortfolioUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class PortfolioEventPublisher {

    @Autowired(required = false)
    private KafkaProducerService kafkaProducerService;

    public void publishPortfolioUpdate(PortfolioModelV1 savedPortfolio, String source) {
        if (kafkaProducerService == null) {
            log.debug("Kafka disabled - skipping portfolio update publish for user: {}",
                      savedPortfolio != null ? savedPortfolio.getOwner() : "null");
            return;
        }
        if (savedPortfolio == null || savedPortfolio.getOwner() == null) {
            log.warn("Skipping publish - savedPortfolio is null or has no owner");
            return;
        }

        // portfolioId in the outbound event = the am-portfolio MongoDB document _id (UUID).
        // am-trade-management uses this as the key to upsert the portfolio record in its DB.
        // name = human-readable portfolio name shown in the UI.
        PortfolioUpdateEvent outboundEvent = PortfolioUpdateEvent.builder()
                .id(savedPortfolio.getId() != null ? savedPortfolio.getId() : UUID.randomUUID())
                .userId(savedPortfolio.getOwner())
                .portfolioId(savedPortfolio.getId() != null ? savedPortfolio.getId().toString() : UUID.randomUUID().toString())
                .name(savedPortfolio.getName())
                .brokerType(savedPortfolio.getBrokerType())
                .source(source != null ? source : "PORTFOLIO_RESOLVED")
                .equities(savedPortfolio.getEquityModels())
                .totalValue(savedPortfolio.getTotalValue())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaProducerService.sendMessage(outboundEvent, null);
        log.info("Published resolved portfolio to outbound topic for user: {}, broker: {}, source: {}",
                 savedPortfolio.getOwner(), savedPortfolio.getBrokerType(), source);
    }
}
