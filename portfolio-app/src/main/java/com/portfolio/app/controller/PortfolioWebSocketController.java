package com.portfolio.app.controller;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class PortfolioWebSocketController {

    @SuppressWarnings("unused")
    private final SimpMessagingTemplate messagingTemplate;

    private final com.portfolio.kafka.service.PortfolioCalculationService portfolioCalculationService;

    public PortfolioWebSocketController(SimpMessagingTemplate messagingTemplate,
            com.portfolio.kafka.service.PortfolioCalculationService portfolioCalculationService) {
        this.messagingTemplate = messagingTemplate;
        this.portfolioCalculationService = portfolioCalculationService;
    }

    /**
     * Endpoint for clients to subscribe to their portfolio updates.
     * This is largely handled by the broker, but we can hook in here if needed.
     */
    @SubscribeMapping("/topic/portfolio")
    public void subscribeToPortfolio() {
        log.info("Client subscribed to portfolio updates");
    }

    /**
     * Endpoint to trigger portfolio calculation manually.
     * Payload: {"userId": "..."}
     */
    @org.springframework.messaging.handler.annotation.MessageMapping("/portfolio/calculate")
    public void triggerPortfolioCalculation(
            @org.springframework.messaging.handler.annotation.Payload java.util.Map<String, String> payload,
            java.security.Principal principal) {
        String userId = payload.get("userId");
        if (userId == null && principal != null) {
            userId = principal.getName();
        }

        log.info("Received portfolio calculation request for user: {}", userId);
        if (userId != null) {
            // Use a random UUID for tracing if not provided
            String traceId = java.util.UUID.randomUUID().toString();
            portfolioCalculationService.processCalculation(userId, traceId);
        } else {
            log.warn("Portfolio calculation request missing userId");
        }
    }
}
