package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import com.portfolio.kafka.model.PortfolioUpdateEvent;
import com.portfolio.service.AMPortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;
    private final AMPortfolioService portfolioService;

    @KafkaListener(topics = "${app.kafka.topic}", 
                  groupId = "${spring.kafka.consumer.group-id}",
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
        portfolioService.processMessage(event);
    }
}
