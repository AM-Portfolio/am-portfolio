package com.portfolio.app.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.kafka.config.KafkaTopics;
import com.portfolio.kafka.service.PortfolioCalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Kafka integration test using Testcontainers.
 * Verifies that the PortfolioCalculationConsumer correctly triggers the calculation service.
 */
class KafkaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void testPortfolioCalculationTrigger() throws Exception {
        // ---- WAIT for the relevant Kafka listener container to be assigned partitions ----
        // This eliminates the race condition where we send before the consumer subscribes.
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            String[] topics = container.getContainerProperties().getTopics();
            if (topics != null && java.util.Arrays.asList(topics).contains(KafkaTopics.TRIGGER_CALCULATION)) {
                ContainerTestUtils.waitForAssignment(container, 1);
                break;
            }
        }

        // Assert consumer is loaded
        org.junit.jupiter.api.Assertions.assertNotNull(applicationContext.getBean(com.portfolio.kafka.consumer.PortfolioCalculationConsumer.class), "Consumer must be loaded");

        // Prepare test data
        String userId = "test-user-kafka";
        String portfolioId = "test-portfolio-kafka";

        // Build the payload as a Map or ObjectNode — KafkaTemplate uses JsonSerializer
        // so if we pass a String, it gets double-serialized into "{\"userId\":\"...\"}"
        java.util.Map<String, String> payload = new java.util.HashMap<>();
        payload.put("userId", userId);
        payload.put("portfolioId", portfolioId);

        // Send message to the trigger topic
        kafkaTemplate.send(KafkaTopics.TRIGGER_CALCULATION, payload).get();

        // Verify that the consumer picked up the message and delegated to the service
        // We use timeout() because Kafka consumption is asynchronous
        verify(portfolioCalculationService, timeout(15000)).processCalculation(
                eq(userId),
                eq(portfolioId),
                nullable(String.class) // correlationId might be null or generated
        );
    }
}
