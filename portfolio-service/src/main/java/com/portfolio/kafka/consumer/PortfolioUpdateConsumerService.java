package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.portfolio.kafka.model.PortfolioUpdateEvent;
import com.portfolio.mapper.PortfolioMapperv1;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.portfolio.consumer.enabled", havingValue = "true", matchIfMissing = false)
public class PortfolioUpdateConsumerService {

    private final ObjectMapper objectMapper;
    private final PortfolioMapperv1 portfolioMapper;
    private final PortfolioService portfolioService;

    @KafkaListener(topics = "${app.kafka.portfolio.topic}", 
                  groupId = "${app.kafka.portfolio.consumer.id}",
                  containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("Received message: {}", message);
            
            // Convert JSON string to PortfolioUpdateEvent
            PortfolioUpdateEvent event = objectMapper.readValue(message, PortfolioUpdateEvent.class);
            log.info("Converted to event: {}", event);
              
            // Process the event
            processMessage(event);
            
            // If processing was successful, acknowledge the message
            acknowledgment.acknowledge();
            log.info("Message processed and acknowledged successfully");
        } catch (Exception e) {
            log.error("Failed to process message: {}. Error: {}", message, e.getMessage(), e);
        }
    }

    private void processMessage(PortfolioUpdateEvent event) {
        PortfolioModelV1 portfolioModel = portfolioMapper.toPortfolioModelV1(event);
        portfolioService.createPortfolio(portfolioModel);
    }   
}
