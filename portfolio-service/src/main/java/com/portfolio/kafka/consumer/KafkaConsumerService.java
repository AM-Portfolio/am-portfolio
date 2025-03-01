package com.portfolio.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import com.portfolio.kafka.model.PortfolioUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class KafkaConsumerService {

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topic}", 
                  groupId = "${spring.kafka.consumer.group-id}",
                  containerFactory = "kafkaListenerContainerFactory")
    public void consume(Object message, Acknowledgment acknowledgment) {
        try {
            log.info("Received message: {}", message);
            
            // Convert the received Object to PortfolioUpdateEvent
            PortfolioUpdateEvent event;
            if (message instanceof PortfolioUpdateEvent) {
                event = (PortfolioUpdateEvent) message;
            } else {
                // Convert from JSON or Map to PortfolioUpdateEvent
                event = objectMapper.convertValue(message, PortfolioUpdateEvent.class);
            }
            
            // Process the event
            processMessage(event);
            
            // If processing was successful, acknowledge the message
            acknowledgment.acknowledge();
            log.info("Message processed and acknowledged successfully");
        } catch (Exception e) {
            // If processing failed, log the error and don't acknowledge
            // This will cause the message to be redelivered
            log.error("Failed to process message: {}", message, e);
            // Don't call acknowledgment.acknowledge() here
        }
    }

    private void processMessage(PortfolioUpdateEvent event) {
        // Implement your message processing logic here
        // If processing fails, throw an exception
    }
}
