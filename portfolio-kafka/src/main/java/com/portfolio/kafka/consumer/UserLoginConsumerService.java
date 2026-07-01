package com.portfolio.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.model.events.UserLoginEvent;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.service.scheduler.SnapshotCatchUpService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.login.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class UserLoginConsumerService {

    private final ObjectMapper objectMapper;
    private final PortfolioService portfolioService;
    private final SnapshotCatchUpService snapshotCatchUpService;

    @KafkaListener(topics = "${app.kafka.login.topic:active-user}", groupId = "${app.kafka.login.consumer.id:am-portfolio-login-group}")
    public void consumeUserLoginEvent(String message, Acknowledgment ack) {
        log.info("Received user login event from active-user topic");
        try {
            UserLoginEvent event = objectMapper.readValue(message, UserLoginEvent.class);
            if (event != null && event.getUserId() != null) {
                // Update lastLoginDate
                portfolioService.updateLastLoginDate(event.getUserId(), LocalDate.now());
                
                // Trigger async catchup
                snapshotCatchUpService.triggerCatchUp(event.getUserId());
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process user login event", e);
            ack.acknowledge(); // Acknowledge to skip bad messages
        }
    }
}
